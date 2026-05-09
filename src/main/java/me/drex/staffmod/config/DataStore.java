package me.drex.staffmod.config;

import com.google.gson.*;
import me.drex.staffmod.StaffMod;
import me.drex.staffmod.core.StaffModAsync;
import me.drex.staffmod.util.JailManager;
import me.drex.staffmod.features.VanishManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    public static String discordWebhookUrl = ""; // Variable para el Webhook

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_DIR = FabricLoader.getInstance().getConfigDir().resolve("staffmod");
    private static final Path PLAYERS_FILE    = DATA_DIR.resolve("players.json");
    private static final Path JAILS_FILE      = DATA_DIR.resolve("jails.json");
    private static final Path STAFF_STATS_FILE = DATA_DIR.resolve("staff_stats.json");
    private static final Path TICKETS_FILE    = DATA_DIR.resolve("tickets.json");
    private static final Path TOGGLES_FILE    = DATA_DIR.resolve("toggles.json");

    private static final Map<UUID, PlayerData>   players       = new ConcurrentHashMap<>();
    private static final Map<String, JailZone>   jails         = new ConcurrentHashMap<>();
    private static final Map<UUID, StaffProfile> staffProfiles = new ConcurrentHashMap<>();
    private static final Map<Integer, TicketEntry> tickets     = new ConcurrentHashMap<>();

    private static final Set<UUID> onDuty         = Collections.synchronizedSet(new HashSet<>());
    private static final Set<UUID> staffChatToggle = Collections.synchronizedSet(new HashSet<>());

    private static volatile int nextTicketId = 1;
    private static int announceCounter = 0;

    // ───── DUTY ─────
    public static boolean isOnDuty(UUID uuid) { return onDuty.contains(uuid); }
    public static void toggleDuty(UUID uuid) {
        if (!onDuty.remove(uuid)) onDuty.add(uuid);
    }
    public static void setDuty(UUID uuid, boolean state) {
        if (state) onDuty.add(uuid); else onDuty.remove(uuid);
    }

    // ───── STAFF CHAT ─────
    public static boolean isStaffChatToggled(UUID uuid) { return staffChatToggle.contains(uuid); }
    public static void toggleStaffChat(UUID uuid) {
        if (!staffChatToggle.remove(uuid)) staffChatToggle.add(uuid);
        saveAsync(); // FIX: Aseguramos que el estado del toggle se guarde permanentemente
    }

    // ───── PLAYERS ─────
    public static PlayerData getOrCreate(UUID uuid, String name) {
        return players.computeIfAbsent(uuid, k -> new PlayerData(uuid, name));
    }
    public static PlayerData get(UUID uuid) { return players.get(uuid); }
    public static Collection<PlayerData> allPlayers() { return players.values(); }

    // ───── JAILS ─────
    public static Map<String, JailZone> getJails() { return Collections.unmodifiableMap(jails); }
    public static JailZone getJail(String name) { return jails.get(name.toLowerCase()); }
    public static void addJail(JailZone zone) { jails.put(zone.name.toLowerCase(), zone); saveAsync(); }
    public static boolean removeJail(String name) {
        boolean removed = jails.remove(name.toLowerCase()) != null;
        if (removed) {
            players.values().forEach(pd -> {
                if (pd.jailed && pd.jailName.equalsIgnoreCase(name)) {
                    pd.jailed = false; pd.jailExpiry = -1; pd.jailName = "";
                }
            });
            saveAsync();
        }
        return removed;
    }

    // ───── STAFF PROFILES ─────
    public static StaffProfile getStaffProfile(UUID uuid, String name) {
        return staffProfiles.computeIfAbsent(uuid, k -> new StaffProfile(uuid, name));
    }
    public static Collection<StaffProfile> allStaffProfiles() { return staffProfiles.values(); }

    // ───── TICKETS ─────
    public static synchronized TicketEntry createTicket(UUID creatorUuid, String creatorName, String message) {
        TicketEntry t = new TicketEntry(nextTicketId++, creatorUuid, creatorName, message);
        tickets.put(t.id, t);
        saveAsync();
        return t;
    }
    public static TicketEntry getTicket(int id) { return tickets.get(id); }
    public static Collection<TicketEntry> getAllTickets() { return tickets.values(); }
    public static void updateTicket(TicketEntry t) { t.updatedAt = System.currentTimeMillis(); tickets.put(t.id, t); saveAsync(); }
    public static void removeTicket(int id) { tickets.remove(id); saveAsync(); }

    // ───── TICK EVENTS ─────
    public static void tickAnnouncements(MinecraftServer server) {
        if (++announceCounter >= 12000) { // cada 10 min a 20 TPS
            announceCounter = 0;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal(
                    "§8[§bPokeLand§8] §f¿Problema o duda? Usa §e/ticket <mensaje> §fpara contactar al staff."));
            }
        }
    }

    // ───── APLICAR AL UNIRSE ─────
    public static void applyOnJoin(ServerPlayer player) {
        PlayerData pd = getOrCreate(player.getUUID(), player.getName().getString());
        pd.lastName = player.getName().getString();

        if (pd.pendingUnjail) {
            pd.pendingUnjail = false;
            var overworld = player.getServer().overworld();
            var spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(),
                player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Tu cárcel expiró mientras estabas offline. Eres libre."));
            saveAsync();
            return;
        }
        if (pd.isMuteActive()) {
            player.sendSystemMessage(Component.literal(
                "§c[sᴛᴀꜰꜰ] Sigues silenciado. Expira: §e" + PlayerData.formatExpiry(pd.muteExpiry)));
        }
        if (pd.isJailActive()) {
            JailManager.teleportToJail(player, pd.jailName);
            player.sendSystemMessage(Component.literal(
                "§c[sᴛᴀꜰꜰ] Sigues en la cárcel. Expira: §e" + PlayerData.formatExpiry(pd.jailExpiry)));
        }

        // Revisar si tiene tickets con respuestas no leídas
        for (TicketEntry t : tickets.values()) {
            if (t.creatorUuid.equals(player.getUUID()) && t.hasUnreadReply) {
                player.getServer().execute(() -> {
                    player.sendSystemMessage(Component.literal(" "));
                    player.sendSystemMessage(Component.literal("§a§l¡Tienes respuestas nuevas en tu ticket #" + t.id + "!"));
                    for (String reply : t.replies) {
                        player.sendSystemMessage(Component.literal(reply));
                    }
                    player.sendSystemMessage(Component.literal("§7Usa §e/ticket reply " + t.id + " <mensaje> §7para contestar."));
                    player.sendSystemMessage(Component.literal(" "));
                });
                t.hasUnreadReply = false;
                updateTicket(t);
            }
        }
    }

    // ───── CARGA ─────
    public static void load() {
        try {
            Files.createDirectories(DATA_DIR);
            loadPlayers();
            loadJails();
            loadStaffStats();
            loadTickets();
            loadToggles();
        } catch (IOException e) {
            StaffMod.LOGGER.error("[StaffMod] Error creando directorio de datos", e);
        }
    }

    private static void loadPlayers() {
        if (!Files.exists(PLAYERS_FILE)) return;
        try (Reader r = Files.newBufferedReader(PLAYERS_FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                UUID uuid = UUID.fromString(e.getKey());
                JsonObject obj = e.getValue().getAsJsonObject();
                PlayerData pd = new PlayerData(uuid, getS(obj, "lastName"));
                pd.muted        = getB(obj, "muted");
                pd.muteExpiry   = getL(obj, "muteExpiry");
                pd.muteReason   = getS(obj, "muteReason");
                pd.banned       = getB(obj, "banned");
                pd.banExpiry    = getL(obj, "banExpiry");
                pd.banReason    = getS(obj, "banReason");
                pd.frozen       = getB(obj, "frozen");
                pd.jailed       = getB(obj, "jailed");
                pd.jailExpiry   = getL(obj, "jailExpiry");
                pd.jailName     = getS(obj, "jailName");
                pd.pendingUnjail = getB(obj, "pendingUnjail");
                if (obj.has("warns")) {
                    for (JsonElement we : obj.get("warns").getAsJsonArray()) {
                        JsonObject wo = we.getAsJsonObject();
                        pd.warns.add(new PlayerData.WarnEntry(
                            getS(wo, "reason"), getL(wo, "timestamp"),
                            getS(wo, "staffName"), getS(wo, "staffUuid")));
                    }
                }
                players.put(uuid, pd);
            }
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando players.json", e);
        }
    }

    private static void loadJails() {
        if (!Files.exists(JAILS_FILE)) return;
        try (Reader r = Files.newBufferedReader(JAILS_FILE)) {
            JsonArray arr = GSON.fromJson(r, JsonArray.class);
            if (arr == null) return;
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                JailZone z = new JailZone(getS(o, "name"), getS(o, "dimension"),
                    getD(o, "x1"), getD(o, "y1"), getD(o, "z1"),
                    getD(o, "x2"), getD(o, "y2"), getD(o, "z2"));
                if (o.has("spawnX")) { z.spawnX = getD(o, "spawnX"); z.spawnY = getD(o, "spawnY"); z.spawnZ = getD(o, "spawnZ"); }
                jails.put(z.name.toLowerCase(), z);
            }
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando jails.json", e);
        }
    }

    private static void loadStaffStats() {
        if (!Files.exists(STAFF_STATS_FILE)) return;
        try (Reader r = Files.newBufferedReader(STAFF_STATS_FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                UUID uuid = UUID.fromString(e.getKey());
                JsonObject obj = e.getValue().getAsJsonObject();
                StaffProfile sp = new StaffProfile(uuid, getS(obj, "name"));
                sp.bans  = getI(obj, "bans");
                sp.mutes = getI(obj, "mutes");
                sp.warns = getI(obj, "warns");
                sp.jails = getI(obj, "jails");
                sp.kicks = getI(obj, "kicks");
                if (obj.has("history"))
                    for (JsonElement he : obj.get("history").getAsJsonArray())
                        sp.recentHistory.add(he.getAsString());
                staffProfiles.put(uuid, sp);
            }
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando staff_stats.json", e);
        }
    }

    private static void loadTickets() {
        if (!Files.exists(TICKETS_FILE)) return;
        try (Reader r = Files.newBufferedReader(TICKETS_FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            nextTicketId = getI(root, "nextTicketId");
            if (nextTicketId < 1) nextTicketId = 1;
            if (root.has("tickets")) {
                for (JsonElement el : root.get("tickets").getAsJsonArray()) {
                    JsonObject obj = el.getAsJsonObject();
                    TicketEntry t = new TicketEntry(
                        getI(obj, "id"),
                        UUID.fromString(getS(obj, "creatorUuid")),
                        getS(obj, "creatorName"),
                        getS(obj, "message"));
                    t.status     = getS(obj, "status");
                    t.handledBy  = getS(obj, "handledBy");
                    t.createdAt  = getL(obj, "createdAt");
                    t.updatedAt  = getL(obj, "updatedAt");
                    
                    // Cargar respuestas
                    if (obj.has("replies")) {
                        for (JsonElement re : obj.get("replies").getAsJsonArray()) {
                            t.replies.add(re.getAsString());
                        }
                    }
                    if (obj.has("hasUnreadReply")) {
                        t.hasUnreadReply = obj.get("hasUnreadReply").getAsBoolean();
                    }
                    
                    tickets.put(t.id, t);
                }
            }
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando tickets.json", e);
        }
    }

    private static void loadToggles() {
        if (!Files.exists(TOGGLES_FILE)) return;
        try (Reader r = Files.newBufferedReader(TOGGLES_FILE)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            
            // Cargar URL del Webhook
            if (root.has("discordWebhook")) {
                discordWebhookUrl = root.get("discordWebhook").getAsString();
            }

            if (root.has("staffChat"))
                for (JsonElement el : root.get("staffChat").getAsJsonArray())
                    staffChatToggle.add(UUID.fromString(el.getAsString()));
            // BUG #8 FIX: cargar vanish persistido para que applyVanishOnJoin funcione tras reinicio
            if (root.has("vanished"))
                for (JsonElement el : root.get("vanished").getAsJsonArray())
                    VanishManager.getVanishedSet().add(UUID.fromString(el.getAsString()));
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando toggles.json", e);
        }
    }

    // ───── GUARDADO ─────
    /** Guardado asíncrono (normal durante juego). */
    public static void saveAsync() {
        StaffModAsync.runAsync(DataStore::save);
    }

    /** Guardado síncrono (solo para shutdown). */
    public static synchronized void save() {
        try {
            Files.createDirectories(DATA_DIR);
            savePlayers();
            saveJails();
            saveStaffStats();
            saveTickets();
            saveToggles();
        } catch (IOException e) {
            StaffMod.LOGGER.error("[StaffMod] Error guardando datos", e);
        }
    }

    private static void savePlayers() throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, PlayerData> e : players.entrySet()) {
            PlayerData pd = e.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("lastName",    pd.lastName);
            obj.addProperty("muted",       pd.muted);
            obj.addProperty("muteExpiry",  pd.muteExpiry);
            obj.addProperty("muteReason",  pd.muteReason);
            obj.addProperty("banned",      pd.banned);
            obj.addProperty("banExpiry",   pd.banExpiry);
            obj.addProperty("banReason",   pd.banReason);
            obj.addProperty("frozen",      pd.frozen);
            obj.addProperty("jailed",      pd.jailed);
            obj.addProperty("jailExpiry",  pd.jailExpiry);
            obj.addProperty("jailName",    pd.jailName);
            obj.addProperty("pendingUnjail", pd.pendingUnjail);
            JsonArray warns = new JsonArray();
            for (PlayerData.WarnEntry w : pd.warns) {
                JsonObject wo = new JsonObject();
                wo.addProperty("reason",    w.reason());
                wo.addProperty("timestamp", w.timestamp());
                wo.addProperty("staffName", w.staffName());
                wo.addProperty("staffUuid", w.staffUuid());
                warns.add(wo);
            }
            obj.add("warns", warns);
            root.add(e.getKey().toString(), obj);
        }
        atomicWrite(PLAYERS_FILE, GSON.toJson(root));
    }

    private static void saveJails() throws IOException {
        JsonArray arr = new JsonArray();
        for (JailZone z : jails.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name",      z.name);
            o.addProperty("dimension", z.dimension);
            o.addProperty("x1", z.x1); o.addProperty("y1", z.y1); o.addProperty("z1", z.z1);
            o.addProperty("x2", z.x2); o.addProperty("y2", z.y2); o.addProperty("z2", z.z2);
            o.addProperty("spawnX", z.spawnX); o.addProperty("spawnY", z.spawnY); o.addProperty("spawnZ", z.spawnZ);
            arr.add(o);
        }
        atomicWrite(JAILS_FILE, GSON.toJson(arr));
    }

    private static void saveStaffStats() throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, StaffProfile> e : staffProfiles.entrySet()) {
            StaffProfile sp = e.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("name",  sp.name);
            obj.addProperty("bans",  sp.bans);
            obj.addProperty("mutes", sp.mutes);
            obj.addProperty("warns", sp.warns);
            obj.addProperty("jails", sp.jails);
            obj.addProperty("kicks", sp.kicks);
            JsonArray hist = new JsonArray();
            for (String h : sp.recentHistory) hist.add(h);
            obj.add("history", hist);
            root.add(e.getKey().toString(), obj);
        }
        atomicWrite(STAFF_STATS_FILE, GSON.toJson(root));
    }

    private static void saveTickets() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("nextTicketId", nextTicketId);
        JsonArray arr = new JsonArray();
        for (TicketEntry t : tickets.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id",          t.id);
            obj.addProperty("creatorUuid", t.creatorUuid.toString());
            obj.addProperty("creatorName", t.creatorName);
            obj.addProperty("message",     t.message);
            obj.addProperty("status",      t.status);
            obj.addProperty("handledBy",   t.handledBy);
            obj.addProperty("createdAt",   t.createdAt);
            obj.addProperty("updatedAt",   t.updatedAt);
            
            // Guardar respuestas
            JsonArray repliesArray = new JsonArray();
            for (String reply : t.replies) {
                repliesArray.add(reply);
            }
            obj.add("replies", repliesArray);
            obj.addProperty("hasUnreadReply", t.hasUnreadReply);

            arr.add(obj);
        }
        root.add("tickets", arr);
        atomicWrite(TICKETS_FILE, GSON.toJson(root));
    }

    private static void saveToggles() throws IOException {
        JsonObject root = new JsonObject();
        
        // Guardar URL del Webhook
        root.addProperty("discordWebhook", discordWebhookUrl);

        JsonArray scArr = new JsonArray();
        for (UUID u : staffChatToggle) scArr.add(u.toString());
        root.add("staffChat", scArr);
        
        // BUG #8 FIX: persistir el set de vanish para sobrevivir reinicios
        JsonArray vanishArr = new JsonArray();
        for (UUID u : VanishManager.getVanishedSet()) vanishArr.add(u.toString());
        root.add("vanished", vanishArr);
        
        atomicWrite(TOGGLES_FILE, GSON.toJson(root));
    }

    /** Escritura atómica: escribe en .tmp y renombra para evitar corrupción. */
    private static void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ───── HELPERS JSON ─────
    private static boolean getB(JsonObject o, String k) { return o.has(k) && o.get(k).getAsBoolean(); }
    private static long    getL(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsLong()   : -1L; }
    private static String  getS(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }
    private static double  getD(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsDouble() : 0.0; }
    private static int     getI(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsInt()    : 0; }
}
