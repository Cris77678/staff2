package me.drex.staffmod.gui;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public class CobblemonPCGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final boolean canEdit;
    private int currentBox;

    // Cobblemon 1.7.3: cada caja tiene 30 slots (5 filas x 6 columnas)
    private static final int SLOTS_PER_BOX = 30;

    public CobblemonPCGui(ServerPlayer staff, ServerPlayer target, int startBox) {
        super(MenuType.GENERIC_9x6, staff, false);
        this.staff      = staff;
        this.target     = target;
        this.currentBox = startBox;
        this.canEdit    = PermissionUtil.has(staff, "staffmod.pokemon.pc.edit");
        AuditLogManager.log(
            staff.getName().getString(), staff.getUUID().toString(),
            "PC_INSPECT", target.getName().getString(), target.getUUID().toString(),
            "Caja " + (currentBox + 1));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        // API REAL Cobblemon 1.7.3: getPC(UUID, RegistryAccess)
        ServerLevel serverLevel = target.serverLevel();
        PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(target.getUUID(), serverLevel.registryAccess());

        if (pc == null) {
            setTitle(Component.literal("§cError: PC no disponible"));
            setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cPC no inicializado"))
                .addLoreLine(Component.literal("§7El jugador puede no tener PC generado."))
                .build());
            return;
        }

        // API REAL: pc.getBoxes() devuelve List<PCBox>
        List<PCBox> boxes = pc.getBoxes();
        int totalBoxes = boxes.size();

        if (totalBoxes == 0) {
            setTitle(Component.literal("§cPC vacío o inaccesible"));
            return;
        }

        currentBox = Math.max(0, Math.min(currentBox, totalBoxes - 1));
        setTitle(Component.literal(
            "§8✦ §bPC de §f" + target.getName().getString()
            + " §8— §7Caja §f" + (currentBox + 1) + "§7/§f" + totalBoxes
            + (canEdit ? " §a(Editor)" : " §7(Lectura)")));

        // API REAL: boxes.get(int) para obtener la caja actual
        PCBox box = boxes.get(currentBox);

        for (int i = 0; i < SLOTS_PER_BOX; i++) {
            Pokemon pokemon = null;
            try {
                // API REAL Cobblemon 1.7.3: box.get(int) — puede lanzar excepción en slot vacío
                pokemon = box.get(i);
            } catch (Exception ignored) {}

            if (pokemon == null) {
                setSlot(i, new GuiElementBuilder(Items.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .setName(Component.literal("§8Slot vacío")).build());
                continue;
            }

            setSlot(i, buildPokemonSlot(pokemon, i));
        }

        // Separador visual slots 30-44
        for (int i = SLOTS_PER_BOX; i < 45; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLUE_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        buildNavBar(totalBoxes);
    }

    private GuiElementBuilder buildPokemonSlot(Pokemon pokemon, int slotIndex) {
        boolean isShiny   = pokemon.getShiny();
        String specName   = pokemon.getSpecies().getName();
        int level         = pokemon.getLevel();
        String natureName = "?";
        try { natureName = pokemon.getNature().getName().getPath(); } catch (Exception ignored) {}

        // IVs: cast unchecked — funciona en runtime porque IVs extiende HashMap<Stat, Integer>
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

        // Habilidad
        String abilityName = "?";
        try { abilityName = pokemon.getAbility().getName(); } catch (Exception ignored) {}

        // Ítem sostenido — API REAL Cobblemon 1.7.3: pokemon.heldItem() (propiedad Kotlin)
        String heldItemName = "Ninguno";
        try {
            var held = pokemon.heldItem();
            if (held != null && !held.isEmpty()) {
                heldItemName = held.getItem().toString().replace("minecraft:", "").replace("_", " ");
            }
        } catch (Exception ignored) {}

        // Movimientos — iterar MoveSet directamente
        StringBuilder movesStr = new StringBuilder();
        try {
            for (var move : pokemon.getMoveSet()) {
                if (move == null) continue;
                if (movesStr.length() > 0) movesStr.append(", ");
                movesStr.append(move.getName());
            }
        } catch (Exception ignored) {}
        if (movesStr.length() == 0) movesStr.append("Sin movimientos");

        return new GuiElementBuilder(isShiny ? Items.NETHER_STAR : Items.EGG)
            .setName(Component.literal(
                (isShiny ? "§e✨ " : "§b") + specName + " §7Nv.§f" + level))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§7Naturaleza: §f" + natureName))
            .addLoreLine(Component.literal("§7Habilidad:  §f" + abilityName))
            .addLoreLine(Component.literal("§7Ítem: §f" + heldItemName))
            .addLoreLine(Component.literal("§7Shiny: " + (isShiny ? "§aSí ✨" : "§cNo")))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§d§lIVs §7(" + ivPct + "% perfectos)"))
            .addLoreLine(Component.literal(
                "§cHP:§f" + ivHp + " §6ATK:§f" + ivAtk + " §eDEF:§f" + ivDef))
            .addLoreLine(Component.literal(
                "§9SPA:§f" + ivSpa + " §aSPD:§f" + ivSpd + " §bSPE:§f" + ivSpe))
            .addLoreLine(Component.literal("§8──────────────────────"))
            .addLoreLine(Component.literal("§7Movs: §f" + movesStr))
            .addLoreLine(Component.literal("§8Slot PC: " + slotIndex));
    }

    private void buildNavBar(int totalBoxes) {
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }
        if (currentBox > 0) {
            final int prev = currentBox - 1;
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§e◄ Caja " + currentBox))
                .setCallback((i, t, a, g) -> new CobblemonPCGui(staff, target, prev).open()).build());
        }
        setSlot(49, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§7Caja §f" + (currentBox + 1) + " §7de §f" + totalBoxes))
            .addLoreLine(Component.literal("§7Jugador: §f" + target.getName().getString()))
            .addLoreLine(Component.literal(canEdit ? "§aModo: Editor" : "§7Modo: Solo lectura"))
            .build());
        if (currentBox < totalBoxes - 1) {
            final int next = currentBox + 1;
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§eCaja " + (currentBox + 2) + " ►"))
                .setCallback((i, t, a, g) -> new CobblemonPCGui(staff, target, next).open()).build());
        }
        setSlot(47, new GuiElementBuilder(Items.DARK_OAK_DOOR)
            .setName(Component.literal("§c◄ Volver a Party"))
            .setCallback((i, t, a, g) -> new CobblemonInspectorGui(staff, target).open()).build());
    }
}
