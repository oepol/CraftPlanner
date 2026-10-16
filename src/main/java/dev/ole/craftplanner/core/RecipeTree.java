package dev.ole.craftplanner.core;

import java.util.List;

public record RecipeTree(String item, long needed, long owned, long surplusUsed,
                         long crafts, String recipe, String note, List<RecipeTree> children) {
    public boolean leaf() { return children.isEmpty(); }
    public long missing() { return Math.max(0, needed - owned - surplusUsed); }
}
