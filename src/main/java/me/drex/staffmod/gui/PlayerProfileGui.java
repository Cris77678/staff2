package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class PlayerProfileGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final PlayerData targetData;

    public PlayerProfileGui(ServerPlayer staff, ServerPlayer target, SimpleGui parent) {
        super(MenuType.GENERIC_9x3, staff, false);
        this.staff = staff;
        this.target = target;
        this.targetData = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        setTitle(Component.literal("§8❖ §3Perfil: §f" + target.getName().getString()));
        build(parent);
    }

    private void build(SimpleGui parent) {
        for (int i = 0; i < getSize(); i++) {
            setSlot(i, new GuiElementBuilder(Items.LIGHT_BLUE_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        setSlot(11, new GuiElementBuilder(Items.PLAYER_HEAD)
            .setName(Component.literal("§b§l" + target.getName().getString()))
            .addLoreLine(Component.literal("§8UUID: " + target.getUUID()))
            .addLoreLine(Component.literal(" "))
            .addLoreLine(Component.literal("§7Ping: §a" + target.connection.latency() + "ms"))
            .addLoreLine(Component.literal("§7Modo: §e" + target.gameMode.getGameModeForPlayer().getName()))
            .build());

        boolean isMuted  = targetData.isMuteActive();
        boolean isJailed = targetData.isJailActive();
        boolean isBanned = targetData.isBanActive();

        setSlot(13, new GuiElementBuilder(Items.WRITTEN_BOOK)
            .setName(Component.literal("§6§lHistorial Administrativo"))
            .addLoreLine(Component.literal("§7Warns:  §c" + targetData.warnCount()))
            .addLoreLine(Component.literal("§7Mute:   " + (isMuted ? "§cActivo" : "§aLimpio")))
            .addLoreLine(Component.literal("§7Cárcel: " + (isJailed ? "§cActivo" : "§aLibre")))
            .addLoreLine(Component.literal("§7Ban:    " + (isBanned ? "§cActivo" : "§aLimpio")))
            .build());

        // FIX: El botón "Moderación" antes solo cerraba la GUI con un mensaje vago.
        // Ahora abre el menú principal de staff para que pueda seleccionar la acción.
        setSlot(15, new GuiElementBuilder(Items.ANVIL)
            .setName(Component.literal("§c§lModeración"))
            .addLoreLine(Component.literal("§7Volver al panel de staff."))
            .setCallback((idx, type, action, gui) -> {
                new StaffMainGui(staff).open();
            }).build());

        if (parent != null) {
            setSlot(26, new GuiElementBuilder(Items.DARK_OAK_DOOR)
                .setName(Component.literal("§c◄ Volver"))
                .setCallback((i, t, a, g) -> parent.open())
                .build());
        } else {
            setSlot(26, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cCerrar"))
                .setCallback((i, t, a, g) -> this.close())
                .build());
        }
    }
}
