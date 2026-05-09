package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.features.BuilderManager;
import me.drex.staffmod.features.VanishManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class StaffMainGui extends SimpleGui {

    private final ServerPlayer staff;

    public StaffMainGui(ServerPlayer staff) {
        super(MenuType.GENERIC_9x6, staff, false);
        this.staff = staff;
        setTitle(Component.literal("§8❖ §6§lᴅᴀsʜʙᴏᴀʀᴅ sᴛᴀꜰꜰ §8❖"));
        build();
    }

    // FIX: El método refresh cierra y reabre la GUI para enviar el estado
    // actualizado al cliente. Es el patrón estándar de sgui 1.6.x ya que
    // updateElements() no existe en esta versión.
    private void refresh() {
        this.close();
        new StaffMainGui(staff).open();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        int onlinePlayers = staff.getServer().getPlayerCount();
        long openTickets  = DataStore.getAllTickets().stream()
            .filter(t -> "ABIERTO".equals(t.status)).count();

        long[] tickTimes = staff.getServer().getTickTimesNanos();
        double avgMs = 0;
        for (long t : tickTimes) avgMs += t;
        avgMs = (avgMs / tickTimes.length) * 1.0E-6D;
        double tps = Math.min(20.0, 1000.0 / Math.max(avgMs, 1.0));
        String tpsColor = tps > 19 ? "§a" : tps > 15 ? "§e" : "§c";

        setSlot(10, new GuiElementBuilder(Items.PLAYER_HEAD)
            .setName(Component.literal("§b§lJugadores Online"))
            .addLoreLine(Component.literal("§7Conectados: §f" + onlinePlayers))
            .build());

        setSlot(13, new GuiElementBuilder(Items.CLOCK)
            .setName(Component.literal("§a§lRendimiento (ᴛᴘs)"))
            .addLoreLine(Component.literal("§7TPS: " + tpsColor + String.format("%.2f", tps)))
            .addLoreLine(Component.literal("§7MSPT: §f" + String.format("%.2f", avgMs) + "ms"))
            .build());

        var ticketItem = openTickets > 0 ? Items.ENCHANTED_BOOK : Items.BOOK;
        String ticketName = openTickets > 0
            ? "§e§lTickets §6(§f" + openTickets + " pendientes§6)"
            : "§aGestión de Tickets";

        setSlot(16, new GuiElementBuilder(ticketItem)
            .setName(Component.literal(ticketName))
            .addLoreLine(Component.literal(openTickets > 0 ? "§7¡Hay tickets esperando atención!" : "§7No hay tickets pendientes."))
            .addLoreLine(Component.literal("§eClick para gestionar"))
            .setCallback((idx, type, action, gui) -> new TicketGui(staff, this).open())
            .build());

        // ── Fila 3: Herramientas de sanción ──────────────────────────────
        addTool(27, "staffmod.kick",    Items.IRON_BOOTS,     "§c§lᴇxᴘᴜʟsᴀʀ",       "Kickear a un jugador",          StaffAction.KICK);
        addTool(28, "staffmod.mute",    Items.STRING,          "§e§lsɪʟᴇɴᴄɪᴀʀ",      "Silencio temporal o permanente",StaffAction.MUTE);
        addTool(29, "staffmod.mute",    Items.FEATHER,         "§a§lᴅᴇs-sɪʟᴇɴᴄɪᴀʀ",  "Quitar silencio",              StaffAction.UNMUTE);
        addTool(30, "staffmod.jail",    Items.IRON_BARS,       "§6§lᴄáʀᴄᴇʟ",         "Enviar a prisión",              StaffAction.JAIL);
        addTool(31, "staffmod.jail",    Items.OAK_DOOR,        "§a§lʟɪʙᴇʀᴀʀ",        "Liberar de la cárcel",          StaffAction.UNJAIL);
        addTool(32, "staffmod.ban",     Items.TNT,             "§4§lʙᴀɴᴇᴀʀ",          "Bloquear acceso",               StaffAction.BAN);
        addTool(33, "staffmod.ban",     Items.TNT_MINECART,    "§a§lᴅᴇs-ʙᴀɴᴇᴀʀ",     "Levantar ban",                  StaffAction.UNBAN);
        addTool(34, "staffmod.warn",    Items.OAK_SIGN,        "§a§lᴀᴅᴠᴇʀᴛᴇɴᴄɪᴀ",   "Advertencia oficial",           StaffAction.WARN);
        addTool(35, "staffmod.freeze",  Items.PACKED_ICE,      "§b§lᴄᴏɴɢᴇʟᴀʀ",       "Inmovilizar para revisión",     StaffAction.FREEZE);

        // ── Fila 4: Inspección y gestión ─────────────────────────────────
        addTool(36, "staffmod.spy",     Items.ENDER_EYE,       "§d§lɪɴᴠsᴘʏ",         "Revisar inventario",            StaffAction.SPY);
        addTool(37, "staffmod.teleport",Items.ENDER_PEARL,     "§3§lᴛᴇʟᴇᴘᴏʀᴛ",       "Teletransportar al jugador",    StaffAction.TELEPORT);
        addTool(38, "staffmod.kill",    Items.SKELETON_SKULL,  "§c§lᴋɪʟʟ",           "Matar a un jugador",            StaffAction.KILL);
        addTool(39, "staffmod.pokespy", Items.DRAGON_EGG,      "§d§lᴘᴏᴋᴇsᴘʏ",        "Inspeccionar equipo Pokémon",   StaffAction.POKESPY);

        // ── Fila 5: Toggles personales ────────────────────────────────────
        // FIX: Los callbacks usan refresh() (close + reopen) en lugar de
        // updateElements() que no existe en sgui 1.6.x.

        boolean scToggled = DataStore.isStaffChatToggled(staff.getUUID());
        setSlot(40, new GuiElementBuilder(scToggled ? Items.YELLOW_DYE : Items.LIGHT_GRAY_DYE)
            .setName(Component.literal(scToggled ? "§e§lsᴛᴀꜰꜰ ᴄʜᴀᴛ: FIJO" : "§7§lsᴛᴀꜰꜰ ᴄʜᴀᴛ: NORMAL"))
            .addLoreLine(Component.literal("§7Click para alternar el canal de staff."))
            .addLoreLine(Component.literal(scToggled ? "§eTus mensajes van al staff chat." : "§7Hablas en el chat global."))
            .setCallback((idx, type, action, gui) -> {
                // FIX: Solo ejecutar si es un clic primario (evita el "doble clic" fantasma de la API)
                if (type.isLeft || type.isRight) {
                    if (!PermissionUtil.has(staff, "staffmod.use")) {
                        staff.sendSystemMessage(Component.literal("§cNo tienes permiso para el staff chat."));
                        return;
                    }
                    DataStore.toggleStaffChat(staff.getUUID());
                    this.close();
                    new StaffMainGui(staff).open();
                }
            }).build());

        boolean isDuty = DataStore.isOnDuty(staff.getUUID());
        setSlot(41, new GuiElementBuilder(isDuty ? Items.LIME_DYE : Items.GRAY_DYE)
            .setName(Component.literal(isDuty ? "§a§lᴛᴜʀɴᴏ: ACTIVO" : "§7§lᴛᴜʀɴᴏ: INACTIVO"))
            .addLoreLine(Component.literal("§7Desactívalo para no recibir notificaciones."))
            .setCallback((idx, type, action, gui) -> {
                DataStore.toggleDuty(staff.getUUID());
                refresh();
            }).build());

        boolean isVanished = VanishManager.isVanished(staff.getUUID());
        setSlot(42, new GuiElementBuilder(isVanished ? Items.PHANTOM_MEMBRANE : Items.GLASS)
            .setName(Component.literal(isVanished ? "§a§lᴠᴀɴɪsʜ: ACTIVO" : "§7§lᴠᴀɴɪsʜ: INACTIVO"))
            .addLoreLine(Component.literal("§7Ocultarte del radar y tabulador."))
            .setCallback((idx, type, action, gui) -> {
                if (PermissionUtil.has(staff, "staffmod.vanish")) {
                    VanishManager.toggleVanish(staff);
                    refresh();
                } else {
                    staff.sendSystemMessage(Component.literal("§cNo tienes permiso para usar Vanish."));
                }
            }).build());

        boolean isBuilder = BuilderManager.isBuilderMode(staff.getUUID());
        setSlot(43, new GuiElementBuilder(isBuilder ? Items.BRICKS : Items.BRICK)
            .setName(Component.literal(isBuilder ? "§a§lʙᴜɪʟᴅᴇʀ: ACTIVO" : "§7§lʙᴜɪʟᴅᴇʀ: INACTIVO"))
            .addLoreLine(Component.literal("§7Modo constructor con ítems bloqueados."))
            .setCallback((idx, type, action, gui) -> {
                if (PermissionUtil.has(staff, "staffmod.builder")) {
                    // Cerrar la GUI ANTES de togglear builder: toggleBuilderMode()
                    // modifica el inventario del jugador y hacerlo con la GUI
                    // abierta causa desyncs en el cliente.
                    this.close();
                    BuilderManager.toggleBuilderMode(staff);
                } else {
                    staff.sendSystemMessage(Component.literal("§cNo tienes permiso para Builder Mode."));
                }
            }).build());

        setSlot(44, new GuiElementBuilder(Items.CHEST)
            .setName(Component.literal("§e§lᴋɪᴛs ᴅᴇ sᴛᴀꜰꜰ"))
            .addLoreLine(Component.literal("§7Reclama tus kits según tu rango."))
            .setCallback((idx, type, action, gui) -> new KitListGui(staff, this).open())
            .build());

        setSlot(50, new GuiElementBuilder(Items.WRITTEN_BOOK)
            .setName(Component.literal("§6§lAᴜᴅɪᴛᴏʀíᴀ sᴛᴀꜰꜰ"))
            .addLoreLine(Component.literal("§7Historial de acciones del equipo."))
            .setCallback((idx, type, action, gui) -> new StaffStatsGui(staff, this).open())
            .build());

        if (PermissionUtil.has(staff, "staffmod.developer")) {
            setSlot(53, new GuiElementBuilder(Items.COMMAND_BLOCK)
                .setName(Component.literal("§d§lPᴀɴᴇʟ ᴅᴇ ᴅᴇsᴀʀʀᴏʟʟᴀᴅᴏʀ"))
                .addLoreLine(Component.literal("§7Herramientas técnicas avanzadas."))
                .setCallback((idx, type, action, gui) -> new DevPanelGui(staff).open())
                .build());
        }
    }

    private void addTool(int slot, String permission, net.minecraft.world.item.Item icon,
                          String name, String lore, StaffAction action) {
        boolean hasPermission = PermissionUtil.has(staff, permission);
        GuiElementBuilder btn = new GuiElementBuilder(hasPermission ? icon : Items.RED_STAINED_GLASS_PANE)
            .setName(Component.literal(hasPermission ? name : "§c§l" + name.replaceAll("§[a-f0-9]", "") + " §7(Sin permiso)"))
            .addLoreLine(Component.literal("§7" + lore));

        if (hasPermission) {
            btn.setCallback((idx, type, a, gui) ->
                new PlayerSelectGui(staff, action, this).open());
        }
        setSlot(slot, btn.build());
    }
}
