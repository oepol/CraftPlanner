package dev.ole.craftplanner;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;

public final class ItemNames {
    private static final Map<String,ItemStack> ICONS = new HashMap<>();
    private static final Map<String,String> NAMES = new HashMap<>();
    private ItemNames() {}
    public static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    public static ItemStack icon(String id) { return ICONS.computeIfAbsent(id, key -> {
        Identifier location = Identifier.tryParse(key);
        return location == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getOptional(location).orElse(Items.AIR));
    }); }
    public static String name(String id) { return NAMES.computeIfAbsent(id, key -> { ItemStack s=icon(key); return s.isEmpty() ? key : s.getHoverName().getString(); }); }
    public static void clear() { ICONS.clear(); NAMES.clear(); }
}
