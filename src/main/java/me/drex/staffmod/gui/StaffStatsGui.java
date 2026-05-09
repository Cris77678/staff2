package me.drex.staffmod.gui;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.StaffProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

// BUG #6 FIX: StaffStatsGui ahora soporta paginación.
// Antes: el bucle llenaba hasta slot 52 y el botón volver estaba en slot 53.
// Con 53+ perfiles, los últimos se perdían silenciosamente.
// Ahora: paginación de 52 perfiles por página con botones de nav en la fila inferior.
public class StaffStatsGui extends SimpleGui {

    private final SimpleGui parent;
    private final ServerPlayer staff;
    private int currentPage;
    private static final int PAGE_SIZE = 52; // slots 0-51 para perfiles, 52-53 para nav

    public StaffStatsGui(ServerPlayer staff, SimpleGui parent, int page) {
        super(MenuType.GENERIC_9x6, staff, false);
        this.parent      = parent;
        this.staff       = staff;
        this.currentPage = page;
        setTitle(Component.literal("§8» §6Aᴜᴅɪᴛᴏʀíᴀ ᴅᴇ sᴛᴀꜰꜰ"));
        build();
    }

    // Constructor de compatibilidad sin número de página
    public StaffStatsGui(ServerPlayer staff, SimpleGui parent) {
        this(staff, parent, 0);
    }

    private void build() {
        for (int i = 0; i < getSize(); i++) clearSlot(i);

        List<StaffProfile> profiles = new ArrayList<>(DataStore.allStaffProfiles());
        int totalPages = Math.max(1, (int) Math.ceil((double) profiles.size() / PAGE_SIZE));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        int start = currentPage * PAGE_SIZE;
        int end   = Math.min(start + PAGE_SIZE, profiles.size());

        for (int i = start; i < end; i++) {
            StaffProfile sp = profiles.get(i);
            int slot = i - start;

            GuiElementBuilder builder = new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Component.literal("§b§l" + sp.name))
                .addLoreLine(Component.literal("§7Bans:  §c" + sp.bans))
                .addLoreLine(Component.literal("§7Mutes: §e" + sp.mutes))
                .addLoreLine(Component.literal("§7Warns: §a" + sp.warns))
                .addLoreLine(Component.literal("§7Jails: §6" + sp.jails))
                .addLoreLine(Component.literal("§7Kicks: §f" + sp.kicks))
                .addLoreLine(Component.literal(" "))
                .addLoreLine(Component.literal("§e§nÚltimas acciones:"));

            if (sp.recentHistory.isEmpty()) {
                builder.addLoreLine(Component.literal("§7(Sin acciones recientes)"));
            } else {
                for (String action : sp.recentHistory) {
                    builder.addLoreLine(Component.literal(action));
                }
            }
            setSlot(slot, builder.build());
        }

        // Fila inferior: navegación
        for (int i = 45; i < 54; i++) {
            setSlot(i, new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE)
                .setName(Component.literal(" ")).build());
        }

        // Página anterior
        if (currentPage > 0) {
            final int prev = currentPage - 1;
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§e◄ Anterior"))
                .setCallback((i, t, a, g) -> new StaffStatsGui(staff, parent, prev).open())
                .build());
        }

        // Indicador de página
        setSlot(49, new GuiElementBuilder(Items.PAPER)
            .setName(Component.literal("§aPágina §f" + (currentPage + 1) + " §ade §f" + totalPages))
            .addLoreLine(Component.literal("§7Total staff: §f" + profiles.size()))
            .build());

        // Página siguiente
        if (currentPage < totalPages - 1) {
            final int next = currentPage + 1;
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("§eSiguiente ►"))
                .setCallback((i, t, a, g) -> new StaffStatsGui(staff, parent, next).open())
                .build());
        }

        // Volver
        setSlot(47, new GuiElementBuilder(Items.DARK_OAK_DOOR)
            .setName(Component.literal("§c◄ Volver"))
            .setCallback((i, t, a, g) -> parent.open())
            .build());
    }
}
