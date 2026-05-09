package me.drex.staffmod.config;

public class Kit {

    public String id;
    public String displayName;
    public String permissionNode;
    public long cooldownSeconds;
    public String displayIconId;
    public String base64Inventory;

    public Kit(String id, String displayName, String permissionNode,
               long cooldownSeconds, String displayIconId, String base64Inventory) {
        this.id = id;
        this.displayName = displayName;
        this.permissionNode = permissionNode;
        this.cooldownSeconds = cooldownSeconds;
        this.displayIconId = displayIconId != null ? displayIconId : "minecraft:chest";
        this.base64Inventory = base64Inventory != null ? base64Inventory : "";
    }
}
