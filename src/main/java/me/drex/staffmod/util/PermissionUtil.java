package me.drex.staffmod.util;

import me.drex.staffmod.StaffMod;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.level.ServerPlayer;

public class PermissionUtil {

    private static LuckPerms api;

    public static void init() {
        try {
            api = LuckPermsProvider.get();
            StaffMod.LOGGER.info("[StaffMod] LuckPerms conectado correctamente.");
        } catch (IllegalStateException e) {
            StaffMod.LOGGER.error("[StaffMod CRÍTICO] LuckPerms NO encontrado. El mod requiere LuckPerms.");
            api = null;
        }
    }

    /**
     * Comprueba un permiso usando la caché ultrarrápida de LuckPerms.
     * Sin fallback inseguro: si LuckPerms no está disponible, devuelve false.
     */
    public static boolean has(ServerPlayer player, String permission) {
        if (player == null || permission == null) return false;
        if (api == null) return false;
        try {
            User user = api.getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData()
                        .checkPermission(permission).asBoolean();
            }
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error consultando LuckPerms para {}: {}",
                player.getName().getString(), e.getMessage());
        }
        return false;
    }

    /**
     * ¿El jugador tiene protección contra acciones de staff?
     */
    public static boolean isProtected(ServerPlayer target) {
        return has(target, "staffmod.protected");
    }

    public static boolean isAvailable() {
        return api != null;
    }
}
