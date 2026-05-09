package me.drex.staffmod.features;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Pokemon;
import me.drex.staffmod.StaffMod;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.util.DiscordWebhook;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Alertas Cobblemon 1.7.3.
 *
 * Desde Cobblemon 1.7.0, Observable#subscribe acepta un Java Consumer<T>
 * (sin retorno kotlin.Unit). Ver changelog 1.7.0:
 * "Added new Observable#subscribe methods that take Java Consumers to make
 *  usage in Java a little cleaner."
 *
 * IVs hereda de HashMap<Stat, Int> en Kotlin → cast a Map<Stat, Integer>.
 */
public class CobblemonAlerts {

    public static void registerEvents() {
        // subscribe() con Consumer<T> — NO retorna kotlin.Unit.INSTANCE
        CobblemonEvents.POKEMON_CAPTURED.subscribe(event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                if (!(event.getPlayer() instanceof ServerPlayer player)) return;

                boolean isLegendary = pokemon.isLegendary();
                boolean isShiny     = pokemon.getShiny();

                @SuppressWarnings("unchecked")
                Map<Stat, Integer> ivs = (Map<Stat, Integer>) (Object) pokemon.getIvs();
                boolean perfectIvs =
                    ivs.getOrDefault(Stats.HP, 0) == 31
                    && ivs.getOrDefault(Stats.ATTACK, 0) == 31
                    && ivs.getOrDefault(Stats.DEFENCE, 0) == 31
                    && ivs.getOrDefault(Stats.SPECIAL_ATTACK, 0) == 31
                    && ivs.getOrDefault(Stats.SPECIAL_DEFENCE, 0) == 31
                    && ivs.getOrDefault(Stats.SPEED, 0) == 31;

                if (!isLegendary && !isShiny && !perfectIvs) return;

                StringBuilder reason = new StringBuilder();
                if (isLegendary) reason.append("Legendario ");
                if (isShiny)     reason.append("Shiny ");
                if (perfectIvs)  reason.append("IVs-6x31 ");
                String reasonStr = reason.toString().trim();

                String speciesName = pokemon.getSpecies().getName();
                String msg = "\u00a78[\u00a7cA\u0280\u1d07\u0280\u1d1b\u1d00\u00a78] \u00a7f"
                    + player.getName().getString()
                    + " \u00a7ecaptur\u00f3 \u00a7d" + speciesName
                    + " \u00a77(" + reasonStr + ")";

                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    if (PermissionUtil.has(p, "staffmod.use") && DataStore.isOnDuty(p.getUUID())) {
                        p.sendSystemMessage(Component.literal(msg));
                    }
                }

                AuditLogManager.log("Sistema", "COBBLEMON_ALERT",
                    player.getName().getString(), speciesName + " | " + reasonStr);
                DiscordWebhook.sendEmbed("Captura Sospechosa",
                    "**Jugador:** " + player.getName().getString()
                    + "\n**Pokemon:** " + speciesName
                    + "\n**Razon:** " + reasonStr, 0xFF00FF);

            } catch (Exception e) {
                StaffMod.LOGGER.error("[StaffMod] Error en alerta Cobblemon:", e);
            }
        });

        StaffMod.LOGGER.info("[StaffMod] Modulo de alertas Cobblemon registrado.");
    }
}
