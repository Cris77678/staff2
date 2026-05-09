package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AdvancedSpyGui extends SimpleGui {

    private final ServerPlayer staff;
    private final ServerPlayer target;
    private final boolean canInteract;

    public AdvancedSpyGui(ServerPlayer staff, ServerPlayer target) {
        super(MenuType.GENERIC_9x5, staff, false);
        this.staff = staff;
        this.target = target;
        this.canInteract = PermissionUtil.has(staff, "staffmod.spy.interact");
        setTitle(Component.literal("§d[ɪɴᴠsᴘʏ] §f" + target.getName().getString()
            + (canInteract ? " §a(Editor)" : " §7(Solo lectura)")));
        build();
    }

    private void build() {
        var inv = target.getInventory();

        // Slots 0–35 → inventario principal
        for (int i = 0; i < 36; i++) {
            final int slot = i;
            setSlot(i, buildSecureSlot(inv.getItem(i), "§8Slot: " + i,
                () -> { if (canInteract) handleSafeSwap(slot, false); }));
        }

        // Slots 36–39 → armadura
        String[] armorNames = {"Botas", "Pantalón", "Pechera", "Casco"};
        for (int i = 0; i < 4; i++) {
            final int armorSlot = i;
            setSlot(36 + i, buildSecureSlot(inv.armor.get(i), "§8Armadura: " + armorNames[i],
                () -> { if (canInteract) handleSafeSwap(armorSlot, true); }));
        }

        // Slot 40 → mano secundaria
        setSlot(40, buildSecureSlot(inv.offhand.get(0), "§8Mano Secundaria", () -> {
            if (canInteract) {
                ItemStack cursorItem = staff.containerMenu.getCarried();
                ItemStack targetItem = inv.offhand.get(0).copy();
                inv.offhand.set(0, cursorItem);
                staff.containerMenu.setCarried(targetItem);
                logAction("modificó la mano secundaria");
                reopenSafely();
            }
        }));

        // Botón cerrar
        setSlot(44, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§c§lCerrar Spy"))
            .setCallback((idx, type, action, gui) -> this.close())
            .build());
    }

    private GuiElementBuilder buildSecureSlot(ItemStack item, String info, Runnable onClick) {
        if (item.isEmpty()) item = new ItemStack(Items.AIR);
        return GuiElementBuilder.from(item.copy())
            .addLoreLine(Component.literal(info))
            .setCallback((idx, clickData, actionType, gui) -> {
                if (!canInteract) return;
                // Bloquear acciones peligrosas
                if (actionType == ClickType.QUICK_MOVE || actionType == ClickType.SWAP
                    || actionType == ClickType.CLONE || actionType == ClickType.QUICK_CRAFT
                    || actionType == ClickType.PICKUP_ALL) {
                    staff.sendSystemMessage(Component.literal(
                        "§c[sᴘʏ] Shift-Click y arrastre deshabilitados por seguridad."));
                    // Reenviar estado del slot de forma segura via SimpleGui
                    staff.containerMenu.sendAllDataToRemote();
                    return;
                }
                onClick.run();
            });
    }

    private void handleSafeSwap(int targetSlot, boolean isArmor) {
        var inv = target.getInventory();
        ItemStack cursorItem = staff.containerMenu.getCarried();
        ItemStack targetItem;

        if (isArmor) {
            targetItem = inv.armor.get(targetSlot).copy();
            inv.armor.set(targetSlot, cursorItem);
        } else {
            targetItem = inv.getItem(targetSlot).copy();
            inv.setItem(targetSlot, cursorItem);
        }
        staff.containerMenu.setCarried(targetItem);
        logAction("modificó el slot " + targetSlot + (isArmor ? " (Armadura)" : ""));
        reopenSafely();
    }

    private void reopenSafely() {
        this.close();
        new AdvancedSpyGui(staff, target).open();
    }

    private void logAction(String detail) {
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "SPY_EDIT", target.getName().getString(), target.getUUID().toString(), detail);
    }
}
