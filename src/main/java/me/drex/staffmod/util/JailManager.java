package me.drex.staffmod.util;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.JailZone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JailManager {

    private static final Map<UUID, BlockPos> pos1 = new HashMap<>();
    private static final Map<UUID, BlockPos> pos2 = new HashMap<>();

    public static void setPos1(UUID staffUuid, BlockPos pos) { pos1.put(staffUuid, pos); }
    public static void setPos2(UUID staffUuid, BlockPos pos) { pos2.put(staffUuid, pos); }
    public static BlockPos getPos1(UUID staffUuid) { return pos1.get(staffUuid); }
    public static BlockPos getPos2(UUID staffUuid) { return pos2.get(staffUuid); }

    public static String createJail(ServerPlayer staff, String name) {
        BlockPos p1 = pos1.get(staff.getUUID());
        BlockPos p2 = pos2.get(staff.getUUID());
        if (p1 == null || p2 == null) return "Debes seleccionar pos1 y pos2 primero.";
        if (DataStore.getJail(name) != null) return "Ya existe una cárcel con ese nombre.";
        String dim = staff.level().dimension().location().toString();
        JailZone zone = new JailZone(name, dim,
            p1.getX(), p1.getY(), p1.getZ(),
            p2.getX(), p2.getY(), p2.getZ());
        DataStore.addJail(zone);
        pos1.remove(staff.getUUID());
        pos2.remove(staff.getUUID());
        return null;
    }

    public static boolean teleportToJail(ServerPlayer player, String jailName) {
        JailZone zone = DataStore.getJail(jailName);
        if (zone == null) {
            zone = DataStore.getJails().values().stream().findFirst().orElse(null);
            if (zone == null) return false;
        }
        ResourceKey<net.minecraft.world.level.Level> dimKey = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.parse(zone.dimension));
        ServerLevel targetLevel = player.getServer().getLevel(dimKey);
        if (targetLevel == null) targetLevel = player.getServer().overworld();

        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(targetLevel, zone.spawnX, zone.spawnY, zone.spawnZ,
            player.getYRot(), player.getXRot());
        return true;
    }

    public static void checkJailBounds(ServerPlayer player) {
        var pd = DataStore.get(player.getUUID());
        if (pd == null || !pd.isJailActive()) return;
        JailZone zone = DataStore.getJail(pd.jailName);
        if (zone == null) return;

        String currentDim = player.level().dimension().location().toString();
        if (!currentDim.equals(zone.dimension)) {
            teleportToJail(player, zone.name);
            player.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] Intento de evasión bloqueado."));
            return;
        }
        Vec3 pos = player.position();
        if (!zone.contains(pos.x, pos.y, pos.z)) {
            player.teleportTo(zone.spawnX, zone.spawnY, zone.spawnZ);
            player.sendSystemMessage(Component.literal("§c[sᴛᴀꜰꜰ] No puedes salir de la cárcel."));
        }
    }
}
