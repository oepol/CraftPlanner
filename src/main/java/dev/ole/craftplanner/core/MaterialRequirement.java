package dev.ole.craftplanner.core;

public record MaterialRequirement(String item, long needed, long owned) {
    public long missing() { return Math.max(0, needed - owned); }
}
