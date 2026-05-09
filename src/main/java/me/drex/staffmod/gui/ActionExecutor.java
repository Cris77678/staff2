package me.drex.staffmod.gui;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.config.StaffProfile;
import me.drex.staffmod.core.StaffModAsync;
import me.drex.staffmod.data.DatabaseManager;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.util.JailManager;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ActionExecutor {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    // FIX: isOffline ahora también loguea la razón específica para debugging.
    private static boolean isOffline(ServerPlayer staff, ServerPlayer target) {
        if (target == null || target.hasDisconnected()) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] El jugador se desconectó. Acción cancelada."));
            return true;
        }
        return false;
    }

    private static boolean guard(ServerPlayer staff, ServerPlayer target) {
        if (PermissionUtil.isProtected(target)) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] No puedes actuar sobre un administrador protegido."));
            return true;
        }
        return false;
    }

    private static void logHistory(ServerPlayer staff, String actionType, ServerPlayer target, String detail) {
        StaffProfile sp = DataStore.getStaffProfile(staff.getUUID(), staff.getName().getString());
        switch (actionType) {
            case "KICK" -> sp.kicks++;
            case "MUTE" -> sp.mutes++;
            case "JAIL" -> sp.jails++;
            case "BAN"  -> sp.bans++;
            case "WARN" -> sp.warns++;
        }
        String time = LocalDateTime.now().format(DTF);
        sp.addAction("§8[" + time + "] §f" + actionType + " §7a §e" + target.getName().getString() + " §8(§f" + detail + "§8)");

        final String staffUuid = staff.getUUID().toString();
        final String targetUuid = target.getUUID().toString();
        final String targetName = target.getName().getString();
        final String staffName  = staff.getName().getString();
        StaffModAsync.runAsync(() -> DatabaseManager.logAudit(staffName, staffUuid, actionType, targetName, targetUuid, detail));
        AuditLogManager.log(staffName, staffUuid, actionType, targetName, targetUuid, detail);
    }

    private static void broadcastToStaff(ServerPlayer staff, String message) {
        for (ServerPlayer p : staff.getServer().getPlayerList().getPlayers()) {
            if (PermissionUtil.has(p, "staffmod.use") && DataStore.isOnDuty(p.getUUID())) {
                p.sendSystemMessage(Component.literal(message));
            }
        }
    }

    // ───── KICK ─────
    public static void kick(ServerPlayer staff, ServerPlayer target, String reason) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        logHistory(staff, "KICK", target, reason);
        DataStore.saveAsync();
        
        // Enviar el webhook en un hilo separado para no bloquear el servidor ni interrumpir el KICK
        StaffModAsync.runAsync(() -> {
            me.drex.staffmod.util.DiscordWebhook.sendEmbed(
                "Sanción Aplicada: KICK",
                "**Staff:** " + staff.getName().getString() + "\n**Jugador:** " + target.getName().getString() + "\n**Razón:** " + reason,
                0xFFA500);
        });
    
        target.connection.disconnect(Component.literal("§cHas sido expulsado.\n§fRazón: §e" + reason));
        broadcastToStaff(staff, "§c[sᴛᴀꜰꜰ] §f" + staff.getName().getString()
            + " §ckickeó §fa §f" + target.getName().getString() + "§7. Razón: " + reason);
    }

    // ───── MUTE ─────
    public static void mute(ServerPlayer staff, ServerPlayer target, String duration, String reason) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        pd.muted      = true;
        pd.muteExpiry = PlayerData.parseDuration(duration);
        pd.muteReason = reason;
        logHistory(staff, "MUTE", target, duration + " - " + reason);
        DataStore.saveAsync();
        me.drex.staffmod.util.DiscordWebhook.sendEmbed(
            "Sanción Aplicada: MUTE",
            "**Staff:** " + staff.getName().getString() + "\n**Jugador:** " + target.getName().getString()
            + "\n**Duración:** " + duration + "\n**Razón:** " + reason,
            0xFFFF00);
        final long expiry = pd.muteExpiry;
        target.sendSystemMessage(Component.literal(
            "§c[sᴛᴀꜰꜰ] Has sido silenciado.\n§fRazón: §e" + reason
            + "\n§fDuración: §e" + PlayerData.formatExpiry(expiry)));
        broadcastToStaff(staff, "§e[sᴛᴀꜰꜰ] §f" + staff.getName().getString()
            + " §emuteó §fa §f" + target.getName().getString()
            + "§7. Duración: " + duration + " | Razón: " + reason);
    }

    // ───── UNMUTE ─────
    public static void unmute(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target)) return;
        PlayerData pd = DataStore.get(target.getUUID());
        if (pd == null || !pd.isMuteActive()) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Ese jugador no está silenciado."));
            return;
        }
        pd.muted = false; pd.muteExpiry = -1; pd.muteReason = "";
        DataStore.saveAsync();
        target.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Tu silencio ha sido removido."));
        staff.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Silencio removido a " + target.getName().getString()));
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "UNMUTE", target.getName().getString(), target.getUUID().toString(), "Manual");
    }

    // ───── JAIL ─────
    public static void jail(ServerPlayer staff, ServerPlayer target, String jailName, String duration) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        if (!JailManager.teleportToJail(target, jailName)) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Error: No se encontró la cárcel '" + jailName + "'."));
            return;
        }
        PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        pd.jailed    = true;
        pd.jailName  = jailName;
        pd.jailExpiry = PlayerData.parseDuration(duration);
        pd.pendingUnjail = false;
        logHistory(staff, "JAIL", target, jailName + " - " + duration);
        DataStore.saveAsync();
        me.drex.staffmod.util.DiscordWebhook.sendEmbed(
            "Sanción Aplicada: JAIL",
            "**Staff:** " + staff.getName().getString() + "\n**Jugador:** " + target.getName().getString()
            + "\n**Cárcel:** " + jailName + "\n**Duración:** " + duration,
            0x800080);
        target.sendSystemMessage(Component.literal(
            "§c[sᴛᴀꜰꜰ] Has sido enviado a la cárcel §f" + jailName
            + "§c.\n§fDuración: §e" + PlayerData.formatExpiry(pd.jailExpiry)));
        broadcastToStaff(staff, "§6[sᴛᴀꜰꜰ] §f" + staff.getName().getString()
            + " §6jaileó §fa §f" + target.getName().getString()
            + "§7. Cárcel: " + jailName + " | Duración: " + duration);
    }

    // ───── UNJAIL ─────
    public static void unjail(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target)) return;
        PlayerData pd = DataStore.get(target.getUUID());
        if (pd == null || !pd.isJailActive()) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Ese jugador no está en la cárcel."));
            return;
        }
        pd.jailed = false; pd.jailExpiry = -1; pd.jailName = ""; pd.pendingUnjail = false;
        DataStore.saveAsync();
        var overworld = target.getServer().overworld();
        var spawn = overworld.getSharedSpawnPos();
        target.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(),
            target.getYRot(), target.getXRot());
        target.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Has sido liberado de la cárcel."));
        staff.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] " + target.getName().getString() + " liberado."));
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "UNJAIL", target.getName().getString(), target.getUUID().toString(), "Manual");
    }

    // ───── BAN ─────
    public static void ban(ServerPlayer staff, ServerPlayer target, String duration, String reason) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        pd.banned    = true;
        pd.banExpiry = PlayerData.parseDuration(duration);
        pd.banReason = reason;
        logHistory(staff, "BAN", target, duration + " - " + reason);
        DataStore.saveAsync();
        me.drex.staffmod.util.DiscordWebhook.sendEmbed(
            "Sanción Aplicada: BAN",
            "**Staff:** " + staff.getName().getString() + "\n**Jugador:** " + target.getName().getString()
            + "\n**Duración:** " + duration + "\n**Razón:** " + reason,
            0xFF0000);
        String expStr = PlayerData.formatExpiry(pd.banExpiry);
        target.connection.disconnect(Component.literal(
            "§cHas sido baneado.\n§fRazón: §e" + reason + "\n§fExpira: §e" + expStr));
        broadcastToStaff(staff, "§4[sᴛᴀꜰꜰ] §f" + staff.getName().getString()
            + " §cbaneó §fa §f" + target.getName().getString()
            + "§7. Duración: " + duration + " | Razón: " + reason);
    }

    // ───── UNBAN (por UUID, funciona con jugadores offline) ─────
    public static void unban(ServerPlayer staff, UUID targetUuid, String targetName) {
        PlayerData pd = DataStore.get(targetUuid);
        if (pd == null || !pd.isBanActive()) {
            staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Ese jugador no está baneado."));
            return;
        }
        pd.banned = false; pd.banExpiry = -1; pd.banReason = "";
        DataStore.saveAsync();
        staff.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Ban removido a " + targetName));
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "UNBAN", targetName, targetUuid.toString(), "Manual");
    }

    // Sobrecarga para cuando el jugador SÍ está online
    public static void unban(ServerPlayer staff, ServerPlayer target) {
        unban(staff, target.getUUID(), target.getName().getString());
        // FIX: también notificar al jugador si está online
        if (!target.hasDisconnected()) {
            target.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Tu ban ha sido levantado."));
        }
    }

    // ───── FREEZE ─────
    public static void freeze(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        pd.frozen = !pd.frozen;
        DataStore.saveAsync();
        if (pd.frozen) {
            target.sendSystemMessage(Component.literal("§b[sᴛᴀꜰꜰ] Has sido congelado. No puedes moverte."));
            staff.sendSystemMessage(Component.literal("§b[sᴛᴀꜰꜰ] " + target.getName().getString() + " congelado."));
            AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
                "FREEZE", target.getName().getString(), target.getUUID().toString(), "");
        } else {
            target.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] Has sido descongelado."));
            staff.sendSystemMessage(Component.literal("§a[sᴛᴀꜰꜰ] " + target.getName().getString() + " descongelado."));
            AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
                "UNFREEZE", target.getName().getString(), target.getUUID().toString(), "");
        }
    }

    // ───── SPY ─────
    public static void spy(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target)) return;
        new AdvancedSpyGui(staff, target).open();
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "SPY", target.getName().getString(), target.getUUID().toString(), "InvSpy abierto");
    }

    // ───── WARN ─────
    public static void warn(ServerPlayer staff, ServerPlayer target, String reason) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        PlayerData pd = DataStore.getOrCreate(target.getUUID(), target.getName().getString());
        pd.warns.add(new PlayerData.WarnEntry(reason, System.currentTimeMillis(),
            staff.getName().getString(), staff.getUUID().toString()));
        logHistory(staff, "WARN", target, reason);
        DataStore.saveAsync();
        me.drex.staffmod.util.DiscordWebhook.sendEmbed(
            "Sanción Aplicada: WARN",
            "**Staff:** " + staff.getName().getString() + "\n**Jugador:** " + target.getName().getString() + "\n**Razón:** " + reason,
            0xFFD700);
        target.sendSystemMessage(Component.literal(
            "§c[sᴛᴀꜰꜰ] Has recibido una advertencia (#" + pd.warns.size() + ").\n§fRazón: §e" + reason));
        staff.sendSystemMessage(Component.literal(
            "§a[sᴛᴀꜰꜰ] Advertencia enviada (total: " + pd.warns.size() + ")"));
    }

    // ───── TELEPORT ─────
    public static void teleport(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target)) return;
        if (staff.isPassenger()) staff.stopRiding();
        staff.teleportTo(
            (net.minecraft.server.level.ServerLevel) target.level(),
            target.getX(), target.getY(), target.getZ(),
            staff.getYRot(), staff.getXRot());
        staff.sendSystemMessage(Component.literal("§3[sᴛᴀꜰꜰ] Teleportado a §f" + target.getName().getString()));
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "TELEPORT", target.getName().getString(), target.getUUID().toString(), "");
    }

    // ───── KILL ─────
    public static void kill(ServerPlayer staff, ServerPlayer target) {
        if (isOffline(staff, target) || guard(staff, target)) return;
        target.hurt(target.damageSources().genericKill(), Float.MAX_VALUE);
        staff.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Mataste a " + target.getName().getString()));
        broadcastToStaff(staff, "§c[sᴛᴀꜰꜰ] §f" + staff.getName().getString()
            + " §cmató §fa §f" + target.getName().getString());
        AuditLogManager.log(staff.getName().getString(), staff.getUUID().toString(),
            "KILL", target.getName().getString(), target.getUUID().toString(), "");
    }

    // ───── STAFF CHAT ─────
    public static void sendStaffChatMessage(ServerPlayer sender, String message) {
        String format = "§8[§cStaffChat§8] §f" + sender.getName().getString() + "§7: §e" + message;
        for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
            if (PermissionUtil.has(p, "staffmod.use")) {
                p.sendSystemMessage(Component.literal(format));
            }
        }
    }
}
