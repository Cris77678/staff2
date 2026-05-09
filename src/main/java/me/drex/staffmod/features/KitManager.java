package me.drex.staffmod.features;

import com.google.gson.reflect.TypeToken;
import me.drex.staffmod.StaffMod;
import me.drex.staffmod.config.Kit;
import me.drex.staffmod.storage.DataHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KitManager {

    private static final Path KITS_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("staffmod/kits.json");
    private static final Path COOLDOWNS_FILE =
        FabricLoader.getInstance().getConfigDir().resolve("staffmod/kit_cooldowns.json");

    private static final Map<String, Kit> loadedKits = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> playerCooldowns = new ConcurrentHashMap<>();

    public static void load() {
        Type kitMapType = new TypeToken<Map<String, Kit>>() {}.getType();
        Map<String, Kit> savedKits = DataHandler.loadSafe(KITS_FILE, kitMapType);
        if (savedKits != null) { loadedKits.clear(); loadedKits.putAll(savedKits); }

        Type cooldownType = new TypeToken<Map<UUID, Map<String, Long>>>() {}.getType();
        Map<UUID, Map<String, Long>> savedCooldowns = DataHandler.loadSafe(COOLDOWNS_FILE, cooldownType);
        if (savedCooldowns != null) { playerCooldowns.clear(); playerCooldowns.putAll(savedCooldowns); }

        StaffMod.LOGGER.info("[StaffMod] {} kits cargados.", loadedKits.size());
    }

    public static void saveAllAsync() {
        DataHandler.saveAsync(loadedKits, KITS_FILE);
        DataHandler.saveAsync(playerCooldowns, COOLDOWNS_FILE);
    }

    public static Collection<Kit> getAllKits() { return loadedKits.values(); }

    public static Kit getKit(String id) {
        return id == null ? null : loadedKits.get(id.toLowerCase());
    }

    public static void createOrUpdateKit(Kit kit) {
        loadedKits.put(kit.id.toLowerCase(), kit);
        saveAllAsync();
    }

    public static void deleteKit(String id) {
        loadedKits.remove(id.toLowerCase());
        saveAllAsync();
    }

    // ───── COOLDOWNS ─────
    public static boolean isOnCooldown(ServerPlayer player, Kit kit) {
        Map<String, Long> pcd = playerCooldowns.get(player.getUUID());
        if (pcd == null) return false;
        long expiry = pcd.getOrDefault(kit.id, 0L);
        return System.currentTimeMillis() < expiry;
    }

    public static long getRemainingCooldown(ServerPlayer player, Kit kit) {
        Map<String, Long> pcd = playerCooldowns.get(player.getUUID());
        if (pcd == null) return 0L;
        long expiry = pcd.getOrDefault(kit.id, 0L);
        return Math.max(0L, expiry - System.currentTimeMillis());
    }

    public static void setCooldown(ServerPlayer player, Kit kit) {
        if (kit.cooldownSeconds <= 0) return;
        playerCooldowns
            .computeIfAbsent(player.getUUID(), k -> new HashMap<>())
            .put(kit.id, System.currentTimeMillis() + (kit.cooldownSeconds * 1000L));
        saveAllAsync();
    }

    // ───── SERIALIZACIÓN ─────
    public static String serializeItems(NonNullList<ItemStack> items,
                                         net.minecraft.core.HolderLookup.Provider provider) {
        try {
            CompoundTag tag = new CompoundTag();
            ContainerHelper.saveAllItems(tag, items, provider);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error serializando kit:", e);
            return "";
        }
    }

    public static NonNullList<ItemStack> deserializeItems(String base64, int size,
                                                           net.minecraft.core.HolderLookup.Provider provider) {
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        if (base64 == null || base64.isEmpty()) return items;
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            CompoundTag tag = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
            ContainerHelper.loadAllItems(tag, items, provider);
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error deserializando kit:", e);
        }
        return items;
    }
}
