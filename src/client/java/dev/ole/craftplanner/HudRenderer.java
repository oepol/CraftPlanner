package dev.ole.craftplanner;
import dev.ole.craftplanner.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class HudRenderer {
    private final CraftPlannerClient app;
    public HudRenderer(CraftPlannerClient app) { this.app=app; }
    public int renderedHeight() { return Math.max(180,Math.min(app.config.maxHeight,280)); }
    public double scale() {
        var win=Minecraft.getInstance().getWindow();
        return Math.min(app.config.scale,Math.min((win.getGuiScaledWidth()-8.0)/app.config.width,(win.getGuiScaledHeight()-8.0)/renderedHeight()));
    }
    public int x(int sw) { return Math.clamp(app.config.x,4,Math.max(4,sw-(int)(app.config.width*scale())-4)); }
    public int y(int sh) { return Math.clamp(app.config.y,4,Math.max(4,sh-(int)(renderedHeight()*scale())-4)); }
    public void draw(GuiGraphicsExtractor g,int mouseX,int mouseY,boolean editor) {
        var cfg=app.config; var plan=app.goals.pinnedPlan(); var materials=app.goals.gathering(); var steps=CraftSteps.next(plan);
        int x=x(g.guiWidth()),y=y(g.guiHeight()),w=cfg.width,h=renderedHeight();
        double mx=(mouseX-x)/scale(),my=(mouseY-y)/scale(); List<Component> tooltip=null;
        g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale((float)scale());
        Ui.panel(g,0,0,w,h,((int)(cfg.opacity*255)<<24)|0x101923); g.fill(10,12,13,25,Ui.ACCENT);
        Ui.text(g,Lang.tr("craftplanner.title"),20,12,Ui.TEXT);
        Ui.text(g,Lang.tr("craftplanner.pins",Pins.count(cfg.goals),cfg.pinLimit),20,25,Ui.MUTED);
        int cy=42,goalRows=Math.min(3,Math.max(1,(h-150)/25));
        for(var goal:plan.goals().stream().limit(goalRows).toList()) {
            long owned=goal.tree().owned()+goal.goal().delivered,need=goal.goal().count;
            g.item(ItemNames.icon(goal.goal().item),10,cy);
            Ui.text(g,Ui.fit(ItemNames.name(goal.goal().item),w-94),33,cy,Ui.TEXT);
            String percent=(owned*100/need)+"%";
            Ui.text(g,percent,w-12-Minecraft.getInstance().font.width(percent),cy,owned>=need?Ui.ACCENT:Ui.MUTED);
            Ui.text(g,Lang.tr("craftplanner.material_count",owned,need),33,cy+10,Ui.MUTED);
            Ui.bar(g,33,cy+21,w-45,(double)owned/need); cy+=27;
        }
        if(plan.goals().isEmpty()) { Ui.text(g,Ui.fit(Lang.tr("craftplanner.hud_empty",KeybindManager.menuLabel()),w-20),10,cy,Ui.MUTED); cy+=20; }
        if(!steps.isEmpty()) {
            Ui.text(g,Lang.tr("craftplanner.next_steps"),10,cy,Ui.MUTED); cy+=13;
            int limit=h>=250?2:1;
            for(var step:steps.stream().limit(limit).toList()) {
                g.item(ItemNames.icon(step.item()),10,cy);
                String label=step.readyCrafts()>0 ? Lang.tr("craftplanner.steps_ready",step.readyCrafts(),ItemNames.name(step.item()),Lang.tr(step.kind())) : Lang.tr("craftplanner.steps_wait",step.crafts(),ItemNames.name(step.item()));
                Ui.text(g,Ui.fit(label,w-44),33,cy+4,step.readyCrafts()>0?Ui.ACCENT:Ui.MUTED); cy+=22;
            }
        }
        // Always reserve a separate gathering area, regardless of how many goals/steps exist.
        cy=Math.min(cy+4,h-69); Ui.text(g,Lang.tr("craftplanner.gathering"),10,cy,Ui.MUTED); cy+=14;
        int visible=Math.max(1,(h-30-cy)/22),shown=0;
        for(var row:materials.stream().limit(visible).toList()) {
            g.item(ItemNames.icon(row.item()),10,cy);
            Ui.text(g,Ui.fit(ItemNames.name(row.item()),w-93),33,cy,Ui.TEXT);
            String n=Long.toString(row.missing()); Ui.text(g,n,w-12-Minecraft.getInstance().font.width(n),cy,Ui.WARN);
            Ui.text(g,Lang.tr("craftplanner.material_count",row.owned(),row.needed()),33,cy+10,Ui.MUTED);
            if(editor && Ui.over(mx,my,6,cy,w-12,22))tooltip=app.sources.details(row.item());
            shown++; cy+=22;
        }
        if(materials.isEmpty() && !plan.goals().isEmpty())Ui.text(g,Ui.fit(Lang.tr(steps.isEmpty()?"craftplanner.hud_finished":"craftplanner.all_stock"),w-20),10,cy,Ui.ACCENT);
        String footer=materials.size()>shown ? Lang.tr("craftplanner.hud_more",materials.size()-shown,KeybindManager.menuLabel()) : Lang.tr("craftplanner.hud_keys",KeybindManager.toggleLabel(),KeybindManager.menuLabel());
        Ui.text(g,Ui.fit(footer,w-20),10,h-13,Ui.MUTED);
        g.pose().popMatrix();
        if(tooltip!=null)g.setComponentTooltipForNextFrame(Minecraft.getInstance().font,tooltip,mouseX,mouseY);
    }
}
