package me.drex.staffmod.features;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.RankConfig;
import me.drex.staffmod.config.RankManager;
import me.drex.staffmod.core.StaffModAsync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;

public class StaffConnectionHandler {

    public static void onStaffJoin(ServerPlayer player) {
        RankConfig rank = RankManager.getHighestRank(player);
        if (rank == null) return;

        // Activar turno por defecto al conectarse
        DataStore.setDuty(player.getUUID(), true);

        // Anuncio de conexión
        String joinMsg = "§8[§bsᴛᴀꜰꜰ§8] " + rank.color + rank.prefix + " "
            + player.getName().getString() + " §eha entrado y está disponible.";
        for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(joinMsg));
        }

        // Resumen diferido 3 segundos (espera que el jugador cargue el mundo)
        StaffModAsync.scheduleOnce(() -> {
            long openTickets = DataStore.getAllTickets().stream()
                .filter(t -> "ABIERTO".equals(t.status)).count();

            player.getServer().execute(() -> {
                player.sendSystemMessage(Component.literal("§8═══════════════════════════════════"));
                player.sendSystemMessage(Component.literal("§6§l¡Bienvenido a tu turno, §f" + player.getName().getString() + "§6!"));
                player.sendSystemMessage(Component.literal(" "));
                if (openTickets > 0) {
                    player.sendSystemMessage(Component.literal(
                        "§c⚠ §l" + openTickets + " §ctickets pendientes sin atender."));
                    player.sendSystemMessage(Component.literal("§7Usa §e/staff §7→ Panel de Tickets para atenderlos."));
                } else {
                    player.sendSystemMessage(Component.literal("§a✔ No hay tickets pendientes. ¡Buen trabajo!"));
                }
                player.sendSystemMessage(Component.literal("§8═══════════════════════════════════"));
            });
        }, 3, TimeUnit.SECONDS);
    }
}
