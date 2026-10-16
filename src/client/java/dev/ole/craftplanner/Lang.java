package dev.ole.craftplanner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
public final class Lang {
 private Lang() {}
 public static String tr(String key,Object... args) { return I18n.get(key,args); }
 public static Component text(String key,Object... args) { return Component.translatable(key,args); }
 public static String state(boolean value) { return tr(value ? "craftplanner.on" : "craftplanner.off"); }
}
