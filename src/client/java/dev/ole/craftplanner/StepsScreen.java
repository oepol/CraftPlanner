package dev.ole.craftplanner;
import dev.ole.craftplanner.core.CraftSteps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class StepsScreen extends Screen {
    private final CraftPlannerClient app; private final Screen parent;
    private int x,y,w,h,offset;
    public StepsScreen(CraftPlannerClient app,Screen parent) { super(Lang.text("craftplanner.steps")); this.app=app; this.parent=parent; }
    @Override protected void init() {
        w=Math.min(560,width-16); h=Math.min(380,height-16); x=(width-w)/2; y=(height-h)/2;
        addRenderableWidget(new Ui.Action(x+w-92,y+h-28,80,20,Lang.tr("craftplanner.back"),false,this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        var rows=CraftSteps.all(app.goals.plan()); int visible=Math.max(1,(h-88)/38); offset=Math.clamp(offset,0,Math.max(0,rows.size()-visible));
        g.fill(0,0,width,height,0xa0080d14); Ui.panel(g,x,y,w,h,0xf5111c28);
        Ui.text(g,Lang.tr("craftplanner.steps"),x+14,y+13,Ui.ACCENT);
        Ui.text(g,Ui.fit(Lang.tr("craftplanner.steps_help"),w-28),x+14,y+29,Ui.MUTED);
        if(rows.isEmpty())Ui.text(g,Lang.tr("craftplanner.steps_empty"),x+14,y+56,Ui.MUTED);
        for(int i=offset;i<Math.min(rows.size(),offset+visible);i++) {
            var s=rows.get(i); int ry=y+48+(i-offset)*38; Ui.panel(g,x+10,ry,w-20,34,0xff213140);
            g.item(ItemNames.icon(s.item()),x+16,ry+5);
            Ui.text(g,Ui.fit(ItemNames.name(s.item())+" · "+Lang.tr(s.kind()),w-60),x+40,ry+5,s.readyCrafts()>0?Ui.ACCENT:Ui.TEXT);
            Ui.text(g,Ui.fit(Lang.tr("craftplanner.steps_pending",s.crafts(),s.readyCrafts()),w-60),x+40,ry+19,Ui.MUTED);
            if(Ui.over(mx,my,x+10,ry,w-20,34)) {
                List<Component> tips=new ArrayList<>();
                s.inputs().forEach(in->tips.add(Component.literal(ItemNames.name(in.item())+": "+in.owned()+" / "+in.needed())));
                g.setComponentTooltipForNextFrame(font,tips,mx,my);
            }
        }
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) { offset=Math.max(0,offset-(int)Math.signum(vertical)*2); return true; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
