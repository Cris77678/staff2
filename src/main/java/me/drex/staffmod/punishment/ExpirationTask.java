package me.drex.staffmod.punishment;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ExpirationTask implements Runnable {

    private final MinecraftServer server;

    public ExpirationTask(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        // FIX: Toda modificación de PlayerData y acciones sobre jugadores online
        // se ejecutan en el main thread mediante server.execute().
        // ExpirationTask corre en el hilo SCHEDULER (async), y los campos de
        // PlayerData no son volátiles, por lo que escribirlos directamente desde
        // un hilo async puede causar condiciones de carrera con el hilo principal
        // (MovementMixin, ChatMixin, etc. leen esos mismos campos).

        // ── Jugadores ONLINE ────────────────────────────────────────────────
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData pd = DataStore.get(player.getUUID());
            if (pd == null) continue;

            // Mute expirado online
            if (pd.muted && pd.muteExpiry != -1 && now >= pd.muteExpiry) {
                server.execute(() -> {
                    pd.muted      = false;
                    pd.muteExpiry = -1;
                    pd.muteReason = "";
                    DataStore.saveAsync();
                    player.sendSystemMessage(Component.literal(
                        "§a[sᴛᴀꜰꜰ] Tu silencio ha expirado. Ya puedes hablar de nuevo."));
                });
            }

            // Jail expirado online
            if (pd.jailed && pd.jailExpiry != -1 && now >= pd.jailExpiry) {
                server.execute(() -> {
                    pd.jailed        = false;
                    pd.jailName      = "";
                    pd.jailExpiry    = -1;
                    pd.pendingUnjail = false;
                    DataStore.saveAsync();
                    var overworld = server.overworld();
                    var spawn = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(),
                        player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal(
                        "§a[sᴛᴀꜰꜰ] Has cumplido tu tiempo en prisión. Eres libre."));
                });
            }

            // Ban expirado online
            if (pd.banned && pd.banExpiry != -1 && now >= pd.banExpiry) {
                server.execute(() -> {
                    pd.banned    = false;
                    pd.banExpiry = -1;
                    DataStore.saveAsync();
                });
            }
        }

        // ── Jugadores OFFLINE ────────────────────────────────────────────────
        // Detectar jails/mutes/bans expirados mientras el jugador estaba desconectado.
        // Para jail: marcar pendingUnjail=true; applyOnJoin() lo libera al reconectarse.
        for (PlayerData pd : DataStore.allPlayers()) {
            boolean isOnline = server.getPlayerList().getPlayer(pd.uuid) != null;
            if (isOnline) continue;

            // Mute expirado offline
            if (pd.muted && pd.muteExpiry != -1 && now >= pd.muteExpiry) {
                server.execute(() -> {
                    pd.muted      = false;
                    pd.muteExpiry = -1;
                    pd.muteReason = "";
                    DataStore.saveAsync();
                });
            }

            // Jail expirado offline
            if (pd.jailed && pd.jailExpiry != -1 && now >= pd.jailExpiry) {
                server.execute(() -> {
                    pd.jailed        = false;
                    pd.jailName      = "";
                    pd.jailExpiry    = -1;
                    pd.pendingUnjail = true; // applyOnJoin() lo teletransportará al spawn
                    DataStore.saveAsync();
                });
            }

            // Ban expirado offline
            if (pd.banned && pd.banExpiry != -1 && now >= pd.banExpiry) {
                server.execute(() -> {
                    pd.banned    = false;
                    pd.banExpiry = -1;
                    DataStore.saveAsync();
                });
            }
        }
    }
}
