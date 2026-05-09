package me.drex.staffmod.logging;

import me.drex.staffmod.core.StaffModAsync;
import me.drex.staffmod.data.DatabaseManager;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AuditLogManager {

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Buffer en memoria para el CSV de exportación
    private static final List<AuditEntry> sessionLogs = new ArrayList<>();

    public record AuditEntry(String timestamp, String staffName, String staffUuid,
                              String action, String targetName, String targetUuid,
                              String details) {}

    /** Registra una acción de staff de forma async (DB + buffer memoria). */
    public static void log(String staffName, String staffUuid, String action,
                            String targetName, String targetUuid, String details) {
        StaffModAsync.runAsync(() -> {
            String now = LocalDateTime.now().format(TIME_FORMAT);
            AuditEntry entry = new AuditEntry(now, staffName, staffUuid, action,
                targetName, targetUuid, details);
            synchronized (sessionLogs) {
                sessionLogs.add(entry);
            }
            // Persistir en SQLite
            DatabaseManager.logAudit(staffName, staffUuid, action, targetName, targetUuid, details);
        });
    }

    /** Sobrecarga sin UUIDs para compatibilidad. */
    public static void log(String staffName, String action, String targetName, String details) {
        log(staffName, "", action, targetName, "", details);
    }

    public static void save() {
        // Los logs ya se persistieron en SQLite en tiempo real.
        // Este método flushea cualquier buffer pendiente si lo hubiera.
    }

    /** Exporta los logs de sesión a CSV. */
    public static void exportToCSV(String fileName) {
        StaffModAsync.runAsync(() -> {
            Path exportPath = FabricLoader.getInstance().getGameDir()
                .resolve("staffmod_exports/" + fileName + ".csv");
            try {
                Files.createDirectories(exportPath.getParent());
                StringBuilder csv = new StringBuilder("Fecha,Staff,StaffUUID,Accion,Objetivo,ObjetivoUUID,Detalles\n");
                synchronized (sessionLogs) {
                    for (AuditEntry e : sessionLogs) {
                        csv.append(String.format("%s,%s,%s,%s,%s,%s,%s\n",
                            e.timestamp(), e.staffName(), e.staffUuid(),
                            e.action(), e.targetName(), e.targetUuid(),
                            e.details().replace(",", ";")));
                    }
                }
                Files.writeString(exportPath, csv.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
