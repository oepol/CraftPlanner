package dev.ole.craftplanner;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class KeybindManager {
    public static KeyMapping toggle,menu;
    private static boolean toggleHeld,menuHeld;
    public static String menuLabel() { return label(menu); }
    public static String toggleLabel() { return label(toggle); }
    private static String label(KeyMapping key) { return key==null || key.isUnbound() ? Lang.tr("craftplanner.unbound") : key.getTranslatedKeyMessage().getString(); }
    private KeybindManager() {}
    public static void register(CraftPlannerClient app) {
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("craftplanner","planner"));
        toggle=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftplanner.toggle",InputConstants.Type.KEYBOARD,InputConstants.KEY_H,category));
        menu=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.craftplanner.menu",InputConstants.Type.KEYBOARD,InputConstants.KEY_G,category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean t=false,m=false;
            while(toggle.consumeClick())t=true; while(menu.consumeClick())m=true;
            if(mc.player!=null && mc.gui.screen()==null) {
                if(t && !toggleHeld) { app.config.enabled=!app.config.enabled; app.save(); }
                if(m && !menuHeld)mc.gui.setScreen(new PlannerScreen(app));
            }
            toggleHeld=toggle.isDown(); menuHeld=menu.isDown();
        });
    }
}
