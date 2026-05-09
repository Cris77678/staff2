package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * BUG #2 FIX: GUI de confirmación para KICK.
 * Pide razón antes de ejecutar el kick, igual que WARN / MUTE / BAN.
 */
public class KickReasonGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final SimpleGui parent;

    private String selectedReason = "Incumplimiento de normas.";

    private static final String[] REASONS = {
        "Incumplimiento de normas.", "Lenguaje inapropiado.", "Spam.",
        "Comportamiento tóxico.", "Acoso a otros jugadores.", "Publicidad.",
        "Decisión del staff.", "Otro."
    };

    public KickReasonGui(ServerPlayer staff, ServerPlayer target, SimpleGui parent) {
        super(MenuType.GENERIC_9x3, staff, false);
        this.staff  = staff;
        this.target = target;
        this.parent = parent;
        setTitle(Component.literal("§8» §cKICK §7a §f" + target.getName().getString()));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        // Fila 1 - razones
        for (int i = 0; i < REASONS.length; i++) {
            final String reason = REASONS[i];
            boolean sel = reason.equals(selectedReason);
            setSlot(i, new GuiElementBuilder(sel ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE)
                .setName(Component.literal(sel ? "§a§l" + reason : "§7" + reason))
                .addLoreLine(Component.literal(sel ? "§aSeleccionada" : "§7Click para seleccionar"))
                .setCallback((idx, type, a, gui) -> { selectedReason = reason; build(); })
                .build());
        }

        // Fila 2 - separador y resumen
        for (int i = 9; i < 18; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }
        setSlot(13, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§e§lResumen"))
            .addLoreLine(Component.literal("§7Acción: §cKICK"))
            .addLoreLine(Component.literal("§7Jugador: §f" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Razón: §a" + selectedReason))
            .build());

        // Fila 3 - cancelar / confirmar
        setSlot(18, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§c§lCancelar"))
            .setCallback((idx, type, a, gui) -> parent.open())
            .build());

        setSlot(26, new GuiElementBuilder(Items.EMERALD_BLOCK)
            .setName(Component.literal("§a§lConfirmar KICK"))
            .addLoreLine(Component.literal("§7Kickeará a §f" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Razón: §e" + selectedReason))
            .setCallback((idx, type, a, gui) -> {
                ActionExecutor.kick(staff, target, selectedReason);
                gui.close(); // Cerrar el inventario en lugar de intentar abrir el perfil de alguien offline
            })
            .build());
    }
}
