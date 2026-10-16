package dev.ole.craftplanner.compat;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.ole.craftplanner.*;
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return parent->new SettingsScreen(CraftPlannerClient.INSTANCE,parent); }
}
