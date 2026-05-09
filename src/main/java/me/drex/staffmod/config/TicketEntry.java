package me.drex.staffmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TicketEntry {

    public int id;
    public UUID creatorUuid;
    public String creatorName;
    public String message;
    public String status;  // "ABIERTO", "TOMADO", "CERRADO"
    public String handledBy;
    public long createdAt;
    public long updatedAt;
    
    // Variables nuevas para respuestas
    public List<String> replies = new ArrayList<>();
    public boolean hasUnreadReply = false;

    public TicketEntry(int id, UUID creatorUuid, String creatorName, String message) {
        this.id = id;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.message = message;
        this.status = "ABIERTO";
        this.handledBy = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
}
