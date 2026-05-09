package me.drex.staffmod.config;

import java.util.List;

public class RankConfig {

    public String id;
    public String displayName;
    public String prefix;
    public String color;
    public int priority;
    public String requiredPermission;
    public List<String> modulesAccess;

    public RankConfig(String id, String displayName, String prefix, String color,
                      int priority, String requiredPermission, List<String> modulesAccess) {
        this.id = id;
        this.displayName = displayName;
        this.prefix = prefix;
        this.color = color;
        this.priority = priority;
        this.requiredPermission = requiredPermission;
        this.modulesAccess = modulesAccess;
    }
}
