package dev.ole.craftplanner;
import dev.ole.craftplanner.core.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;

/** Positions come from a real client block interaction, never a container title or guessed ray trace. */
public final class ChestSources {
    private final CraftPlannerClient app;
    private ChestSource candidate,opened;
    private long candidateAt;
    private ChestMenu menu;
    private Ui.Action button;
    private Map<String,Long> live=Map.of();
    private boolean changed;
    private Map<String,Long> previousChest,deposits=Map.of();
    public Map<String,Long> deposits() { return deposits; }
    public String openedKey() { return opened==null ? "" : opened.key(); }
    public ChestSources(CraftPlannerClient app) { this.app=app; }
    public void register() {
        UseBlockCallback.EVENT.register((player,world,hand,hit) -> {
            if(!world.isClientSide())return InteractionResult.PASS;
            candidate=null;
            var state=world.getBlockState(hit.getBlockPos());
            if(state.getBlock() instanceof ChestBlock) {
                BlockPos pos=hit.getBlockPos(); String other=null;
                if(state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE) {
                    BlockPos partner=ChestBlock.getConnectedBlockPos(pos,state);
                    if(!(world.getBlockState(partner).getBlock() instanceof ChestBlock))return InteractionResult.PASS;
                    other=position(partner);
                }
                candidate=new ChestSource(world.dimension().identifier().toString(),position(pos),other);
                candidateAt=System.nanoTime();
            }
            return InteractionResult.PASS;
        });
        ScreenEvents.AFTER_INIT.register((mc,screen,width,height) -> {
            if(!(screen instanceof ContainerScreen chest) || mc.level==null)return;
            if(menu!=chest.getMenu()) {
                opened=null; previousChest=null; menu=chest.getMenu();
                if(candidate!=null && System.nanoTime()-candidateAt<5_000_000_000L
                    && candidate.dimension.equals(dimension()) && menu.getRowCount()==(candidate.partner==null?3:6))opened=candidate;
                candidate=null;
            }
            // Vanilla chest geometry: place outside the 176 px container, never over slots/labels.
            int left=(width-176)/2,top=(height-(114+18*menu.getRowCount()))/2;
            int bx=left+180,bw=Math.min(130,width-bx-4),by=Math.max(4,top+4);
            if(bw<28) { bx=4; bw=Math.max(20,left-8); }
            button=new Ui.Action(bx,by,bw,20,"",false,()->toggle(mc));
            Screens.getWidgets(screen).add(button); updateButton();
        });
    }
    private static String position(BlockPos pos) { return pos.getX()+", "+pos.getY()+", "+pos.getZ(); }
    public String dimension() { var mc=Minecraft.getInstance(); return mc.level==null ? "" : mc.level.dimension().identifier().toString(); }
    public boolean isLive(String key) { return opened!=null && menu!=null && key.equals(opened.key()) && app.config.sources.containsKey(key) && opened.dimension.equals(dimension()); }
    public Map<String,Long> liveStock() { return opened==null ? Map.of() : ChestSource.unreserved(live,app.config.goals,opened.key()); }
    public void update(Minecraft mc) {
        deposits=Map.of();
        if(menu!=null && (mc.player==null || mc.player.containerMenu!=menu || !(mc.gui.screen() instanceof ContainerScreen))) {
            if(changed)app.save(); changed=false; opened=null; menu=null; button=null; live=Map.of(); previousChest=null;
        }
        if(menu==null || opened==null) { live=Map.of(); return; }
        Map<String,Long> current=read(menu);
        boolean firstObservation=previousChest==null;
        if(previousChest!=null) {
            Map<String,Long> gain=new HashMap<>();
            Set<String> keys=new HashSet<>(current.keySet()); keys.addAll(previousChest.keySet());
            keys.forEach(k->{long n=current.getOrDefault(k,0L)-previousChest.getOrDefault(k,0L); if(n!=0)gain.put(k,n);}); deposits=Map.copyOf(gain);
        }
        previousChest=Map.copyOf(current);
        var saved=app.config.sources.get(opened.key());
        if(saved!=null && opened.dimension.equals(dimension())) {
            live=Map.copyOf(current);
            if(firstObservation || !saved.lastStock.equals(current)) { saved.lastStock=new TreeMap<>(current); saved.observedAt=System.currentTimeMillis(); changed=true; }
        } else live=Map.of();
        updateButton();
    }
    private static Map<String,Long> read(ChestMenu menu) {
        Map<String,Long> stock=new HashMap<>();
        // Only chest slots; player slots and cursor are sampled once by InventoryTracker.
        for(int i=0;i<menu.getRowCount()*9;i++)InventoryTracker.add(stock,menu.getSlot(i).getItem());
        return stock;
    }
    private void toggle(Minecraft mc) {
        if(opened==null) { message("source_unknown"); return; }
        String key=opened.key();
        if(app.config.sources.remove(key)!=null) { live=Map.of(); app.goals.changed(); updateButton(); message("source_removed"); return; }
        if(app.config.goals.stream().noneMatch(g->!g.paused && !g.archived)) { message("source_no_plan"); return; }
        Map<String,Long> stock=read(menu);
        Set<String> relevant=new HashSet<>();
        app.config.goals.stream().filter(g->!g.paused && !g.archived).forEach(g->relevant.add(g.item));
        for(int depth=0;depth<32;depth++) {
            int old=relevant.size();
            for(var r:app.catalog.entries())if(relevant.contains(r.output()))for(var in:r.inputs())relevant.addAll(in.alternatives());
            if(old==relevant.size())break;
        }
        if(stock.keySet().stream().noneMatch(relevant::contains)) { message("source_no_match"); return; }
        ChestSource source=new ChestSource(opened.dimension,opened.position,opened.partner);
        source.lastStock=new TreeMap<>(stock); source.observedAt=System.currentTimeMillis();
        app.config.sources.put(key,source); live=Map.copyOf(stock); app.goals.changed(); updateButton(); message("source_added");
    }
    private void updateButton() {
        if(button==null)return;
        boolean included=opened!=null && app.config.sources.containsKey(opened.key());
        String action=included?"source_remove":"source_add";
        button.setMessage(Lang.text("craftplanner."+action+(button.getWidth()<120?"_short":"")));
        String tip=opened==null ? "source_unknown" : !included && app.config.goals.stream().noneMatch(g->!g.paused && !g.archived) ? "source_no_plan" : action;
        button.setTooltip(Tooltip.create(Lang.text("craftplanner."+tip)));
        button.active=opened!=null && (included || app.config.goals.stream().anyMatch(g->!g.paused && !g.archived));
    }
    private void message(String key) { var p=Minecraft.getInstance().player; if(p!=null)p.sendOverlayMessage(Lang.text("craftplanner."+key)); }
    public void remove(String key) { app.config.sources.remove(key); if(opened!=null && key.equals(opened.key()))live=Map.of(); app.goals.changed(); }
    public long cached(String item) { return app.config.sources.entrySet().stream().filter(e->!isLive(e.getKey()) && e.getValue().dimension.equals(dimension())).mapToLong(e->e.getValue().lastStock.getOrDefault(item,0L)).sum(); }
    public List<net.minecraft.network.chat.Component> details(String item) {
        var stock=app.inventory.sources();
        return List.of(Lang.text("craftplanner.source_details"),Lang.text("craftplanner.source_direct",stock.direct().getOrDefault(item,0L)),Lang.text("craftplanner.source_shulker",stock.shulkers().getOrDefault(item,0L)),Lang.text("craftplanner.source_chest",stock.openChest().getOrDefault(item,0L)),Lang.text("craftplanner.source_last",cached(item)));
    }
    public void clear() { if(changed)app.save(); changed=false; candidate=null; opened=null; menu=null; button=null; live=Map.of(); previousChest=null; deposits=Map.of(); }
}
