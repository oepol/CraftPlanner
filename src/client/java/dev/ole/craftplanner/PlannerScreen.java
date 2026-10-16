package dev.ole.craftplanner;

import com.mojang.blaze3d.platform.InputConstants;
import dev.ole.craftplanner.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import java.util.*;

public final class PlannerScreen extends Screen {
    private final CraftPlannerClient app;
    private int tab,offset,x,y,w,h,top,bottom,rowHeight;
    private String query="",selected="minecraft:piston",editing;
    private long quantity=50;
    private EditBox search,amount;
    private List<String> items=List.of();
    private final Set<String> collapsed=new HashSet<>();
    private List<TreeRow> treeRows=List.of();
    private RecipeResolver.Plan treePlan;
    private boolean treeDirty=true,showArchived;
    private List<CraftGoal> visibleGoals() { return app.config.goals.stream().filter(g->showArchived || !g.archived).sorted(Comparator.comparing((CraftGoal g)->g.archived)).toList(); }
    private record TreeRow(RecipeTree node,int depth,String key) {}
    public PlannerScreen(CraftPlannerClient app) { super(Lang.text("craftplanner.title")); this.app=app; }
    @Override protected void init() {
        w=Math.min(620,width-16); h=Math.min(430,height-16); x=(width-w)/2; y=(height-h)/2;
        clearWidgets();
        add(x+w-64,y+10,54,20,Lang.tr("craftplanner.done"),false,this::onClose);
        add(x+w-142,y+10,72,20,Lang.tr("craftplanner.settings"),false,() -> minecraft.gui.setScreen(new SettingsScreen(app,this)));
        add(x+14,y+27,60,12,Lang.tr("craftplanner.sources"),false,()->minecraft.gui.setScreen(new SourcesScreen(app,this)));
        add(x+80,y+27,70,12,Lang.tr("craftplanner.steps_short"),false,()->minecraft.gui.setScreen(new StepsScreen(app,this)));
        String[] tabs={Lang.tr("craftplanner.items"),Lang.tr("craftplanner.goals"),Lang.tr("craftplanner.materials"),Lang.tr("craftplanner.tree")};
        int tw=(w-24)/4;
        for (int i=0;i<4;i++) { int target=i; add(x+10+i*tw,y+42,tw-3,22,tabs[i],tab==i,() -> { saveFields(); tab=target; offset=0; init(); }); }
        top=y+74; bottom=y+h-36; rowHeight=tab==0 ? 25 : tab==1 ? 38 : 27;
        if (tab==0) {
            search=new EditBox(font,x+12,top,w-24,20,Component.literal(Lang.tr("craftplanner.search"))); search.setMaxLength(120);
            search.setHint(Component.literal(Lang.tr("craftplanner.search_hint"))); search.setValue(query);
            search.setResponder(value -> { query=value; offset=0; filterItems(); }); addRenderableWidget(search);
            top+=29; bottom=y+h-104;
            amount=new EditBox(font,x+12,y+h-62,70,20,Component.literal(Lang.tr("craftplanner.amount"))); amount.setMaxLength(7); amount.setValue(Long.toString(quantity));
            amount.setResponder(value -> { try { quantity=Math.clamp(Long.parseLong(value),1,1_000_000); } catch (NumberFormatException ignored) { } });
            addRenderableWidget(amount);
            int bx=x+89; int bw=Math.max(23,(w-103)/6);
            int[] delta={-64,-10,-1,1,10,64};
            for (int n:delta) { add(bx,y+h-62,bw-2,20,(n>0?"+":"")+n,false,() -> setAmount(quantity+n)); bx+=bw; }
            add(x+w-132,y+h-34,120,19,editing==null ? Lang.tr("craftplanner.add_goal") : Lang.tr("craftplanner.save_goal"),true,this::commit);
            int[] presets={1,16,32,64,128,256}; bx=x+12;
            int presetWidth=Math.min(38,(w-154)/6);
            for (int n:presets) { add(bx,y+h-34,presetWidth-3,19,""+n,false,() -> setAmount(n)); bx+=presetWidth; }
            filterItems(); setInitialFocus(search);
        } else if (tab==1) {
            add(x+12,y+h-27,132,19,Lang.tr("craftplanner.clear_complete"),false,() -> { app.goals.clearCompleted(); app.goals.recalculate(); offset=0; });
            if(app.profiles.canImport())add(x+150,y+h-27,144,19,Lang.tr("craftplanner.import_legacy"),false,()->{app.profiles.importLegacy();app.goals.changed();app.goals.recalculate();init();});
            else add(x+w-150,y+h-27,138,19,Lang.tr("craftplanner.show_archived",Lang.state(showArchived)),false,()->{showArchived=!showArchived;offset=0;init();});
        } else if (tab==2) {
            add(x+12,y+h-27,140,19,Lang.tr("craftplanner.missing_only",Lang.state(app.config.hideCompleteMaterials)),false,() -> { app.config.hideCompleteMaterials=!app.config.hideCompleteMaterials; app.goals.changed(); app.goals.recalculate(); init(); });
            add(x+160,y+h-27,125,19,Lang.tr("craftplanner.sort",Lang.tr("craftplanner.sort."+app.config.sort)),false,() -> {
                app.config.sort=switch(app.config.sort) { case "missing" -> "name"; case "name" -> "progress"; default -> "missing"; };
                app.goals.changed(); app.goals.recalculate(); init();
            });
        } else {
            add(x+12,y+h-27,130,19,Lang.tr("craftplanner.expand_all"),false,() -> { collapsed.clear(); treeDirty=true; });
        }
    }
    private void add(int ax,int ay,int aw,int ah,String label,boolean accent,Runnable action) { addRenderableWidget(new Ui.Action(ax,ay,aw,ah,label,accent,action)); }
    private void saveFields() { if (tab==0 && search!=null) query=search.getValue(); }
    private void setAmount(long value) { quantity=Math.clamp(value,1,1_000_000); amount.setValue(Long.toString(quantity)); }
    private void filterItems() {
        String q=query.toLowerCase(Locale.ROOT).trim();
        items=BuiltInRegistries.ITEM.stream().filter(item -> item!=Items.AIR).map(ItemNames::id)
            .filter(id -> id.contains(q) || ItemNames.name(id).toLowerCase(Locale.ROOT).contains(q))
            .sorted(Comparator.comparing(ItemNames::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private void commit() {
        try { quantity=Long.parseLong(amount.getValue()); } catch (NumberFormatException e) { amount.setValue("50"); quantity=50; return; }
        quantity=Math.clamp(quantity,1,1_000_000);
        CraftGoal goal=app.config.goals.stream().filter(g -> Objects.equals(g.id,editing)).findFirst().orElse(null);
        if (goal==null) app.goals.add(selected,quantity);
        else { goal.item=selected; goal.count=quantity; goal.archived=false; goal.achieved=false; goal.delivered=0; goal.deliveredByChest.clear(); app.goals.changed(); }
        editing=null; app.goals.recalculate(); tab=1; offset=0; init();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xa0080d14); Ui.panel(g,x,y,w,h,0xf5111c28);
        Ui.text(g,Lang.tr("craftplanner.title"),x+14,y+13,Ui.ACCENT);
        
        if (tab==3) buildTreeRows();
        int total=size(),visible=Math.max(1,(bottom-top)/rowHeight);
        offset=Math.clamp(offset,0,Math.max(0,total-visible));
        g.enableScissor(x+8,top,x+w-8,bottom);
        for (int i=offset;i<Math.min(total,offset+visible);i++) {
            int ry=top+(i-offset)*rowHeight;
            boolean hover=Ui.over(mx,my,x+10,ry,w-20,rowHeight-2);
            if (hover) Ui.panel(g,x+10,ry,w-20,rowHeight-2,0xff25384a);
            if (tab==0) drawItem(g,i,ry);
            else if (tab==1) drawGoal(g,i,ry,mx,my);
            else if (tab==2) drawMaterial(g,i,ry,hover,mx,my);
            else drawTree(g,i,ry,hover,mx,my);
        }
        if (total==0) Ui.text(g,tab==0 ? Lang.tr("craftplanner.no_items") : Lang.tr("craftplanner.empty_plan"),x+18,top+12,Ui.MUTED);
        g.disableScissor();
        if (total>visible) {
            int track=bottom-top;
            g.fill(x+w-6,top,x+w-4,bottom,0xff2a3949);
            int thumb=Math.max(10,track*visible/total),sy=top+(track-thumb)*offset/Math.max(1,total-visible);
            g.fill(x+w-6,sy,x+w-4,sy+thumb,Ui.ACCENT);
        }
        if (tab==0) {
            g.item(ItemNames.icon(selected),x+13,y+h-90);
            Ui.text(g,Ui.fit((editing==null ? ItemNames.name(selected) : Lang.tr("craftplanner.editing",ItemNames.name(selected))),w-50),x+36,y+h-86,Ui.ACCENT);
        } else {
            String status=tab==1 ? Lang.tr("craftplanner.help_goals") : tab==2 ? Lang.tr("craftplanner.help_materials") : Lang.tr("craftplanner.help_tree");
            Ui.text(g,Ui.fit(status,w-28),x+14,y+65,Ui.MUTED);
        }
        super.extractRenderState(g,mx,my,delta);
    }
    private void drawItem(GuiGraphicsExtractor g,int i,int ry) {
        String id=items.get(i);
        if (id.equals(selected)) g.fill(x+10,ry,x+13,ry+rowHeight-2,Ui.ACCENT);
        g.item(ItemNames.icon(id),x+18,ry+3);
        Ui.text(g,Ui.fit(ItemNames.name(id),w/2-40),x+42,ry+7,Ui.TEXT);
        Ui.text(g,Ui.fit(id,w/2-22),x+w/2,ry+7,Ui.MUTED);
    }
    private void drawGoal(GuiGraphicsExtractor g,int i,int ry,int mx,int my) {
        CraftGoal goal=visibleGoals().get(i); long owned=app.goals.owned(goal);
        g.item(ItemNames.icon(goal.item),x+18,ry+5);
        Ui.text(g,Ui.fit(ItemNames.name(goal.item),w-210),x+42,ry+4,goal.paused?Ui.MUTED:Ui.TEXT);
        Ui.text(g,goal.archived?Lang.tr("craftplanner.archived"):goal.paused?Lang.tr("craftplanner.paused"):Lang.tr("craftplanner.progress",owned,goal.count,owned*100/goal.count),x+42,ry+16,owned>=goal.count?Ui.ACCENT:Ui.MUTED);
        Ui.bar(g,x+42,ry+29,w-206,(double)owned/goal.count);
        if(Ui.over(mx,my,x+w-156,ry,36,rowHeight))g.setTooltipForNextFrame(font,Lang.text(goal.pinned?"craftplanner.unpin":"craftplanner.pin"),mx,my);
        Ui.text(g,goal.pinned?"●":"+",x+w-147,ry+10,goal.pinned?Ui.ACCENT:Ui.MUTED);
        Ui.text(g,Lang.tr("craftplanner.edit"),x+w-116,ry+10,Ui.ACCENT); Ui.text(g,"×",x+w-27,ry+10,Ui.WARN);
    }
    private void drawMaterial(GuiGraphicsExtractor g,int i,int ry,boolean hover,int mx,int my) {
        MaterialRequirement row=app.goals.rows().get(i);
        g.item(ItemNames.icon(row.item()),x+18,ry+4);
        Ui.text(g,Ui.fit(ItemNames.name(row.item()),w-205),x+42,ry+4,row.missing()==0?Ui.ACCENT:Ui.TEXT);
        Ui.text(g,row.owned()+" / "+row.needed(),x+w-150,ry+4,Ui.MUTED);
        Ui.text(g,Lang.tr("craftplanner.missing",row.missing()),x+42,ry+15,row.missing()==0?Ui.ACCENT:Ui.WARN);
        if (hover) g.setComponentTooltipForNextFrame(font,joinTips(List.of(Component.literal(ItemNames.name(row.item())),Component.literal(Lang.tr("craftplanner.allocated",row.needed(),row.owned())),Component.literal(Lang.tr("craftplanner.inventory",app.inventory.counts().getOrDefault(row.item(),0L))),Component.literal(Lang.tr("craftplanner.recipe_hint"))),app.sources.details(row.item())),mx,my);
    }
    private static List<Component> joinTips(List<Component> a,List<Component> b) { var all=new ArrayList<>(a); all.addAll(b); return all; }
    private void drawTree(GuiGraphicsExtractor g,int i,int ry,boolean hover,int mx,int my) {
        TreeRow row=treeRows.get(i); var n=row.node(); int indent=Math.min(row.depth()*12,w/3);
        Ui.text(g,n.children().isEmpty()?"·":collapsed.contains(row.key())?"+":"−",x+16+indent,ry+8,Ui.ACCENT);
        g.item(ItemNames.icon(n.item()),x+28+indent,ry+3);
        Ui.text(g,Ui.fit(ItemNames.name(n.item()),w-indent-200),x+50+indent,ry+4,Ui.TEXT);
        Ui.text(g,n.owned()+" / "+n.needed(),x+w-127,ry+4,Ui.MUTED);
        Ui.text(g,Ui.fit(n.crafts()>0 ? Lang.tr("craftplanner.crafts",n.crafts(),Lang.tr(n.note())) : n.missing()==0 ? Lang.tr("craftplanner.stock") : Lang.tr("craftplanner.gather_hint"),w-indent-90),x+50+indent,ry+15,Ui.MUTED);
        if (hover) g.setComponentTooltipForNextFrame(font,List.of(Component.literal(ItemNames.name(n.item())),Component.literal(Lang.tr("craftplanner.needed_owned",n.needed(),n.owned())),Component.literal(Lang.tr("craftplanner.surplus",n.surplusUsed())),Component.literal(n.note().isBlank()?Lang.tr("craftplanner.tree_hint"):Lang.tr(n.note()))),mx,my);
    }
    private void buildTreeRows() {
        if (!treeDirty && treePlan==app.goals.plan()) return;
        treeDirty=false; treePlan=app.goals.plan();
        List<TreeRow> result=new ArrayList<>();
        for (var goal:app.goals.plan().goals()) flatten(goal.tree(),0,goal.goal().id,result);
        treeRows=List.copyOf(result);
    }
    private void flatten(RecipeTree n,int depth,String key,List<TreeRow> rows) {
        rows.add(new TreeRow(n,depth,key));
        if (!collapsed.contains(key)) for (int i=0;i<n.children().size();i++) flatten(n.children().get(i),depth+1,key+"/"+i,rows);
    }
    private int size() { return switch(tab) { case 0 -> items.size(); case 1 -> visibleGoals().size(); case 2 -> app.goals.rows().size(); default -> treeRows.size(); }; }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if (super.mouseClicked(e,twice)) return true;
        if (!Ui.over(e.x(),e.y(),x+10,top,w-20,bottom-top)) return false;
        int i=offset+(int)(e.y()-top)/rowHeight; if (i>=size()) return false;
        if (tab==0) selected=items.get(i);
        else if (tab==1) {
            CraftGoal goal=visibleGoals().get(i);
            if (e.hasShiftDown() || e.x()>x+w-40) { app.goals.remove(goal); app.goals.recalculate(); }
            else if(e.x()>=x+w-156 && e.x()<x+w-120) { app.goals.togglePin(goal); app.goals.recalculate(); }
            else if (e.button()==InputConstants.MOUSE_BUTTON_RIGHT) { goal.paused=!goal.paused; app.goals.changed(); app.goals.recalculate(); }
            else { editing=goal.id; selected=goal.item; quantity=goal.count; tab=0; offset=0; init(); }
        } else if (tab==2) minecraft.gui.setScreen(new RecipeScreen(app,this,app.goals.rows().get(i).item()));
        else {
            TreeRow row=treeRows.get(i);
            if (e.button()==InputConstants.MOUSE_BUTTON_RIGHT) minecraft.gui.setScreen(new RecipeScreen(app,this,row.node().item()));
            else { if (!collapsed.add(row.key())) collapsed.remove(row.key()); treeDirty=true; }
        }
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if (Ui.over(mx,my,x,top,w,bottom-top)) { offset=Math.max(0,offset-(int)Math.signum(vertical)*3); return true; }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { app.save(); minecraft.gui.setScreen(null); }
}
