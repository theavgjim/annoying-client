package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

// Microsoft account sign-in for direct launches.
//
// Flow: Microsoft device code (the user enters a code at microsoft.com/link) -> Xbox Live user token ->
// XSTS token for Minecraft -> Minecraft access token -> ownership check -> Minecraft profile.
// Minecraft only accepts tokens from Azure applications Mojang has approved, so the application
// (client) ID comes from Settings.

const (
	msDeviceCodeURL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
	msTokenURL      = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
	xblAuthURL      = "https://user.auth.xboxlive.com/user/authenticate"
	xstsAuthURL     = "https://xsts.auth.xboxlive.com/xsts/authorize"
	mcLoginURL      = "https://api.minecraftservices.com/authentication/login_with_xbox"
	mcEntitlements  = "https://api.minecraftservices.com/entitlements/mcstore"
	mcProfileURL    = "https://api.minecraftservices.com/minecraft/profile"
	msScope         = "XboxLive.signin offline_access"
)

// Account is a signed-in Minecraft account. Stored encrypted (Windows DPAPI) in <data>/account.dat.
type Account struct {
	Name           string    `json:"name"`
	UUID           string    `json:"uuid"`
	AccessToken    string    `json:"accessToken"`
	AccessExpires  time.Time `json:"accessExpires"`
	MSRefreshToken string    `json:"msRefreshToken"`
	ClientID       string    `json:"clientId"`
}

// DeviceLogin is shown to the user while signing in.
type DeviceLogin struct {
	UserCode        string `json:"userCode"`
	VerificationURI string `json:"verificationUri"`
	Message         string `json:"message"`
	ExpiresIn       int    `json:"expiresIn"`
}

type AuthManager struct {
	mu      sync.Mutex
	path    string
	account *Account
	cancel  context.CancelFunc
}

func newAuthManager(path string) *AuthManager {
	a := &AuthManager{path: path}
	if data, err := os.ReadFile(path); err == nil {
		if plain, err := unprotect(data); err == nil {
			var acc Account
			if json.Unmarshal(plain, &acc) == nil && acc.Name != "" {
				a.account = &acc
			}
		} else {
			logf("stored account could not be decrypted: %v", err)
		}
	}
	return a
}

func (a *AuthManager) Current() *Account {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.account == nil {
		return nil
	}
	c := *a.account
	return &c
}

func (a *AuthManager) save(acc *Account) error {
	data, _ := json.Marshal(acc)
	enc, err := protect(data)
	if err != nil {
		return err
	}
	if err := writeFileAtomic(a.path, enc); err != nil {
		return err
	}
	_ = os.Chmod(a.path, 0o600)
	a.account = acc
	return nil
}

func (a *AuthManager) Logout() {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cancel != nil {
		a.cancel()
	}
	a.account = nil
	_ = os.Remove(a.path)
}

func formPost(ctx context.Context, endpoint string, form url.Values, out any) error {
	data, err := doRequest(ctx, http.MethodPost, endpoint, strings.NewReader(form.Encode()),
		map[string]string{"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"})
	if err != nil {
		var he *HTTPError
		if errors.As(err, &he) && out != nil {
			_ = json.Unmarshal([]byte(he.Body), out)
		}
		return err
	}
	return json.Unmarshal(data, out)
}

type msTokenResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	Error        string `json:"error"`
	ErrorDesc    string `json:"error_description"`
	Interval     int    `json:"interval"`
}

// StartLogin requests a device code and finishes the sign-in in the background.
// done receives the account or an error once the user has entered the code (or it expired).
func (a *AuthManager) StartLogin(clientID string, done func(*Account, error)) (*DeviceLogin, error) {
	clientID = strings.TrimSpace(clientID)
	if clientID == "" {
		return nil, errors.New("direct launch needs a Microsoft application (client) ID approved for Minecraft. Add it in Settings, or play through the Minecraft Launcher")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 16*time.Minute)
	var dc struct {
		DeviceCode      string `json:"device_code"`
		UserCode        string `json:"user_code"`
		VerificationURI string `json:"verification_uri"`
		ExpiresIn       int    `json:"expires_in"`
		Interval        int    `json:"interval"`
		Message         string `json:"message"`
		Error           string `json:"error"`
		ErrorDesc       string `json:"error_description"`
	}
	if err := formPost(ctx, msDeviceCodeURL, url.Values{"client_id": {clientID}, "scope": {msScope}}, &dc); err != nil {
		cancel()
		if dc.ErrorDesc != "" {
			return nil, fmt.Errorf("microsoft rejected the sign-in request: %s", firstLine(dc.ErrorDesc))
		}
		return nil, err
	}
	a.mu.Lock()
	if a.cancel != nil {
		a.cancel()
	}
	a.cancel = cancel
	a.mu.Unlock()

	go func() {
		defer cancel()
		interval := time.Duration(max(dc.Interval, 5)) * time.Second
		for {
			select {
			case <-ctx.Done():
				done(nil, errors.New("sign-in timed out or was cancelled"))
				return
			case <-time.After(interval):
			}
			var tok msTokenResponse
			err := formPost(ctx, msTokenURL, url.Values{
				"grant_type":  {"urn:ietf:params:oauth:grant-type:device_code"},
				"client_id":   {clientID},
				"device_code": {dc.DeviceCode},
			}, &tok)
			switch tok.Error {
			case "authorization_pending":
				continue
			case "slow_down":
				interval += 5 * time.Second
				continue
			case "":
			default:
				done(nil, fmt.Errorf("sign-in failed: %s", firstLine(tok.ErrorDesc)))
				return
			}
			if err != nil {
				done(nil, err)
				return
			}
			acc, err := minecraftLogin(ctx, tok.AccessToken)
			if err != nil {
				done(nil, err)
				return
			}
			acc.MSRefreshToken = tok.RefreshToken
			acc.ClientID = clientID
			a.mu.Lock()
			err = a.save(acc)
			a.mu.Unlock()
			done(acc, err)
			return
		}
	}()
	return &DeviceLogin{UserCode: dc.UserCode, VerificationURI: dc.VerificationURI, Message: dc.Message, ExpiresIn: dc.ExpiresIn}, nil
}

// Valid returns an account with a usable Minecraft token, refreshing it when it is about to expire.
func (a *AuthManager) Valid(ctx context.Context) (*Account, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.account == nil {
		return nil, errors.New("sign in with your Microsoft account first")
	}
	if time.Until(a.account.AccessExpires) > 10*time.Minute {
		c := *a.account
		return &c, nil
	}
	var tok msTokenResponse
	err := formPost(ctx, msTokenURL, url.Values{
		"grant_type":    {"refresh_token"},
		"client_id":     {a.account.ClientID},
		"refresh_token": {a.account.MSRefreshToken},
		"scope":         {msScope},
	}, &tok)
	if err != nil || tok.AccessToken == "" {
		return nil, fmt.Errorf("your sign-in expired; sign in again (%v)", firstLine(tok.ErrorDesc+" "+errString(err)))
	}
	acc, err := minecraftLogin(ctx, tok.AccessToken)
	if err != nil {
		return nil, err
	}
	acc.MSRefreshToken = tok.RefreshToken
	acc.ClientID = a.account.ClientID
	if err := a.save(acc); err != nil {
		return nil, err
	}
	c := *acc
	return &c, nil
}

func minecraftLogin(ctx context.Context, msAccessToken string) (*Account, error) {
	var xbl struct {
		Token         string `json:"Token"`
		DisplayClaims struct {
			Xui []struct {
				Uhs string `json:"uhs"`
			} `json:"xui"`
		} `json:"DisplayClaims"`
	}
	err := postJSON(ctx, xblAuthURL, map[string]any{
		"Properties":   map[string]any{"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": "d=" + msAccessToken},
		"RelyingParty": "http://auth.xboxlive.com",
		"TokenType":    "JWT",
	}, &xbl, nil)
	if err != nil || len(xbl.DisplayClaims.Xui) == 0 {
		return nil, fmt.Errorf("xbox Live sign-in failed: %v", errString(err))
	}
	var xsts struct {
		Token string `json:"Token"`
	}
	err = postJSON(ctx, xstsAuthURL, map[string]any{
		"Properties":   map[string]any{"SandboxId": "RETAIL", "UserTokens": []string{xbl.Token}},
		"RelyingParty": "rp://api.minecraftservices.com/",
		"TokenType":    "JWT",
	}, &xsts, nil)
	if err != nil {
		var he *HTTPError
		if errors.As(err, &he) {
			switch {
			case strings.Contains(he.Body, "2148916233"):
				return nil, errors.New("this Microsoft account has no Xbox profile. Sign in once at minecraft.net to create one")
			case strings.Contains(he.Body, "2148916235"):
				return nil, errors.New("xbox Live is not available in your country")
			case strings.Contains(he.Body, "2148916236"), strings.Contains(he.Body, "2148916237"):
				return nil, errors.New("this account needs adult verification on xbox.com")
			case strings.Contains(he.Body, "2148916238"):
				return nil, errors.New("this is a child account; an adult must add it to a Microsoft family")
			}
		}
		return nil, fmt.Errorf("xbox security token failed: %v", err)
	}
	var mc struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	err = postJSON(ctx, mcLoginURL, map[string]any{
		"identityToken": "XBL3.0 x=" + xbl.DisplayClaims.Xui[0].Uhs + ";" + xsts.Token,
	}, &mc, nil)
	if err != nil {
		var he *HTTPError
		if errors.As(err, &he) && he.Status == http.StatusForbidden {
			return nil, errors.New("minecraft refused this application ID. New Azure apps must be approved by Mojang (aka.ms/mce-reviewappid). Until then, play through the Minecraft Launcher")
		}
		return nil, fmt.Errorf("minecraft sign-in failed: %v", err)
	}
	auth := map[string]string{"Authorization": "Bearer " + mc.AccessToken}
	var ent struct {
		Items []struct {
			Name string `json:"name"`
		} `json:"items"`
	}
	if data, err := doRequest(ctx, http.MethodGet, mcEntitlements, nil, auth); err == nil {
		_ = json.Unmarshal(data, &ent)
	}
	var prof struct {
		ID   string `json:"id"`
		Name string `json:"name"`
	}
	data, err := doRequest(ctx, http.MethodGet, mcProfileURL, nil, auth)
	if err != nil {
		var he *HTTPError
		if errors.As(err, &he) && he.Status == http.StatusNotFound {
			if len(ent.Items) == 0 {
				return nil, errors.New("this Microsoft account does not own Minecraft Java Edition")
			}
			return nil, errors.New("create your Minecraft profile (username) at minecraft.net first")
		}
		return nil, err
	}
	if err := json.Unmarshal(data, &prof); err != nil || prof.Name == "" {
		return nil, errors.New("minecraft returned an empty profile")
	}
	return &Account{
		Name: prof.Name, UUID: prof.ID, AccessToken: mc.AccessToken,
		AccessExpires: time.Now().Add(time.Duration(mc.ExpiresIn) * time.Second),
	}, nil
}

func firstLine(s string) string {
	s = strings.TrimSpace(s)
	if i := strings.IndexAny(s, "\r\n"); i >= 0 {
		s = s[:i]
	}
	return s
}

func errString(err error) string {
	if err == nil {
		return ""
	}
	return err.Error()
}
