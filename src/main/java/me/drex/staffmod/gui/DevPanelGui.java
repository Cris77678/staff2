package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.RankManager;
import me.drex.staffmod.features.KitManager;
import me.drex.staffmod.logging.AuditLogManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class DevPanelGui extends SimpleGui {

    private final ServerPlayer player;

    public DevPanelGui(ServerPlayer player) {
        super(MenuType.GENERIC_9x3, player, false);
        this.player = player;
        setTitle(Component.literal("§8❖ §d§lᴘᴀɴᴇʟ ᴅᴇ ᴅᴇsᴀʀʀᴏʟʟᴀᴅᴏʀ §8❖"));
        build();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) {
            setSlot(i, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        // Recargar módulos
        setSlot(10, new GuiElementBuilder(Items.REPEATING_COMMAND_BLOCK)
            .setName(Component.literal("§a§lRecargar Módulos"))
            .addLoreLine(Component.literal("§7Recarga Rangos y Kits sin reiniciar."))
            .setCallback((idx, type, action, gui) -> {
                RankManager.loadRanks();
                KitManager.load();
                player.sendSystemMessage(Component.literal("§a[Dev] Módulos recargados."));
                this.close();
            }).build());

        // Monitor JVM
        long maxMem   = Runtime.getRuntime().maxMemory()   / 1024 / 1024;
        long totalMem = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        long freeMem  = Runtime.getRuntime().freeMemory()  / 1024 / 1024;
        long usedMem  = totalMem - freeMem;

        setSlot(13, new GuiElementBuilder(Items.COMPARATOR)
            .setName(Component.literal("§e§lEstado de la JVM"))
            .addLoreLine(Component.literal("§7RAM: §f" + usedMem + "MB / " + maxMem + "MB"))
            .addLoreLine(Component.literal("§7Hilos activos: §f" + Thread.activeCount()))
            .build());

        // Exportar logs
        setSlot(16, new GuiElementBuilder(Items.HOPPER)
            .setName(Component.literal("§b§lExportar Logs a CSV"))
            .addLoreLine(Component.literal("§7Genera un archivo .csv en staffmod_exports/"))
            .setCallback((idx, type, action, gui) -> {
                String name = "audit_" + System.currentTimeMillis();
                AuditLogManager.exportToCSV(name);
                player.sendSystemMessage(Component.literal(
                    "§a[Dev] Logs exportados a: §fstaffmod_exports/" + name + ".csv"));
                this.close();
            }).build());

        setSlot(26, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§cCerrar"))
            .setCallback((i, t, a, g) -> this.close())
            .build());
    }
}
