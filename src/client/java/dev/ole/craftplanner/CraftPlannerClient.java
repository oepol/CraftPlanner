package dev.ole.craftplanner;

import dev.ole.craftplanner.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.slf4j.*;

public final class CraftPlannerClient implements ClientModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("craftplanner");
    public static CraftPlannerClient INSTANCE;
    public PlannerConfig config;
    public final RecipeCatalog catalog=new RecipeCatalog();
    public final InventoryTracker inventory=new InventoryTracker();
    public final GoalManager goals=new GoalManager(this);
    public final HudRenderer hud=new HudRenderer(this);
    private ConfigManager storage;
    public PlanProfiles profiles;
    public final ChestSources sources=new ChestSources(this);
    SettingsScreen settings;
    private Object connection;
    private int ticks;
    private Object level;
    @Override public void onInitializeClient() {
        INSTANCE=this;
        storage=new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve("craftplanner.json"),LOG::warn);
        config=storage.load(); profiles=new PlanProfiles(config);
        KeybindManager.register(this); sources.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { cancelSettings(); save(); });
        ClientPlayConnectionEvents.DISCONNECT.register((listener,mc) -> { cancelSettings(); sources.clear(); profiles.activate(null); save(); connection=null; reset(); });
        ClientPlayConnectionEvents.JOIN.register((listener,sender,mc) -> { reset(); connection=null; });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("craftplanner","goals"),(graphics,delta) -> {
            Minecraft mc=Minecraft.getInstance();
            if (config.enabled && mc.player!=null && !(mc.gui.screen() instanceof PlannerScreen) && !(mc.gui.screen() instanceof SettingsScreen) && !(mc.gui.screen() instanceof SourcesScreen) && !(mc.gui.screen() instanceof StepsScreen) && !(mc.gui.screen() instanceof HudEditorScreen))
                hud.draw(graphics,-1,-1,false);
        });
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Identifier.fromNamespaceAndPath("craftplanner","refresh"),new SimpleReloadListener<Boolean>() {
            @Override protected Boolean prepare(PreparableReloadListener.SharedState state) { return true; }
            @Override protected void apply(Boolean ignored,PreparableReloadListener.SharedState state) {
                ItemNames.clear(); catalog.invalidate(); goals.invalidate();
            }
        });
        save();
    }
    private void tick(Minecraft mc) {
        if (mc.level!=level) { reset(); level=mc.level; }
        if (mc.player==null || mc.level==null) return;
        if (connection!=mc.getConnection()) {
            connection=mc.getConnection();
            var server=mc.getSingleplayerServer(); var remote=mc.getCurrentServer();
            String identity=server!=null ? "world:"+server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize()
                : remote!=null && remote.ip!=null && !remote.ip.isBlank() ? "server:"+remote.ip.toLowerCase(java.util.Locale.ROOT)+":"+mc.player.getUUID()
                : "session:"+java.util.UUID.randomUUID();
            profiles.activate(PlanProfiles.key(identity)); goals.clear(); save();
        }
        var before=inventory.sources();
        sources.update(mc);
        if (inventory.update(mc,config.includeShulkers,sources.liveStock())) goals.invalidate();
        if(goals.observe(before,inventory.sources(),sources.deposits()))inventory.update(mc,config.includeShulkers,sources.liveStock());
        ++ticks;
        if (ticks%20==0 && catalog.refresh(mc)) goals.invalidate();
        goals.recalculate();
    }
    private void reset() { sources.clear(); catalog.clear(); inventory.clear(); goals.clear(); ticks=19; }
    public boolean save() {
        if(storage==null || config==null)return false;
        return storage.save(profiles.forStorage(settings==null ? config : settings.originalSettings()));
    }
    private void cancelSettings() { if(settings!=null)settings.cancel(false); }
    public void refresh() { catalog.invalidate(); goals.invalidate(); }
}
