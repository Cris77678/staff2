package me.drex.staffmod.data;

import me.drex.staffmod.StaffMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Gestor de base de datos SQLite con base preparada para MySQL.
 * Threading: todas las operaciones deben llamarse desde hilos async.
 */
public class DatabaseManager {

    private static final Path DB_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("staffmod/staffmod.db");

    private static Connection connection;

    public static void init() throws Exception {
        Files.createDirectories(DB_PATH.getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH.toAbsolutePath());
        connection.createStatement().execute("PRAGMA journal_mode=WAL;");
        connection.createStatement().execute("PRAGMA synchronous=NORMAL;");
        createTables();
        StaffMod.LOGGER.info("[StaffMod] SQLite conectado: {}", DB_PATH.getFileName());
    }

    private static void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Logs de auditoría
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    staff_name TEXT NOT NULL,
                    staff_uuid TEXT NOT NULL,
                    action TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    target_uuid TEXT,
                    details TEXT,
                    created_at INTEGER NOT NULL
                )
            """);

            // Historial de sanciones
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sanctions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    reason TEXT,
                    duration_ms INTEGER,
                    expiry_ts INTEGER,
                    staff_uuid TEXT NOT NULL,
                    staff_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    active INTEGER DEFAULT 1
                )
            """);

            // Tickets
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS tickets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    creator_uuid TEXT NOT NULL,
                    creator_name TEXT NOT NULL,
                    message TEXT NOT NULL,
                    status TEXT DEFAULT 'ABIERTO',
                    handled_by TEXT DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
            """);

            // Logs de PC Inspect (Cobblemon)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pc_inspect_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    staff_uuid TEXT NOT NULL,
                    staff_name TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    target_name TEXT NOT NULL,
                    action TEXT NOT NULL,
                    details TEXT,
                    created_at INTEGER NOT NULL
                )
            """);
        }
    }

    public static void logAudit(String staffName, String staffUuid, String action,
                                 String targetName, String targetUuid, String details) {
        if (connection == null) return;
        try {
            String now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO audit_log (timestamp,staff_name,staff_uuid,action,target_name,target_uuid,details,created_at) VALUES (?,?,?,?,?,?,?,?)");
            ps.setString(1, now);
            ps.setString(2, staffName);
            ps.setString(3, staffUuid);
            ps.setString(4, action);
            ps.setString(5, targetName);
            ps.setString(6, targetUuid != null ? targetUuid : "");
            ps.setString(7, details != null ? details : "");
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            StaffMod.LOGGER.error("[StaffMod] Error inserting audit log:", e);
        }
    }

    public static void logSanction(String playerUuid, String playerName, String type,
                                    String reason, long durationMs, long expiryTs,
                                    String staffUuid, String staffName) {
        if (connection == null) return;
        try {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sanctions (player_uuid,player_name,type,reason,duration_ms,expiry_ts,staff_uuid,staff_name,created_at) VALUES (?,?,?,?,?,?,?,?,?)");
            ps.setString(1, playerUuid);
            ps.setString(2, playerName);
            ps.setString(3, type);
            ps.setString(4, reason != null ? reason : "");
            ps.setLong(5, durationMs);
            ps.setLong(6, expiryTs);
            ps.setString(7, staffUuid);
            ps.setString(8, staffName);
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            StaffMod.LOGGER.error("[StaffMod] Error inserting sanction:", e);
        }
    }

    public static void logPcInspect(String staffUuid, String staffName,
                                     String targetUuid, String targetName,
                                     String action, String details) {
        if (connection == null) return;
        try {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO pc_inspect_log (staff_uuid,staff_name,target_uuid,target_name,action,details,created_at) VALUES (?,?,?,?,?,?,?)");
            ps.setString(1, staffUuid);
            ps.setString(2, staffName);
            ps.setString(3, targetUuid);
            ps.setString(4, targetName);
            ps.setString(5, action);
            ps.setString(6, details != null ? details : "");
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            StaffMod.LOGGER.error("[StaffMod] Error inserting PC inspect log:", e);
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static void close() {
        if (connection != null) {
            try {
                connection.close();
                StaffMod.LOGGER.info("[StaffMod] Base de datos cerrada correctamente.");
            } catch (SQLException e) {
                StaffMod.LOGGER.error("[StaffMod] Error cerrando base de datos:", e);
            }
        }
    }
}
