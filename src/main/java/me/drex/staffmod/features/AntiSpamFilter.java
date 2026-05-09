package me.drex.staffmod.features;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiSpamFilter {

    private static final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> warnings = new ConcurrentHashMap<>();

    private static final long COOLDOWN_MS = 1500L;
    private static final int MAX_WARNINGS = 3;
    private static final long MUTE_DURATION_MS = 5L * 60L * 1000L;

    /** @return true si el mensaje es válido, false si se bloquea. */
    public static boolean checkChat(ServerPlayer player, String message) {
        if (PermissionUtil.has(player, "staffmod.use")) return true;

        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        long last = lastMessage.getOrDefault(uuid, 0L);

        if (now - last < COOLDOWN_MS) {
            int w = warnings.getOrDefault(uuid, 0) + 1;
            warnings.put(uuid, w);

            if (w >= MAX_WARNINGS) {
                PlayerData pd = DataStore.getOrCreate(uuid, player.getName().getString());
                pd.muted = true;
                pd.muteExpiry = now + MUTE_DURATION_MS;
                pd.muteReason = "AutoMod - SPAM";
                DataStore.saveAsync();
                warnings.put(uuid, 0);
                player.sendSystemMessage(Component.literal(
                    "§c[ᴀᴜᴛᴏᴍᴏᴅ] Has sido silenciado 5 minutos por SPAM excesivo."));
                notifyStaff(player.getServer(), player.getName().getString());
            } else {
                player.sendSystemMessage(Component.literal(
                    "§c[ᴀᴜᴛᴏᴍᴏᴅ] Enviando mensajes muy rápido. Advertencia " + w + "/" + MAX_WARNINGS + "."));
            }
            return false;
        }

        lastMessage.put(uuid, now);
        if (now - last > 5000 && warnings.containsKey(uuid)) {
            warnings.computeIfPresent(uuid, (k, v) -> Math.max(0, v - 1));
        }
        return true;
    }

    private static void notifyStaff(net.minecraft.server.MinecraftServer server, String playerName) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (PermissionUtil.has(p, "staffmod.use")) {
                p.sendSystemMessage(Component.literal(
                    "§8[§cAlert§8] §eAutoMod silenció a §f" + playerName + " §epor SPAM."));
            }
        }
    }
}
