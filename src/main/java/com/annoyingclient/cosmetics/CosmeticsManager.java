package com.annoyingclient.cosmetics;

import com.annoyingclient.AnnoyingClient;
import com.annoyingclient.module.impl.visual.CosmeticsModule;
import com.annoyingclient.notification.NotificationType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Talks to the cosmetics server and decides which cape and trail each player shows.
 *
 * <p>Sign-in works like joining a Minecraft server: the server hands out a random server id, the
 * client asks Mojang to "join" it with the player's own session, and the server checks with Mojang
 * who joined. All network calls run on a background thread; results are applied on the game thread.
 */
public final class CosmeticsManager {

    public enum Status { NO_SERVER, SIGNED_OUT, SIGNING_IN, SIGNED_IN, ERROR }

    /** The signed-in player's data as the server returned it. */
    public record Profile(String name, int coins, Set<String> owned, Map<String, String> equipped, boolean admin, boolean owner, boolean dailyReady) {
    }

    private static final Gson GSON = new Gson();

    private final Minecraft mc = Minecraft.getInstance();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Annoying Client Cosmetics");
        t.setDaemon(true);
        return t;
    });

    private volatile Status status = Status.NO_SERVER;
    private volatile String error = "";
    private volatile String token;
    private volatile Profile profile;
    private volatile List<Cosmetic> catalog = Cosmetic.BUILTIN;
    private final Map<UUID, Map<String, String>> others = new ConcurrentHashMap<>();
    private final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private String connectedUrl = "";
    private long nextLookup;
    private long nextAnnouncementCheck;
    private long nextRetry;
    private int failedSignIns;
    private int lastAnnouncement = -1;
    private int tickCounter;

    private CosmeticsModule module() {
        return AnnoyingClient.modules().get(CosmeticsModule.class);
    }

    public Status status() {
        return status;
    }

    public String error() {
        return error;
    }

    public Profile profile() {
        return profile;
    }

    public List<Cosmetic> catalog() {
        return catalog;
    }

    public Cosmetic find(String id) {
        for (Cosmetic c : catalog) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Validates a server URL: https everywhere, plain http only for this computer. Returns an error or null. */
    public static String checkUrl(String url) {
        if (url.isBlank()) {
            return null;
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost();
            if (host == null) {
                return "Enter a full address such as https://cosmetics.example.com";
            }
            boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
            if ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()) && local) {
                return null;
            }
            return "Use https:// (plain http only works for a server on this computer)";
        } catch (IllegalArgumentException e) {
            return "That address is not valid";
        }
    }

    private String baseUrl() {
        CosmeticsModule m = module();
        String url = m == null ? "" : m.serverUrl().trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    // ---- game-thread hooks --------------------------------------------------------------------

    /** Called every client tick. */
    public void tick() {
        CosmeticsModule m = module();
        if (m == null || !m.isEnabled()) {
            return;
        }
        String url = baseUrl();
        if (!url.equals(connectedUrl)) {
            connectedUrl = url;
            token = null;
            profile = null;
            others.clear();
            status = url.isEmpty() ? Status.NO_SERVER : checkUrl(url) != null ? Status.ERROR : Status.SIGNED_OUT;
            error = url.isEmpty() ? "" : checkUrl(url) == null ? "" : checkUrl(url);
            if (status == Status.SIGNED_OUT) {
                loadCatalog();
                signIn(null);
            }
        }
        long now = System.currentTimeMillis();
        // The server restarted and forgot our login (sessions are never backed up): sign in again quietly.
        if (status == Status.SIGNED_OUT && token == null && checkUrl(url) == null && now >= nextRetry) {
            nextRetry = now + 5_000;
            signIn(null);
        }
        if (status == Status.ERROR && checkUrl(url) == null && nextRetry > 0 && now >= nextRetry) {
            nextRetry = 0;
            signIn(null);
        }
        if (status != Status.NO_SERVER && status != Status.ERROR && mc.level != null && now >= nextLookup) {
            nextLookup = now + 30_000;
            lookupNearby();
        }
        if (status == Status.SIGNED_IN && now >= nextAnnouncementCheck) {
            nextAnnouncementCheck = now + 60_000;
            checkAnnouncement();
        }
        if (m.showTrails() && mc.level != null && ++tickCounter % 2 == 0) {
            spawnTrails();
        }
    }

    /** The cape id a player should show, or null. */
    public String capeFor(UUID uuid) {
        return equipped(uuid, "cape");
    }

    public String trailFor(UUID uuid) {
        return equipped(uuid, "trail");
    }

    private String equipped(UUID uuid, String type) {
        CosmeticsModule m = module();
        if (m == null || !m.isEnabled() || uuid == null) {
            return null;
        }
        boolean self = mc.getUser() != null && uuid.equals(mc.getUser().getProfileId());
        if (self) {
            Profile p = profile;
            if (p != null) {
                return p.equipped().get(type);
            }
            String preview = type.equals("cape") ? m.previewCape() : m.previewTrail();
            return status == Status.NO_SERVER && !preview.isEmpty() ? preview : null;
        }
        if (!m.showOthers()) {
            return null;
        }
        Map<String, String> eq = others.get(uuid);
        return eq == null ? null : eq.get(type);
    }

    public static ClientAsset.ResourceTexture capeTexture(String id) {
        if (id == null || !Cosmetic.known(id)) {
            return null;
        }
        return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(AnnoyingClient.MOD_ID, "cape/" + id));
    }

    private void spawnTrails() {
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        Set<UUID> seen = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (mc.player != null && p.distanceToSqr(mc.player) > 48 * 48) {
                continue;
            }
            UUID id = p.getUUID();
            seen.add(id);
            String trail = trailFor(id);
            Vec3 pos = p.position();
            Vec3 last = lastPositions.put(id, pos);
            if (trail == null || last == null || pos.distanceToSqr(last) < 0.0004 || p.isInvisible()) {
                continue;
            }
            ParticleOptions type = switch (trail) {
                case "trail_hearts" -> ParticleTypes.HEART;
                case "trail_flames" -> ParticleTypes.FLAME;
                case "trail_sparkle" -> ParticleTypes.END_ROD;
                case "trail_notes" -> ParticleTypes.NOTE;
                default -> null;
            };
            if (type == null) {
                continue;
            }
            double ox = (Math.random() - 0.5) * 0.4, oz = (Math.random() - 0.5) * 0.4;
            double vx = type == ParticleTypes.NOTE ? Math.random() : 0;
            mc.level.addParticle(type, pos.x + ox, pos.y + 0.15, pos.z + oz, vx, 0.02, 0);
        }
        lastPositions.keySet().retainAll(seen);
    }

    // ---- server calls -------------------------------------------------------------------------

    private JsonElement request(String method, String path, Object body, boolean auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl() + path)).timeout(Duration.ofSeconds(70)) // the free host needs up to a minute to wake up
                .header("Accept", "application/json").header("User-Agent", "AnnoyingClient/" + AnnoyingClient.getInstance().getVersion());
        if (auth && token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        JsonElement json = resp.body().isBlank() ? new JsonObject() : JsonParser.parseString(resp.body());
        if (resp.statusCode() >= 400) {
            String msg = json.isJsonObject() && json.getAsJsonObject().has("error") ? json.getAsJsonObject().get("error").getAsString() : "HTTP " + resp.statusCode();
            if (resp.statusCode() == 401) {
                token = null;
                profile = null;
                status = Status.SIGNED_OUT;
            }
            throw new IllegalStateException(capitalize(msg));
        }
        return json;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void async(String what, Callable task, Consumer<String> done) {
        io.execute(() -> {
            String err = null;
            try {
                task.run();
            } catch (Exception e) {
                err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                AnnoyingClient.LOGGER.debug("Cosmetics {} failed: {}", what, e.toString());
            }
            String result = err;
            mc.execute(() -> {
                if (done != null) {
                    done.accept(result);
                }
            });
        });
    }

    @FunctionalInterface
    private interface Callable {
        void run() throws Exception;
    }

    private void loadCatalog() {
        async("catalog", () -> {
            JsonElement json = request("GET", "/v1/catalog", null, false);
            List<Cosmetic> list = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) {
                Cosmetic c = GSON.fromJson(e, Cosmetic.class);
                if (Cosmetic.known(c.id())) {
                    list.add(c); // only cosmetics this client can draw
                }
            }
            if (!list.isEmpty()) {
                catalog = List.copyOf(list);
            }
        }, null);
    }

    /** Signs in with the player's Minecraft session. {@code done} gets an error message or null. */
    public void signIn(Consumer<String> done) {
        if (baseUrl().isEmpty() || status == Status.SIGNING_IN) {
            return;
        }
        status = Status.SIGNING_IN;
        error = "";
        async("sign-in", () -> {
            String serverId = request("POST", "/v1/auth/start", null, false).getAsJsonObject().get("serverId").getAsString();
            var user = mc.getUser();
            try {
                mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            } catch (Exception e) {
                // The server's Mojang check will refuse the sign-in and explain why.
                AnnoyingClient.LOGGER.debug("Mojang join failed: {}", e.toString());
            }
            Map<String, String> body = Map.of("username", user.getName(), "serverId", serverId);
            JsonObject out = request("POST", "/v1/auth/finish", body, false).getAsJsonObject();
            token = out.get("token").getAsString();
            applyProfile(out.getAsJsonObject("profile"));
        }, err -> {
            if (err == null) {
                status = Status.SIGNED_IN;
                nextRetry = 0; // a later sign-out (server restart) signs in again right away
                failedSignIns = 0;
                nextLookup = 0;
                nextAnnouncementCheck = 0;
            } else {
                status = Status.ERROR;
                error = err;
                // Try again soon after a first failure (a hiccup, a sleeping server waking up), then every 2 minutes.
                nextRetry = System.currentTimeMillis() + (failedSignIns++ == 0 ? 15_000 : 120_000);
            }
            if (done != null) {
                done.accept(err);
            }
        });
    }

    private void applyProfile(JsonObject p) {
        Set<String> owned = new HashSet<>();
        if (p.has("owned") && p.get("owned").isJsonArray()) {
            for (JsonElement e : p.getAsJsonArray("owned")) {
                owned.add(e.getAsString());
            }
        }
        Map<String, String> equipped = new HashMap<>();
        if (p.has("equipped") && p.get("equipped").isJsonObject()) {
            for (var e : p.getAsJsonObject("equipped").entrySet()) {
                equipped.put(e.getKey(), e.getValue().getAsString());
            }
        }
        profile = new Profile(p.get("name").getAsString(), p.get("coins").getAsInt(), Set.copyOf(owned), Map.copyOf(equipped),
                p.get("admin").getAsBoolean(), p.has("owner") && p.get("owner").getAsBoolean(), p.has("dailyReady") && p.get("dailyReady").getAsBoolean());
    }

    private void lookupNearby() {
        if (mc.level == null) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (ids.size() < 100) {
                ids.add(p.getUUID().toString().replace("-", ""));
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        async("lookup", () -> {
            JsonObject json = request("GET", "/v1/players?uuids=" + String.join(",", ids), null, false).getAsJsonObject();
            Map<UUID, Map<String, String>> fresh = new HashMap<>();
            for (var e : json.entrySet()) {
                String raw = e.getKey();
                if (raw.length() != 32) {
                    continue;
                }
                UUID uuid = UUID.fromString(raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5"));
                Map<String, String> eq = new HashMap<>();
                for (var t : e.getValue().getAsJsonObject().entrySet()) {
                    eq.put(t.getKey(), t.getValue().getAsString());
                }
                fresh.put(uuid, eq);
            }
            others.clear();
            others.putAll(fresh);
        }, null);
    }

    private void checkAnnouncement() {
        async("announcement", () -> {
            JsonObject json = request("GET", "/v1/announcement", null, false).getAsJsonObject();
            if (!json.has("announcement") || json.get("announcement").isJsonNull()) {
                return;
            }
            JsonObject a = json.getAsJsonObject("announcement");
            int id = a.get("id").getAsInt();
            if (lastAnnouncement >= 0 && id > lastAnnouncement) {
                String msg = a.get("message").getAsString();
                String by = a.get("by").getAsString();
                mc.execute(() -> AnnoyingClient.notifications().push(NotificationType.INFO, "Announcement from " + by, msg));
            }
            lastAnnouncement = Math.max(lastAnnouncement, id);
        }, null);
    }

    /** Runs a call that returns the updated profile. */
    private void profileCall(String path, Object body, Consumer<String> done) {
        async(path, () -> applyProfile(request("POST", path, body, true).getAsJsonObject()), err -> {
            if (err == null) {
                nextLookup = 0; // refresh what others see right away
            }
            if (done != null) {
                done.accept(err);
            }
        });
    }

    public void claimDaily(Consumer<String> done) {
        profileCall("/v1/daily", null, done);
    }

    public void buy(String id, Consumer<String> done) {
        profileCall("/v1/buy", Map.of("id", id), done);
    }

    public void equip(String type, String id, Consumer<String> done) {
        profileCall("/v1/equip", Map.of("type", type, "id", id == null ? "" : id), done);
    }

    public void refresh(Consumer<String> done) {
        async("me", () -> applyProfile(request("GET", "/v1/me", null, true).getAsJsonObject()), done);
    }

    // ---- admin ----------------------------------------------------------------------------------

    /** Runs an admin action; {@code done} gets an error or null. The server checks admin rights. */
    public void admin(String action, Map<String, Object> body, Consumer<String> done) {
        async("admin " + action, () -> request("POST", "/v1/admin/" + action, body, true), err -> {
            if (err == null) {
                refresh(null);
                nextLookup = 0;
            }
            done.accept(err);
        });
    }

    public void shutdown() {
        io.shutdownNow();
    }

    /** Used by the wardrobe's player list for admins. */
    public void adminPlayers(Consumer<List<String>> done) {
        io.execute(() -> {
            List<String> names = new ArrayList<>();
            try {
                JsonArray arr = request("GET", "/v1/admin/players", null, true).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    names.add(o.get("name").getAsString() + "  ·  " + o.get("coins").getAsInt() + " coins" + (o.has("owner") && o.get("owner").getAsBoolean() ? "  ·  owner" : o.get("admin").getAsBoolean() ? "  ·  admin" : ""));
                }
            } catch (Exception ignored) {
                // shown as an empty list
            }
            mc.execute(() -> done.accept(names));
        });
    }
}
