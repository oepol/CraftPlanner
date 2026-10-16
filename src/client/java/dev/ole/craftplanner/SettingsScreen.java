package dev.ole.craftplanner;
import com.mojang.blaze3d.platform.InputConstants;
import dev.ole.craftplanner.core.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
public final class SettingsScreen extends Screen {
    private final CraftPlannerClient app; private final Screen parent;
    private final PlannerConfig original; private final String originalMenu,originalToggle;
    private int x,y,w,h,page; private boolean finished,error;
    public SettingsScreen(CraftPlannerClient app,Screen parent) {
        super(Lang.text("craftplanner.settings")); this.app=app; this.parent=parent;
        if(app.settings!=null)app.settings.cancel(false);
        original=app.config.settingsCopy(); originalMenu=KeybindManager.menu.saveString(); originalToggle=KeybindManager.toggle.saveString(); app.settings=this;
    }
    PlannerConfig originalSettings() { return original; }
    @Override protected void init() {
        clearWidgets(); w=Math.min(420,width-16); h=Math.min(238,height-16); x=(width-w)/2; y=(height-h)/2;
        int tw=(w-24)/3; String[] tabs={"general","planning","controls"};
        for(int i=0;i<3;i++) { int p=i; button(x+10+i*tw,y+36,tw-2,tabs[i],page==i,()->{page=p;init();},null); }
        if(page==0) {
            toggle(64,"enabled",app.config.enabled,()->app.config.enabled=!app.config.enabled,null);
            toggle(89,"notify",app.config.notifyComplete,()->app.config.notifyComplete=!app.config.notifyComplete,"notify_tip");
            button(x+12,y+114,w-24,"hud_editor",false,()->minecraft.gui.setScreen(new HudEditorScreen(app,this)),null);
            button(x+12,y+139,w-24,"language",false,()->minecraft.gui.setScreen(new LanguageSelectScreen(this,minecraft.options,minecraft.getLanguageManager())),"language_tip");
        } else if(page==1) {
            toggle(64,"include_shulkers",app.config.includeShulkers,()->app.config.includeShulkers=!app.config.includeShulkers,"include_shulkers_tip");
            toggle(89,"missing_only",app.config.hideCompleteMaterials,()->app.config.hideCompleteMaterials=!app.config.hideCompleteMaterials,"missing_only_tip");
            add(x+12,y+114,w-24,Lang.tr("craftplanner.sort",Lang.tr("craftplanner.sort."+app.config.sort)),false,()->{app.config.sort=switch(app.config.sort){case "missing"->"name";case "name"->"progress";default->"missing";};preview();},"sort_tip");
            add(x+12,y+139,w-80,Lang.tr("craftplanner.pin_limit",app.config.pinLimit),false,()->limit(1),"pin_limit_tip");
            add(x+w-62,y+139,24,"−",false,()->limit(-1),"pin_limit_tip"); add(x+w-36,y+139,24,"+",false,()->limit(1),"pin_limit_tip");
        } else button(x+12,y+114,w-24,"keybinds",false,()->minecraft.gui.setScreen(new KeyBindsScreen(this,minecraft.options)),"keybinds_tip");
        int fw=(w-32)/3;
        button(x+12,y+h-30,fw,"defaults",false,this::defaults,null);
        button(x+16+fw,y+h-30,fw,"cancel",false,this::onClose,null);
        button(x+20+fw*2,y+h-30,fw,"save",true,this::commit,null);
    }
    private void limit(int delta) {
        int next=Math.clamp(app.config.pinLimit+delta,1,12);
        if(next<Pins.count(app.config.goals)) { if(minecraft.player!=null)minecraft.player.sendOverlayMessage(Lang.text("craftplanner.pin_min")); return; }
        app.config.pinLimit=next; preview();
    }
    private void toggle(int ry,String key,boolean value,Runnable run,String tip) { add(x+12,y+ry,w-24,Lang.tr("craftplanner."+key,Lang.state(value)),false,()->{run.run();preview();},tip); }
    private void button(int bx,int by,int bw,String key,boolean accent,Runnable run,String tip) { add(bx,by,bw,Lang.tr("craftplanner."+key),accent,run,tip); }
    private void add(int bx,int by,int bw,String label,boolean accent,Runnable run,String tip) {
        var b=new Ui.Action(bx,by,bw,20,label,accent,run); if(tip!=null)b.setTooltip(Tooltip.create(Lang.text("craftplanner."+tip))); addRenderableWidget(b);
    }
    private void preview() { app.config.sanitize(); app.goals.invalidate(); error=false; init(); }
    private void defaults() {
        app.config.copySettingsFrom(new PlannerConfig()); app.config.pinLimit=(int)Math.max(3,Pins.count(app.config.goals));
        KeybindManager.menu.setKey(KeybindManager.menu.getDefaultKey()); KeybindManager.toggle.setKey(KeybindManager.toggle.getDefaultKey()); KeyMapping.resetMapping(); preview();
    }
    private void commit() { app.settings=null; if(!app.save()){app.settings=this;error=true;return;} minecraft.options.save(); finished=true; minecraft.gui.setScreen(parent); }
    void cancel(boolean navigate) {
        if(finished)return; app.config.copySettingsFrom(original);
        KeybindManager.menu.setKey(InputConstants.getKey(originalMenu)); KeybindManager.toggle.setKey(InputConstants.getKey(originalToggle)); KeyMapping.resetMapping();
        net.minecraft.client.Minecraft.getInstance().options.save(); app.settings=null; app.goals.invalidate(); finished=true;
        if(navigate)minecraft.gui.setScreen(parent);
    }
    @Override public void onClose() { cancel(true); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xa0080d14); Ui.panel(g,x,y,w,h,0xf5111c28); Ui.text(g,Lang.tr("craftplanner.title"),x+14,y+13,Ui.ACCENT);
        if(page==2) {
            Ui.text(g,Ui.fit(Lang.tr("craftplanner.key_label",Lang.tr("key.craftplanner.menu"),KeybindManager.menuLabel()),w-28),x+14,y+72,Ui.TEXT);
            Ui.text(g,Ui.fit(Lang.tr("craftplanner.key_label",Lang.tr("key.craftplanner.toggle"),KeybindManager.toggleLabel()),w-28),x+14,y+94,Ui.TEXT);
            Ui.text(g,Ui.fit(Lang.tr("craftplanner.keys_live"),w-28),x+14,y+146,Ui.MUTED);
        }
        Ui.text(g,Ui.fit(Lang.tr(error?"craftplanner.save_error":"craftplanner.preview"),w-28),x+14,y+h-46,error?Ui.WARN:Ui.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
}
