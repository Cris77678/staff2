package me.drex.staffmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerData {

    public UUID uuid;
    public String lastName;

    // Mute
    public boolean muted = false;
    public long muteExpiry = -1;
    public String muteReason = "";

    // Ban
    public boolean banned = false;
    public long banExpiry = -1;
    public String banReason = "";

    // Freeze
    public boolean frozen = false;

    // Jail
    public boolean jailed = false;
    public long jailExpiry = -1;
    public String jailName = "";
    public boolean pendingUnjail = false;

    // Estado del turno (no persistido entre reinicios del servidor)
    public transient boolean onDuty = false;

    // Warns
    public List<WarnEntry> warns = new ArrayList<>();

    public PlayerData(UUID uuid, String lastName) {
        this.uuid = uuid;
        this.lastName = lastName;
    }

    public int warnCount() {
        return warns.size();
    }

    public boolean isMuteActive() {
        if (!muted) return false;
        if (muteExpiry == -1) return true;
        return System.currentTimeMillis() < muteExpiry;
    }

    public boolean isBanActive() {
        if (!banned) return false;
        if (banExpiry == -1) return true;
        if (System.currentTimeMillis() >= banExpiry) { banned = false; return false; }
        return true;
    }

    public boolean isJailActive() {
        if (!jailed) return false;
        if (jailExpiry == -1) return true;
        if (System.currentTimeMillis() >= jailExpiry) { jailed = false; return false; }
        return true;
    }

    /** Formatea tiempo restante como "1d 2h 30m 20s" o "Permanente" */
    public static String formatExpiry(long expiry) {
        if (expiry == -1) return "Permanente";
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) return "Expirado";
        long secs = remaining / 1000;
        long mins = secs / 60; secs %= 60;
        long hours = mins / 60; mins %= 60;
        long days = hours / 24; hours %= 24;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m ");
        sb.append(secs).append("s");
        return sb.toString().trim();
    }

    /** Parsea "10m", "2h", "1d", "perm" → epoch millis de expiración o 0 (protección) */
    public static long parseDuration(String input) {
        if (input == null) return -1;
        String s = input.toLowerCase().trim();
        if (s.equals("perm") || s.equals("permanente") || s.equals("-1")) return -1;
        try {
            long multiplier;
            if (s.endsWith("s")) { multiplier = 1_000L; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { multiplier = 60_000L; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("h")) { multiplier = 3_600_000L; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("d")) { multiplier = 86_400_000L; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("w")) { multiplier = 604_800_000L; s = s.substring(0, s.length() - 1); }
            else return 0;
            return System.currentTimeMillis() + Long.parseLong(s) * multiplier;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public record WarnEntry(String reason, long timestamp, String staffName, String staffUuid) {
        /** Constructor de compatibilidad sin staffUuid */
        public WarnEntry(String reason, long timestamp, String staffName) {
            this(reason, timestamp, staffName, "");
        }
    }
}
