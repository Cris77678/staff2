package me.drex.staffmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.drex.staffmod.StaffMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RankManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RANKS_FILE = FabricLoader.getInstance().getConfigDir().resolve("staffmod/ranks.json");
    private static List<RankConfig> loadedRanks = new ArrayList<>();

    public static void loadRanks() {
        if (!Files.exists(RANKS_FILE)) {
            generateDefaultRanks();
            return;
        }
        try {
            Type listType = new TypeToken<ArrayList<RankConfig>>() {}.getType();
            String json = Files.readString(RANKS_FILE);
            List<RankConfig> ranks = GSON.fromJson(json, listType);
            if (ranks == null || ranks.isEmpty()) {
                generateDefaultRanks();
            } else {
                loadedRanks = ranks;
                loadedRanks.sort(Comparator.comparingInt((RankConfig r) -> r.priority).reversed());
            }
            StaffMod.LOGGER.info("[StaffMod] {} rangos cargados.", loadedRanks.size());
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error cargando rangos:", e);
            generateDefaultRanks();
        }
    }

    private static void generateDefaultRanks() {
        loadedRanks.clear();
        loadedRanks.add(new RankConfig("admin", "ᴀᴅᴍɪɴɪsᴛʀᴀᴅᴏʀ", "[Admin]", "§4", 100, "staffmod.rank.admin", List.of("ALL")));
        loadedRanks.add(new RankConfig("mod", "ᴍᴏᴅᴇʀᴀᴅᴏʀ", "[Mod]", "§2", 50, "staffmod.rank.mod", List.of("TICKETS", "MUTE", "KICK", "FREEZE", "JAIL")));
        loadedRanks.add(new RankConfig("helper", "ᴀʏᴜᴅᴀɴᴛᴇ", "[Helper]", "§b", 10, "staffmod.rank.helper", List.of("TICKETS")));
        loadedRanks.add(new RankConfig("builder", "ᴄᴏɴsᴛʀᴜᴄᴛᴏʀ", "[Builder]", "§e", 5, "staffmod.rank.builder", List.of("BUILDER_MODE")));
        try {
            Files.createDirectories(RANKS_FILE.getParent());
            Files.writeString(RANKS_FILE, GSON.toJson(loadedRanks));
        } catch (IOException e) {
            StaffMod.LOGGER.error("[StaffMod] Error guardando rangos por defecto:", e);
        }
    }

    public static RankConfig getHighestRank(ServerPlayer player) {
        for (RankConfig rank : loadedRanks) {
            if (me.drex.staffmod.util.PermissionUtil.has(player, rank.requiredPermission)) {
                return rank;
            }
        }
        return null;
    }

    public static List<RankConfig> getAll() {
        return List.copyOf(loadedRanks);
    }
}
