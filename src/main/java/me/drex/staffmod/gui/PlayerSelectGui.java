package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

public class PlayerSelectGui extends SimpleGui {

    private final ServerPlayer staff;
    private final StaffAction action;
    final SimpleGui parent;

    public PlayerSelectGui(ServerPlayer staff, StaffAction action, SimpleGui parent) {
        super(resolveSize(staff, action), staff, false);
        this.staff  = staff;
        this.action = action;
        this.parent = parent;
        setTitle(Component.literal("§8» §6Selecciona jugador §7[" + action.name() + "]"));
        build();
    }

    // FIX: calcular tamaño en base a jugadores elegibles reales, no playerCount-1
    // que podía devolver 0 si solo estaba el staff conectado.
    private static MenuType<?> resolveSize(ServerPlayer staff, StaffAction action) {
        List<ServerPlayer> all = staff.getServer().getPlayerList().getPlayers();
        long eligible = all.stream()
            .filter(p -> !p.getUUID().equals(staff.getUUID()))
            .filter(p -> {
                PlayerData pd = DataStore.get(p.getUUID());
                if (action == StaffAction.UNMUTE) return pd != null && pd.isMuteActive();
                if (action == StaffAction.UNJAIL) return pd != null && pd.isJailActive();
                if (action == StaffAction.UNBAN)  return pd != null && pd.isBanActive();
                return true;
            })
            .count();
        // Mínimo 1 slot real + 1 para el botón Volver = 2 → usamos 9 (1 fila)
        int needed = (int) Math.max(1, eligible) + 1;
        if (needed <= 9)  return MenuType.GENERIC_9x1;
        if (needed <= 18) return MenuType.GENERIC_9x2;
        if (needed <= 27) return MenuType.GENERIC_9x3;
        if (needed <= 36) return MenuType.GENERIC_9x4;
        if (needed <= 45) return MenuType.GENERIC_9x5;
        return MenuType.GENERIC_9x6;
    }

    // FIX: refresh usa close+reopen (patrón sgui 1.6.x, no existe updateElements())
    private void refresh() {
        this.close();
        new PlayerSelectGui(staff, action, parent).open();
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        List<ServerPlayer> online = staff.getServer().getPlayerList().getPlayers();
        int slot = 0;

        for (ServerPlayer target : online) {
            if (slot >= getSize() - 1) break;
            if (target.getUUID().equals(staff.getUUID())) continue;

            PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());

            // Para acciones de reversión solo mostrar candidatos válidos
            if (action == StaffAction.UNMUTE && !pd.isMuteActive())  continue;
            if (action == StaffAction.UNJAIL && !pd.isJailActive())  continue;
            if (action == StaffAction.UNBAN  && !pd.isBanActive())   continue;

            boolean isProtected = PermissionUtil.isProtected(target);

            GuiElementBuilder btn = isProtected
                ? new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                    .setName(Component.literal("§c" + target.getName().getString() + " §7[Protegido]"))
                : buildTargetSlot(target, pd);

            final ServerPlayer finalTarget = target;
            btn.setCallback((idx, type, clickAction, gui) -> {
                if (isProtected) {
                    staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] No puedes actuar sobre un administrador."));
                    return;
                }
                // Shift+Click → acción rápida para acciones que la soportan
                if (clickAction == ClickType.QUICK_MOVE) {
                    switch (action) {
                        case MUTE   -> ActionExecutor.mute(staff, finalTarget, "5m", "Mute rápido");
                        case BAN    -> ActionExecutor.ban(staff, finalTarget, "1d", "Ban rápido");
                        case WARN   -> ActionExecutor.warn(staff, finalTarget, "Comportamiento inadecuado");
                        case FREEZE -> ActionExecutor.freeze(staff, finalTarget);
                        default     -> {}
                    }
                    refresh();
                    return;
                }
                handleAction(finalTarget);
            });

            setSlot(slot++, btn.build());
        }

        // Si no hay jugadores elegibles, mostrar mensaje informativo
        if (slot == 0) {
            setSlot(4, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cNo hay jugadores disponibles"))
                .addLoreLine(Component.literal(switch (action) {
                    case UNMUTE -> "§7Ningún jugador está silenciado actualmente.";
                    case UNJAIL -> "§7Ningún jugador está en la cárcel actualmente.";
                    case UNBAN  -> "§7Ningún jugador online está baneado actualmente.";
                    default     -> "§7No hay jugadores conectados para seleccionar.";
                }))
                .build());
        }

        // Botón volver siempre en el último slot
        setSlot(getSize() - 1, new GuiElementBuilder(Items.ARROW)
            .setName(Component.literal("§7◄ Volver"))
            .setCallback((i, t, a, g) -> parent.open())
            .build());
    }

    private GuiElementBuilder buildTargetSlot(ServerPlayer target, PlayerData pd) {
        boolean muted  = pd.isMuteActive();
        boolean jailed = pd.isJailActive();
        boolean frozen = pd.frozen;
        boolean banned = pd.isBanActive();

        var icon = muted  ? Items.YELLOW_STAINED_GLASS_PANE
                 : jailed ? Items.ORANGE_STAINED_GLASS_PANE
                 : frozen ? Items.LIGHT_BLUE_STAINED_GLASS_PANE
                 : banned ? Items.RED_STAINED_GLASS_PANE
                 : Items.LIME_STAINED_GLASS_PANE;

        return new GuiElementBuilder(icon)
            .setName(Component.literal("§f§l" + target.getName().getString()))
            .addLoreLine(Component.literal("§7Ping: §f" + target.connection.latency() + "ms"))
            .addLoreLine(Component.literal("§7Estado: "
                + (muted  ? "§eMuteado " : "")
                + (jailed ? "§6Jaileado " : "")
                + (frozen ? "§bCongelado " : "")
                + (banned ? "§cBaneado " : "")
                + (!muted && !jailed && !frozen && !banned ? "§aNormal" : "")))
            .addLoreLine(Component.literal(" "))
            .addLoreLine(Component.literal("§eClick: §f" + action.name()))
            .addLoreLine(Component.literal("§bShift+Click: §fAcción rápida"));
    }

    private void handleAction(ServerPlayer target) {
        switch (action) {
            case KICK      -> new KickReasonGui(staff, target, this).open();
            case FREEZE    -> { ActionExecutor.freeze(staff, target); refresh(); }
            case TELEPORT  -> ActionExecutor.teleport(staff, target);
            case KILL      -> { ActionExecutor.kill(staff, target); refresh(); }
            case MUTE      -> new DurationReasonGui(staff, target, StaffAction.MUTE, this).open();
            case UNMUTE    -> { ActionExecutor.unmute(staff, target); refresh(); }
            case JAIL      -> new JailSelectGui(staff, target, this).open();
            case UNJAIL    -> { ActionExecutor.unjail(staff, target); refresh(); }
            case BAN       -> new DurationReasonGui(staff, target, StaffAction.BAN, this).open();
            case UNBAN     -> { ActionExecutor.unban(staff, target); refresh(); }
            case WARN      -> new DurationReasonGui(staff, target, StaffAction.WARN, this).open();
            case SPY       -> ActionExecutor.spy(staff, target);
            case POKESPY   -> new CobblemonInspectorGui(staff, target).open();
        }
    }
}
