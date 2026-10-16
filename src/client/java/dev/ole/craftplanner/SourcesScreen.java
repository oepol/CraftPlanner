package dev.ole.craftplanner;
import dev.ole.craftplanner.core.ChestSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
public final class SourcesScreen extends Screen {
    private final CraftPlannerClient app; private final Screen parent;
    private int x,y,w,h,offset; private List<Map.Entry<String,ChestSource>> rows=List.of();
    public SourcesScreen(CraftPlannerClient app,Screen parent) { super(Lang.text("craftplanner.sources")); this.app=app; this.parent=parent; }
    @Override protected void init() {
        clearWidgets(); w=Math.min(560,width-16); h=Math.min(360,height-16); x=(width-w)/2; y=(height-h)/2;
        rows=new ArrayList<>(app.config.sources.entrySet()); offset=Math.clamp(offset,0,Math.max(0,rows.size()-visible()));
        addRenderableWidget(new Ui.Action(x+w-92,y+h-28,80,20,Lang.tr("craftplanner.back"),false,this::onClose));
        for(int i=offset;i<Math.min(rows.size(),offset+visible());i++) {
            String key=rows.get(i).getKey(); int ry=y+42+(i-offset)*54;
            addRenderableWidget(new Ui.Action(x+w-92,ry+4,80,20,Lang.tr("craftplanner.remove"),false,()->{app.sources.remove(key);init();}));
        }
    }
    private int visible() { return Math.max(1,(h-84)/54); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xa0080d14); Ui.panel(g,x,y,w,h,0xf5111c28); Ui.text(g,Lang.tr("craftplanner.sources"),x+14,y+14,Ui.ACCENT);
        if(rows.isEmpty())Ui.text(g,Ui.fit(Lang.tr("craftplanner.source_empty"),w-28),x+14,y+48,Ui.MUTED);
        for(int i=offset;i<Math.min(rows.size(),offset+visible());i++) {
            var e=rows.get(i); var s=e.getValue(); int ry=y+42+(i-offset)*54;
            Ui.panel(g,x+10,ry,w-20,50,0xff213140);
            String location=Lang.tr("craftplanner.source_location",s.dimension,s.partner==null?s.position:s.position+" / "+s.partner);
            String state=app.sources.isLive(e.getKey())?"source_live":!s.dimension.equals(app.sources.dimension())?"source_other_dimension":"source_cached";
            Ui.text(g,Ui.fit(location,w-122),x+16,ry+5,Ui.TEXT);
            Ui.text(g,Ui.fit(Lang.tr("craftplanner."+state),w-122),x+16,ry+19,Ui.MUTED);
            String date=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(s.observedAt).atZone(java.time.ZoneId.systemDefault()));
            Ui.text(g,Ui.fit(Lang.tr("craftplanner.source_count",s.lastStock.values().stream().mapToLong(Long::longValue).sum(),date),w-32),x+16,ry+34,Ui.MUTED);
            if(Ui.over(mx,my,x+10,ry,w-116,50)) {
                List<Component> tip=new ArrayList<>(); tip.add(Component.literal(location)); tip.add(Lang.text("craftplanner."+state));
                s.lastStock.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(12).forEach(item->tip.add(Component.literal(item.getValue()+" × "+ItemNames.name(item.getKey()))));
                g.setComponentTooltipForNextFrame(font,tip,mx,my);
            }
        }
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) { offset=Math.max(0,offset-(int)Math.signum(vertical)); init(); return true; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
