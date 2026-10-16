package dev.ole.craftplanner;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class HudEditorScreen extends Screen {
    private final CraftPlannerClient app;
    private final Screen parent;
    private boolean dragging;
    private double dx,dy;
    private int px,py,pw,page;
    public HudEditorScreen(CraftPlannerClient app,Screen parent) { super(Component.literal(Lang.tr("craftplanner.hud_editor"))); this.app=app; this.parent=parent; }
    @Override protected void init() {
        pw=Math.min(210,width-24); px=app.config.x>width/2 ? 12 : width-pw-12; py=12;
        addRenderableWidget(new Ui.Action(px+10,py+28,pw-20,20,Lang.tr("craftplanner.back"),true,this::onClose));
        String[] tabs={Lang.tr("craftplanner.size"),Lang.tr("craftplanner.position"),Lang.tr("craftplanner.display")}; int tw=(pw-20)/3;
        for(int i=0;i<3;i++) { int target=i; addRenderableWidget(new Ui.Action(px+10+i*tw,py+54,tw-2,20,tabs[i],page==i,() -> { page=target; rebuildWidgets(); })); }
        if(page==0) {
            slider(84,Lang.tr("craftplanner.scale"),.6,2,app.config.scale,v -> app.config.scale=v,v -> String.format(java.util.Locale.ROOT,"%.2f×",v));
            slider(114,Lang.tr("craftplanner.width"),180,480,app.config.width,v -> app.config.width=(int)v,v -> (int)v+" px");
            slider(144,Lang.tr("craftplanner.height"),100,900,app.config.maxHeight,v -> app.config.maxHeight=(int)v,v -> (int)v+" px");
            slider(174,Lang.tr("craftplanner.opacity"),.15,1,app.config.opacity,v -> app.config.opacity=v,v -> (int)(v*100)+"%");
        } else if(page==1) {
            int by=py+84;
            addRenderableWidget(new Ui.Action(px+10,by,(pw-25)/2,20,Lang.tr("craftplanner.top_left"),false,() -> anchor(false,false)));
            addRenderableWidget(new Ui.Action(px+15+(pw-25)/2,by,(pw-25)/2,20,Lang.tr("craftplanner.top_right"),false,() -> anchor(true,false)));
            addRenderableWidget(new Ui.Action(px+10,by+26,(pw-25)/2,20,Lang.tr("craftplanner.bottom_left"),false,() -> anchor(false,true)));
            addRenderableWidget(new Ui.Action(px+15+(pw-25)/2,by+26,(pw-25)/2,20,Lang.tr("craftplanner.bottom_right"),false,() -> anchor(true,true)));
            addRenderableWidget(new Ui.Action(px+10,by+56,pw-20,20,Lang.tr("craftplanner.reset_hud"),false,() -> { app.config.x=14; app.config.y=18; app.config.scale=1; app.config.width=224; app.config.maxHeight=340; app.config.opacity=.82; rebuildWidgets(); }));
        } else {
            addRenderableWidget(new Ui.Action(px+10,py+84,pw-20,20,Lang.tr("craftplanner.missing_only",Lang.state(app.config.hideCompleteMaterials)),false,() -> { app.config.hideCompleteMaterials=!app.config.hideCompleteMaterials; app.goals.invalidate(); rebuildWidgets(); }));
            addRenderableWidget(new Ui.Action(px+10,py+112,pw-20,20,Lang.tr("craftplanner.enabled",Lang.state(app.config.enabled)),false,() -> { app.config.enabled=!app.config.enabled; rebuildWidgets(); }));
            addRenderableWidget(new Ui.Action(px+10,py+140,pw-20,20,Lang.tr("craftplanner.notify",Lang.state(app.config.notifyComplete)),false,() -> { app.config.notifyComplete=!app.config.notifyComplete; rebuildWidgets(); }));
        }
    }
    private void slider(int ry,String name,double min,double max,double value,DoubleConsumer set,DoubleFunction<String> format) {
        addRenderableWidget(new AbstractSliderButton(px+10,py+ry,pw-20,22,Component.empty(),(value-min)/(max-min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal(Lang.tr("craftplanner.label_value",name,format.apply(min+this.value*(max-min))))); }
            @Override protected void applyValue() { set.accept(min+this.value*(max-min)); }
        });
    }
    private void anchor(boolean right,boolean bottom) {
        app.config.x=right ? Math.max(4,width-(int)(app.config.width*app.hud.scale())-14) : 14;
        app.config.y=bottom ? Math.max(4,height-(int)(app.hud.renderedHeight()*app.hud.scale())-14) : 18;
        rebuildWidgets();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x55070d14);
        app.hud.draw(g,mx,my,true);
        Ui.panel(g,px,py,pw,Math.min(height-24,208),0xf5111c28);
        Ui.text(g,Lang.tr("craftplanner.drag_hud"),px+10,py+12,Ui.ACCENT);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        if (super.mouseClicked(e,twice)) return true;
        if (e.button()==InputConstants.MOUSE_BUTTON_LEFT && !Ui.over(e.x(),e.y(),px,py,pw,208) && Ui.over(e.x(),e.y(),app.hud.x(width),app.hud.y(height),(int)(app.config.width*app.hud.scale()),(int)(app.hud.renderedHeight()*app.hud.scale()))) {
            dragging=true; dx=e.x()-app.hud.x(width); dy=e.y()-app.hud.y(height); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double deltaX,double deltaY) {
        if (!dragging) return super.mouseDragged(e,deltaX,deltaY);
        app.config.x=Math.clamp((int)(e.x()-dx),4,Math.max(4,width-(int)(app.config.width*app.hud.scale())-4));
        app.config.y=Math.clamp((int)(e.y()-dy),4,Math.max(4,height-(int)(app.hud.renderedHeight()*app.hud.scale())-4)); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e) { dragging=false; return super.mouseReleased(e); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
