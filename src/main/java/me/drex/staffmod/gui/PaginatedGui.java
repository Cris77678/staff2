package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Framework abstracto de paginación para GUIs de 6 filas.
 * Filas 1-5 (slots 0-44) → datos paginados.
 * Fila 6 (slots 45-53)   → barra de navegación.
 */
public abstract class PaginatedGui<T> extends SimpleGui {

    protected final SimpleGui parent;
    protected List<T> data;
    protected int currentPage = 0;
    protected static final int PAGE_SIZE = 45;

    public PaginatedGui(ServerPlayer player, SimpleGui parent, Component title, List<T> data) {
        super(MenuType.GENERIC_9x6, player, false);
        this.parent = parent;
        this.data = data;
        setTitle(title);
    }

    protected void build() {
        // Limpiar todos los slots
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        int maxPages = Math.max(1, (int) Math.ceil((double) data.size() / PAGE_SIZE));
        currentPage = Math.max(0, Math.min(currentPage, maxPages - 1));

        int startIndex = currentPage * PAGE_SIZE;
        int endIndex   = Math.min(startIndex + PAGE_SIZE, data.size());

        for (int i = startIndex; i < endIndex; i++) {
            setSlot(i - startIndex, buildItem(data.get(i)));
        }

        buildNavigation(maxPages);
    }

    protected abstract GuiElementBuilder buildItem(T item);

    private void buildNavigation(int maxPages) {
        // Fondo fila 6
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        // Anterior
        if (currentPage > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§e◄ Página Anterior"))
                .setCallback((idx, type, action, gui) -> { currentPage--; build(); })
                .build());
        }

        // Indicador
        setSlot(49, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§aPágina §f" + (currentPage + 1) + " §ade §f" + maxPages))
            .addLoreLine(Component.literal("§7Total de registros: §f" + data.size()))
            .build());

        // Siguiente
        if (currentPage < maxPages - 1) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§eSiguiente Página ►"))
                .setCallback((idx, type, action, gui) -> { currentPage++; build(); })
                .build());
        }

        // Volver / Cerrar
        if (parent != null) {
            setSlot(47, new GuiElementBuilder(Items.DARK_OAK_DOOR)
                .setName(Component.literal("§c◄ Volver al Menú"))
                .setCallback((idx, type, action, gui) -> parent.open())
                .build());
        } else {
            setSlot(47, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("§cCerrar"))
                .setCallback((idx, type, action, gui) -> this.close())
                .build());
        }
    }
}
