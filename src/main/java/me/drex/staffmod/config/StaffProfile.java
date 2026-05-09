package me.drex.staffmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StaffProfile {

    public UUID uuid;
    public String name;
    public int bans = 0;
    public int mutes = 0;
    public int warns = 0;
    public int jails = 0;
    public int kicks = 0;
    public List<String> recentHistory = new ArrayList<>();

    public StaffProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public void addAction(String action) {
        recentHistory.add(0, action);
        if (recentHistory.size() > 20) {
            recentHistory.remove(20);
        }
    }
}
