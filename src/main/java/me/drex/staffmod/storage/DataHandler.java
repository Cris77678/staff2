package me.drex.staffmod.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import me.drex.staffmod.StaffMod;
import me.drex.staffmod.core.StaffModAsync;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DataHandler {

    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private static final DateTimeFormatter BACKUP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void saveAsync(Object data, Path file) {
        StaffModAsync.runAsync(() -> {
            try {
                Files.createDirectories(file.getParent());
                if (Files.exists(file) && Files.size(file) > 0) {
                    Path backupDir = file.getParent().resolve("backups");
                    Files.createDirectories(backupDir);
                    String backupName = file.getFileName().toString().replace(".json", "")
                        + "_" + LocalDateTime.now().format(BACKUP_FORMAT) + ".json";
                    Files.copy(file, backupDir.resolve(backupName), StandardCopyOption.REPLACE_EXISTING);
                    cleanOldBackups(backupDir);
                }
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp.toFile()), "UTF-8")) {
                    GSON.toJson(data, w);
                }
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                StaffMod.LOGGER.error("[StaffMod] Error guardando {}", file.getFileName(), e);
            }
        });
    }

    public static <T> T loadSafe(Path file, Class<T> clazz) {
        if (!Files.exists(file)) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(file.toFile()), "UTF-8")) {
            return GSON.fromJson(r, clazz);
        } catch (JsonSyntaxException e) {
            handleCorrupted(file, e.getMessage());
            return null;
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error leyendo {}", file.getFileName(), e);
            return null;
        }
    }

    public static <T> T loadSafe(Path file, Type type) {
        if (!Files.exists(file)) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(file.toFile()), "UTF-8")) {
            return GSON.fromJson(r, type);
        } catch (JsonSyntaxException e) {
            handleCorrupted(file, e.getMessage());
            return null;
        } catch (Exception e) {
            StaffMod.LOGGER.error("[StaffMod] Error leyendo {}", file.getFileName(), e);
            return null;
        }
    }

    private static void handleCorrupted(Path file, String detail) {
        StaffMod.LOGGER.error("[StaffMod CRÍTICO] JSON corrupto: {} — {}", file.getFileName(), detail);
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupted"),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    private static void cleanOldBackups(Path backupDir) {
        long cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir)) {
            for (Path entry : stream) {
                if (Files.getLastModifiedTime(entry).toMillis() < cutoff)
                    Files.deleteIfExists(entry);
            }
        } catch (IOException ignored) {}
    }
}
