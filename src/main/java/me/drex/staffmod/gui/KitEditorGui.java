package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.Kit;
import me.drex.staffmod.features.KitManager;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class KitEditorGui extends SimpleGui {

    private final ServerPlayer staff;
    private final Kit kit;
    private final SimpleGui parent;
    private long currentCooldown;

    public KitEditorGui(ServerPlayer staff, Kit kit, SimpleGui parent) {
        super(MenuType.GENERIC_9x6, staff, false);
        this.staff           = staff;
        this.kit             = kit;
        this.parent          = parent;
        this.currentCooldown = kit.cooldownSeconds;
        setTitle(Component.literal("§8❖ §eEditando Kit: §f" + kit.displayName));
        // Permite al staff arrastrar items reales a los 36 slots superiores
        setLockPlayerInventory(false);
        loadExistingItems();
        build();
    }

    private void loadExistingItems() {
        if (kit.base64Inventory == null || kit.base64Inventory.isEmpty()) return;
        NonNullList<ItemStack> items = KitManager.deserializeItems(
            kit.base64Inventory, 36, staff.serverLevel().registryAccess());
        for (int i = 0; i < 36 && i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                setSlot(i, items.get(i));
            }
        }
    }

    private void build() {
        // Fila 5: separador visual (slots 36-44)
        for (int i = 36; i < 45; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        // Botón: Guardar
        setSlot(40, new GuiElementBuilder(Items.EMERALD_BLOCK)
            .setName(Component.literal("§a§lGUARDAR KIT"))
            .addLoreLine(Component.literal("§7Guarda los ítems colocados en los 36 slots superiores."))
            .addLoreLine(Component.literal("§eClick para guardar"))
            .setCallback((idx, type, action, gui) -> saveKitAndClose())
            .build());

        // Cooldown -1h
        setSlot(38, new GuiElementBuilder(Items.REDSTONE)
            .setName(Component.literal("§c-1 Hora de Cooldown"))
            .setCallback((idx, type, action, gui) -> {
                currentCooldown = Math.max(0, currentCooldown - 3600);
                updateCooldownDisplay();
            }).build());

        // Cooldown +1h
        setSlot(42, new GuiElementBuilder(Items.GLOWSTONE_DUST)
            .setName(Component.literal("§a+1 Hora de Cooldown"))
            .setCallback((idx, type, action, gui) -> {
                currentCooldown += 3600;
                updateCooldownDisplay();
            }).build());

        updateCooldownDisplay();

        // Botón: Cancelar
        setSlot(36, new GuiElementBuilder(Items.BARRIER)
            .setName(Component.literal("§cCancelar y Volver"))
            .setCallback((idx, type, action, gui) -> {
                this.close();
                if (parent != null) parent.open();
            }).build());
    }

    private void updateCooldownDisplay() {
        long hours = currentCooldown / 3600;
        long mins  = (currentCooldown % 3600) / 60;
        setSlot(39, new GuiElementBuilder(Items.CLOCK)
            .setName(Component.literal("§eCooldown Actual"))
            .addLoreLine(Component.literal("§f" + hours + "h " + mins + "m §7(" + currentCooldown + "s)"))
            .build());
    }

    private void saveKitAndClose() {
        // BUG #10 FIX:
        // ANTES: this.getSlot(i).getItemStack() devuelve el item del GuiElementBuilder
        //        asignado por código, NO el item real que el staff arrastró.
        // FIX:   Leer los items directamente desde staff.containerMenu.slots,
        //        que refleja el estado real del contenedor tras la interacción del jugador.
        //
        // En un GENERIC_9x6 (54 slots) con setLockPlayerInventory(false):
        //   - containerMenu.slots 0–53 = slots del GUI (0-35 son los editables por el staff,
        //     36-44 el separador, 45-53 fila inferior con botones)
        //   - Los items arrastrados por el staff al GUI van a containerMenu.slots[i]
        //
        NonNullList<ItemStack> newItems = NonNullList.withSize(36, ItemStack.EMPTY);

        try {
            var slots = staff.containerMenu.slots;
            for (int i = 0; i < 36 && i < slots.size(); i++) {
                ItemStack realItem = slots.get(i).getItem();
                // Excluir el fondo de vidrio negro que pusimos como separador (por si acaso)
                if (realItem != null && !realItem.isEmpty()
                        && realItem.getItem() != Items.BLACK_STAINED_GLASS_PANE) {
                    newItems.set(i, realItem.copy());
                }
            }
        } catch (Exception e) {
            // Fallback: si containerMenu no es accesible por alguna razón,
            // usar getSlot() como antes (puede perder items arrastrados)
            for (int i = 0; i < 36; i++) {
                var slotEl = this.getSlot(i);
                if (slotEl != null) {
                    ItemStack stack = slotEl.getItemStack();
                    if (stack != null && !stack.isEmpty()) {
                        newItems.set(i, stack.copy());
                    }
                }
            }
        }

        int itemCount = (int) newItems.stream().filter(s -> !s.isEmpty()).count();

        kit.base64Inventory = KitManager.serializeItems(newItems, staff.serverLevel().registryAccess());
        kit.cooldownSeconds = currentCooldown;
        KitManager.createOrUpdateKit(kit);

        staff.sendSystemMessage(Component.literal(
            "§a[ᴋɪᴛs] Kit §f" + kit.displayName + " §aguardado correctamente. §7(" + itemCount + " ítems)"));
        this.close();
        if (parent != null) parent.open();
    }
}
