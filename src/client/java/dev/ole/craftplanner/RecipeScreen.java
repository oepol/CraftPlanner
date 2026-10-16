package dev.ole.craftplanner;

import dev.ole.craftplanner.core.Recipe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class RecipeScreen extends Screen {
    private final CraftPlannerClient app;
    private final Screen parent;
    private final String item;
    private final List<Recipe> recipes;
    private int x,y,w,h,offset;
    public RecipeScreen(CraftPlannerClient app,Screen parent,String item) {
        super(Component.literal(Lang.tr("craftplanner.recipe_title"))); this.app=app; this.parent=parent; this.item=item;
        recipes=app.catalog.entries().stream().filter(r -> r.output().equals(item)).toList();
    }
    @Override protected void init() {
        w=Math.min(520,width-24); h=Math.min(350,height-24); x=(width-w)/2; y=(height-h)/2;
        addRenderableWidget(new Ui.Action(x+12,y+48,(w-30)/2,22,Lang.tr("craftplanner.recipe_auto"),true,() -> { app.config.rawItems.remove(item); app.config.recipes.remove(item); apply(); }));
        addRenderableWidget(new Ui.Action(x+18+(w-30)/2,y+48,(w-30)/2,22,Lang.tr("craftplanner.gather"),false,() -> { app.config.rawItems.add(item); apply(); }));
        addRenderableWidget(new Ui.Action(x+w-82,y+h-30,70,20,Lang.tr("craftplanner.back"),false,this::onClose));
    }
    private void apply() { app.goals.changed(); app.goals.recalculate(); onClose(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xb0080d14); Ui.panel(g,x,y,w,h,0xf5111c28);
        g.item(ItemNames.icon(item),x+12,y+12); Ui.text(g,Ui.fit(ItemNames.name(item),w-60),x+36,y+13,Ui.ACCENT);
        Ui.text(g,Lang.tr("craftplanner.recipe_info"),x+12,y+32,Ui.MUTED);
        int visible=Math.max(1,(h-122)/40); offset=Math.clamp(offset,0,Math.max(0,recipes.size()-visible));
        g.enableScissor(x+10,y+80,x+w-10,y+h-42);
        for (int i=offset;i<Math.min(recipes.size(),offset+visible);i++) {
            Recipe r=recipes.get(i); int ry=y+82+(i-offset)*40;
            boolean active=r.id().equals(app.config.recipes.get(item)) && !app.config.rawItems.contains(item);
            Ui.panel(g,x+12,ry,w-24,36,Ui.over(mx,my,x+12,ry,w-24,36)?0xff354c60:0xff213140);
            Ui.text(g,(active?"✓ ":"")+r.outputCount()+" × "+Lang.tr(r.kind()),x+20,ry+5,active?Ui.ACCENT:Ui.TEXT);
            String ingredients=r.inputs().stream().map(in -> in.count()+" × "+ItemNames.name(in.alternatives().getFirst())+(in.alternatives().size()>1?Lang.tr("craftplanner.variants"):"")).reduce((a,b) -> a+", "+b).orElse("");
            Ui.text(g,Ui.fit(ingredients,w-44),x+20,ry+20,Ui.MUTED);
            if (Ui.over(mx,my,x+12,ry,w-24,36)) g.setTooltipForNextFrame(font,Component.literal(ingredients),mx,my);
        }
        if (recipes.isEmpty()) Ui.text(g,Lang.tr("craftplanner.no_recipe"),x+16,y+90,Ui.MUTED);
        g.disableScissor();
        Ui.text(g,app.config.rawItems.contains(item)?Lang.tr("craftplanner.gather_current"):Lang.tr("craftplanner.recipe_help"),x+12,y+h-40,Ui.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if (super.mouseClicked(e,twice)) return true;
        if (!Ui.over(e.x(),e.y(),x+12,y+82,w-24,h-124)) return false;
        int index=offset+(int)(e.y()-y-82)/40;
        if (index>=recipes.size()) return false;
        app.config.rawItems.remove(item); app.config.recipes.put(item,recipes.get(index).id()); apply(); return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) { offset=Math.max(0,offset-(int)Math.signum(vertical)); return true; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
