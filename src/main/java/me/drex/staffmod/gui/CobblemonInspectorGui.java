package me.drex.staffmod.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.logging.AuditLogManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Inspector de party Cobblemon 1.7.3.
 *
 * API REAL verificada contra el código original que compilaba:
 *  - getParty(ServerPlayer): PlayerPartyStore
 *  - party.size(): int (devuelve nº de pokemon presentes, no 6 siempre)
 *  - party.get(int): Pokemon (puede ser null)
 *  - pokemon.getIvs(): cast a Map<Stat,Integer> — funciona en runtime (IVs extiende HashMap)
 *  - pokemon.getEvs(): igual que IVs
 *  - moveSet.getMoves(): List/Array — size() funciona
 *  - moveSet.get(int): Move (puede ser null)
 *  - pokemon.heldItem(): propiedad Kotlin, llamada directa (NO getHeldItem())
 */
public class CobblemonInspectorGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;

    public CobblemonInspectorGui(ServerPlayer staff, ServerPlayer target) {
        super(MenuType.GENERIC_9x4, staff, false);
        this.staff  = staff;
        this.target = target;
        setTitle(Component.literal("§8✦ §3Party de §f" + target.getName().getString()));
        AuditLogManager.log(
            staff.getName().getString(), staff.getUUID().toString(),
            "PARTY_INSPECT", target.getName().getString(), target.getUUID().toString(),
            "Inspeccionó party");
        build();
    }

    private void build() {
        // Fondo completo
        for (int i = 0; i < getSize(); i++) {
            setSlot(i, new GuiElementBuilder(Items.LIGHT_BLUE_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(target);

        if (party == null) {
            setSlot(13, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cParty no disponible"))
                .addLoreLine(Component.literal("§7El jugador no tiene party inicializada."))
                .build());
            buildFooter();
            return;
        }

        // API REAL: party.size() devuelve cuántos Pokémon tiene el jugador (no siempre 6)
        int partySize = party.size();
        int[] displaySlots = {10, 11, 12, 13, 14, 15};
        int shown = 0;

        for (int i = 0; i < partySize && i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null) continue;
            setSlot(displaySlots[shown], buildPokemonButton(pokemon).build());
            shown++;
        }

        if (shown == 0) {
            setSlot(13, new GuiElementBuilder(Items.GRAY_DYE)
                .setName(Component.literal("§7Party vacía"))
                .addLoreLine(Component.literal(
                    "§7" + target.getName().getString() + " no tiene Pokémon en su party."))
                .build());
        }

        buildFooter();
    }

    private GuiElementBuilder buildPokemonButton(Pokemon pokemon) {
        boolean isShiny = pokemon.getShiny();
        String specName = pokemon.getSpecies().getName();
        int level       = pokemon.getLevel();

        String natureName = "?";
        try { natureName = pokemon.getNature().getName().getPath(); } catch (Exception ignored) {}

        String abilityName = "?";
        try { abilityName = pokemon.getAbility().getName(); } catch (Exception ignored) {}

        // IVs — cast unchecked verificado: IVs extiende HashMap<Stat, Integer> en Cobblemon 1.7.3
        @SuppressWarnings("unchecked")
        Map<Stat, Integer> ivs = (Map<Stat, Integer>) (Object) pokemon.getIvs();
        int ivHp  = ivs.getOrDefault(Stats.HP, 0);
        int ivAtk = ivs.getOrDefault(Stats.ATTACK, 0);
        int ivDef = ivs.getOrDefault(Stats.DEFENCE, 0);
        int ivSpa = ivs.getOrDefault(Stats.SPECIAL_ATTACK, 0);
        int ivSpd = ivs.getOrDefault(Stats.SPECIAL_DEFENCE, 0);
        int ivSpe = ivs.getOrDefault(Stats.SPEED, 0);
        int totalIvs = ivHp + ivAtk + ivDef + ivSpa + ivSpd + ivSpe;
        int ivPct    = (totalIvs * 100) / 186;

        // EVs — mismo patrón que IVs
        @SuppressWarnings("unchecked")
        Map<Stat, Integer> evs = (Map<Stat, Integer>) (Object) pokemon.getEvs();
        int evHp  = evs.getOrDefault(Stats.HP, 0);
        int evAtk = evs.getOrDefault(Stats.ATTACK, 0);
        int evDef = evs.getOrDefault(Stats.DEFENCE, 0);
        int evSpa = evs.getOrDefault(Stats.SPECIAL_ATTACK, 0);
        int evSpd = evs.getOrDefault(Stats.SPECIAL_DEFENCE, 0);
        int evSpe = evs.getOrDefault(Stats.SPEED, 0);
        int totalEvs = evHp + evAtk + evDef + evSpa + evSpd + evSpe;

        // Movimientos — API REAL: getMoves() existe y tiene size()
        MoveSet moveSet = pokemon.getMoveSet();
        StringBuilder movesStr = new StringBuilder();
        for (int m = 0; m < moveSet.getMoves().size(); m++) {
            var move = moveSet.get(m);
            if (move != null) {
                if (movesStr.length() > 0) movesStr.append(", ");
                movesStr.append(move.getName());
            }
        }
        if (movesStr.length() == 0) movesStr.append("Sin movimientos");

        // Ítem sostenido — API REAL: pokemon.heldItem() (propiedad Kotlin, llamada directa)
        String heldItemName = "Ninguno";
        try {
            var held = pokemon.heldItem();
            if (held != null && !held.isEmpty()) {
                heldItemName = held.getItem().toString()
                    .replace("minecraft:", "")
                    .replace("_", " ");
            }
        } catch (Exception ignored) {}

        return new GuiElementBuilder(isShiny ? Items.NETHER_STAR : Items.PAPER)
            .setName(Component.literal(
                (isShiny ? "§e✨ " : "§b") + specName + " §7(Nv. §f" + level + "§7)"))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§7Naturaleza: §f" + natureName))
            .addLoreLine(Component.literal("§7Habilidad:  §f" + abilityName))
            .addLoreLine(Component.literal("§7Shiny: " + (isShiny ? "§aSí ✨" : "§cNo")))
            .addLoreLine(Component.literal("§7Ítem: §f" + heldItemName))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§d§lIVs §7(" + ivPct + "% perfectos)"))
            .addLoreLine(Component.literal(
                "§cHP:§f" + ivHp + " §6ATK:§f" + ivAtk + " §eDEF:§f" + ivDef))
            .addLoreLine(Component.literal(
                "§9SPA:§f" + ivSpa + " §aSPD:§f" + ivSpd + " §bSPE:§f" + ivSpe))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§5§lEVs §7(Total: " + totalEvs + "/510)"))
            .addLoreLine(Component.literal(
                "§cHP:§f" + evHp + " §6ATK:§f" + evAtk + " §eDEF:§f" + evDef))
            .addLoreLine(Component.literal(
                "§9SPA:§f" + evSpa + " §aSPD:§f" + evSpd + " §bSPE:§f" + evSpe))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§7Movimientos: §f" + movesStr));
    }

    private void buildFooter() {
        setSlot(27, new GuiElementBuilder(Items.CHEST)
            .setName(Component.literal("§b§lAbrir PC"))
            .addLoreLine(Component.literal(
                "§7Inspecciona las cajas del PC de §f" + target.getName().getString()))
            .addLoreLine(Component.literal("§eClick para abrir"))
            .setCallback((idx, type, action, gui) -> new CobblemonPCGui(staff, target, 0).open())
            .build());

        setSlot(35, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§cCerrar Inspector"))
            .addLoreLine(Component.literal("§7Vuelve al menú anterior"))
            .setCallback((idx, type, action, gui) -> this.close())
            .build());
    }
}
