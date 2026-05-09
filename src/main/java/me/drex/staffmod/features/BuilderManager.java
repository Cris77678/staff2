package me.drex.staffmod.features;

import me.drex.staffmod.StaffMod;
import me.drex.staffmod.logging.AuditLogManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BuilderManager {

    // BUG #9 FIX: savedInventories era solo en memoria → el inventario original
    // se perdía en crash/reinicio. Ahora se persiste a disco en un archivo NBT.
    private static final Map<UUID, ListTag> savedInventories = new ConcurrentHashMap<>();
    private static Path saveDir;

    public static void init(Path configDir) {
        saveDir = configDir.resolve("staffmod/builder_inventories");
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            StaffMod.LOGGER.error("[BuilderManager] No se pudo crear directorio de inventarios builder:", e);
        }
        // Cargar inventarios guardados de sesiones anteriores
        loadAll();
    }

    private static Path inventoryPath(UUID uuid) {
        return saveDir.resolve(uuid + ".nbt");
    }

    private static void loadAll() {
        if (saveDir == null || !Files.exists(saveDir)) return;
        try {
            Files.list(saveDir)
                .filter(p -> p.getFileName().toString().endsWith(".nbt"))
                .forEach(p -> {
                    try {
                        String name = p.getFileName().toString().replace(".nbt", "");
                        UUID uuid = UUID.fromString(name);
                        CompoundTag compound = net.minecraft.nbt.NbtIo.read(p);
                        if (compound != null && compound.contains("items")) {
                            savedInventories.put(uuid, compound.getList("items", 10));
                        }
                    } catch (Exception e) {
                        StaffMod.LOGGER.warn("[BuilderManager] No se pudo cargar inventario para {}: {}", p, e.getMessage());
                    }
                });
            StaffMod.LOGGER.info("[BuilderManager] {} inventarios builder cargados.", savedInventories.size());
        } catch (IOException e) {
            StaffMod.LOGGER.error("[BuilderManager] Error cargando inventarios builder:", e);
        }
    }

    private static void saveInventory(UUID uuid, ListTag items) {
        if (saveDir == null) return;
        try {
            CompoundTag compound = new CompoundTag();
            compound.put("items", items);
            Path target = inventoryPath(uuid);
            Path tmp    = target.resolveSibling(uuid + ".tmp");
            net.minecraft.nbt.NbtIo.write(compound, tmp);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            StaffMod.LOGGER.error("[BuilderManager] No se pudo guardar inventario builder para {}:", uuid, e);
        }
    }

    private static void deleteInventory(UUID uuid) {
        if (saveDir == null) return;
        try {
            Files.deleteIfExists(inventoryPath(uuid));
        } catch (IOException e) {
            StaffMod.LOGGER.warn("[BuilderManager] No se pudo eliminar inventario builder para {}: {}", uuid, e.getMessage());
        }
    }

    public static boolean isBuilderMode(UUID uuid) {
        return savedInventories.containsKey(uuid);
    }

    public static void toggleBuilderMode(ServerPlayer player) {
        UUID uuid = player.getUUID();

        if (isBuilderMode(uuid)) {
            // SALIR: restaurar inventario de supervivencia
            player.getInventory().clearContent();
            ListTag saved = savedInventories.remove(uuid);
            player.getInventory().load(saved);
            deleteInventory(uuid); // limpiar del disco
            player.setGameMode(GameType.SURVIVAL);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.sendSystemMessage(Component.literal("§c[ʙᴜɪʟᴅᴇʀ] Modo constructor desactivado. Inventario restaurado."));
            AuditLogManager.log(player.getName().getString(), "BUILDER_OFF", player.getName().getString(), "Salió de Builder Mode");
        } else {
            // ENTRAR: guardar inventario en disco y cambiar a creativo
            ListTag currentInv = new ListTag();
            player.getInventory().save(currentInv);
            savedInventories.put(uuid, currentInv);
            saveInventory(uuid, currentInv); // persistir en disco
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.sendSystemMessage(Component.literal("§a[ʙᴜɪʟᴅᴇʀ] Modo constructor activado."));
            player.sendSystemMessage(Component.literal("§eAtención: §fÍtems peligrosos bloqueados (Bedrock, Command Blocks...)."));
            player.sendSystemMessage(Component.literal("§7Tu inventario está guardado de forma segura. Usa el botón de nuevo para salir."));
            AuditLogManager.log(player.getName().getString(), "BUILDER_ON", player.getName().getString(), "Entró en Builder Mode");
        }
    }

    /** Llamado en JOIN: avisar al staff si sigue en Builder Mode desde sesión anterior. */
    public static void applyBuilderOnJoin(ServerPlayer player) {
        if (!isBuilderMode(player.getUUID())) return;
        // Ya está en builder mode desde antes del reinicio — mantener creativo y avisar
        player.setGameMode(GameType.CREATIVE);
        player.sendSystemMessage(Component.literal("§e[ʙᴜɪʟᴅᴇʀ] §fSigues en Modo Constructor de tu sesión anterior."));
        player.sendSystemMessage(Component.literal("§7Usa el menú staff para salir y recuperar tu inventario."));
    }
}
