package dev.ole.craftplanner.core;

import java.util.UUID;

public final class CraftGoal {
    public String id = UUID.randomUUID().toString();
    public String item;
    public long count;
    public boolean paused,pinned,achieved,archived;
    public long delivered;
    public java.util.Map<String,Long> deliveredByChest=new java.util.HashMap<>();
    public CraftGoal(String item, long count) { this.item = item; this.count = count; }
}
