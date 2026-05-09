package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class DurationReasonGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final StaffAction action;
    private final SimpleGui parent;

    private String selectedDuration = "perm";
    private String selectedReason   = "Infracción de normas.";

    private static final String[] DURATIONS = {"5m","30m","1h","6h","12h","1d","7d","perm"};
    private static final String[] REASONS = {
        "Infracción de normas.", "Lenguaje inapropiado.", "Spam.", "Hack / Trampa.",
        "Acoso a otros jugadores.", "Publicidad.", "Comportamiento tóxico.", "Otro."
    };

    public DurationReasonGui(ServerPlayer staff, ServerPlayer target,
                              StaffAction action, SimpleGui parent) {
        super(MenuType.GENERIC_9x3, staff, false);
        this.staff  = staff;
        this.target = target;
        this.action = action;
        this.parent = parent;
        setTitle(Component.literal("§8» §6" + action.name() + " §7a §f" + target.getName().getString()));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        // Fila 1 – duraciones (solo para MUTE y BAN)
        if (action == StaffAction.MUTE || action == StaffAction.BAN) {
            for (int i = 0; i < DURATIONS.length; i++) {
                final String dur = DURATIONS[i];
                boolean sel = dur.equals(selectedDuration);
                setSlot(i, new GuiElementBuilder(sel ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE)
                    .setName(Component.literal(sel ? "§a§l" + dur : "§7" + dur))
                    .addLoreLine(Component.literal(sel ? "§aSeleccionado" : "§7Click para seleccionar"))
                    .setCallback((idx, type, a, gui) -> { selectedDuration = dur; build(); })
                    .build());
            }
        }

        // Fila 2 – razones
        for (int i = 0; i < REASONS.length; i++) {
            final String reason = REASONS[i];
            boolean sel = reason.equals(selectedReason);
            setSlot(9 + i, new GuiElementBuilder(sel ? Items.LIME_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE)
                .setName(Component.literal(sel ? "§a§l" + reason : "§7" + reason))
                .addLoreLine(Component.literal(sel ? "§aSeleccionada" : "§7Click para seleccionar"))
                .setCallback((idx, type, a, gui) -> { selectedReason = reason; build(); })
                .build());
        }

        // Fila 3 – cancelar, resumen, confirmar
        setSlot(18, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§c§lCancelar"))
            .setCallback((idx, type, a, gui) -> parent.open())
            .build());

        setSlot(22, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§e§lResumen"))
            .addLoreLine(Component.literal("§7Acción: §f" + action.name()))
            .addLoreLine(Component.literal("§7Jugador: §f" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Duración: §a" + selectedDuration))
            .addLoreLine(Component.literal("§7Razón: §a" + selectedReason))
            .build());

        setSlot(26, new GuiElementBuilder(Items.EMERALD_BLOCK)
            .setName(Component.literal("§a§lConfirmar"))
            .addLoreLine(Component.literal("§7" + action.name() + " a §f" + target.getName().getString()))
            .setCallback((idx, type, a, gui) -> confirm())
            .build());
    }

    private void confirm() {
        switch (action) {
            case MUTE -> ActionExecutor.mute(staff, target, selectedDuration, selectedReason);
            case BAN  -> ActionExecutor.ban(staff, target, selectedDuration, selectedReason);
            case WARN -> ActionExecutor.warn(staff, target, selectedReason);
            default   -> {}
        }
        parent.open();
    }
}
