# Annoying Client cosmetics server

Stores coins, owned and equipped cosmetics (capes, trails) and admin roles for Annoying Client.
Standard library Go, one JSON database file, no other services needed.

## How sign-in works
Exactly like a Minecraft server checks players: the client asks Mojang to "join" a random server id
with the player's own session, then this server asks Mojang (`hasJoined`) who joined. Nobody can sign
in as someone else, so admin rights can't be faked.

## Configuration
| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Listen port |
| `DATA` | `data/cosmetics.json` | Database file (keep it on persistent storage) |
| `ADMINS` | `theavgjim_` | Owners (comma-separated usernames). They become admin on their first sign-in and can't be removed by other admins |
| `GITHUB_TOKEN` | (none) | GitHub token with only the **gist** scope. Backs the database up to a secret gist on that account, so coins and cosmetics survive restarts on hosts without a permanent disk (Render's free plan). Strongly recommended there |
| `GIST_ID` | (found automatically) | Use a specific gist instead of finding it by its description |

### Keeping data on Render's free plan
Free Render servers lose their disk every time they go to sleep (about 15 minutes without players) or restart.
Set `GITHUB_TOKEN` and the server keeps a copy in a secret gist: it uploads changes within 15 seconds (and right
away when it is stopped) and reads them back when it starts. Login sessions are not part of the copy, so players
simply sign in again (the client does that by itself).

1. Create a token at <https://github.com/settings/tokens/new?scopes=gist&description=Annoying%20Client%20cosmetics>
   (classic token, only the `gist` box ticked, expiration "No expiration" or a date you will remember).
2. In Render: your service > **Environment** > **Add environment variable**: `GITHUB_TOKEN` = the token. Save; Render redeploys.
3. The log shows `created backup gist ...` after the first change, and `restored the database from GitHub gist ...` after restarts.

New players get 100 coins and the Annoying Cape. Everyone can claim 50 coins every 20 hours.

## One-click deploy
[Deploy to Render](https://render.com/deploy?repo=https://github.com/theavgjim/annoying-client) uses `render.yaml`
(free plan; data resets when the server restarts unless you add a disk mounted at `/data`).

## Run it
```bash
go run .
```
or with Docker:
```bash
docker build -t annoying-cosmetics .
docker run -d -p 8080:8080 -v annoying-data:/data annoying-cosmetics
```
Put it behind HTTPS (any host with automatic TLS works: Fly.io, Render, Railway, a VPS with Caddy).
The client only connects over `https://`, except to a server on the same computer.

Then in game: **Right Shift > Wardrobe**, paste the server address, **Connect**.

## Admin actions (in the Wardrobe tab for admins)
Give or take coins, give or remove cosmetics, make or remove admins, send an announcement to everyone,
list players. Every action is checked here on the server and written to the audit log in the database.

## Tests
```bash
go test ./...
```
