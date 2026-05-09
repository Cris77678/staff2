package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.JailZone;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class DurationSelectForJailGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final JailZone zone;
    private final SimpleGui parent;
    private String selectedDuration = "perm";

    private static final String[] DURATIONS = {"5m","30m","1h","6h","12h","1d","7d","perm"};

    public DurationSelectForJailGui(ServerPlayer staff, ServerPlayer target,
                                     JailZone zone, SimpleGui parent) {
        super(MenuType.GENERIC_9x2, staff, false);
        this.staff  = staff;
        this.target = target;
        this.zone   = zone;
        this.parent = parent;
        setTitle(Component.literal("§8» §6Duración en §f" + zone.name));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        for (int i = 0; i < DURATIONS.length; i++) {
            final String dur = DURATIONS[i];
            boolean sel = dur.equals(selectedDuration);
            setSlot(i, new GuiElementBuilder(sel ? Items.LIME_CONCRETE : Items.GRAY_CONCRETE)
                .setName(Component.literal(sel ? "§a§l" + dur : "§7" + dur))
                .setCallback((idx, type, a, gui) -> { selectedDuration = dur; build(); })
                .build());
        }

        setSlot(13, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§eResumen"))
            .addLoreLine(Component.literal("§7Cárcel: §f" + zone.name))
            .addLoreLine(Component.literal("§7Jugador: §f" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Duración: §a" + selectedDuration))
            .build());

        setSlot(15, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§cCancelar"))
            .setCallback((i, t, a, g) -> parent.open())
            .build());

        setSlot(17, new GuiElementBuilder(Items.EMERALD_BLOCK)
            .setName(Component.literal("§a§lConfirmar Jail"))
            .setCallback((i, t, a, g) -> {
                ActionExecutor.jail(staff, target, zone.name, selectedDuration);
                parent.open();
            }).build());
    }
}
