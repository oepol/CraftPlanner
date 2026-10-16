package dev.ole.craftplanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class Ui {
    public static final int TEXT=0xffedf4ff, MUTED=0xff9baabd, ACCENT=0xff72e5c0, WARN=0xffffbd7b;
    private Ui() {}
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,int bg) {
        g.fill(x+3,y,x+w-3,y+h,bg); g.fill(x,y+3,x+w,y+h-3,bg);
        g.fill(x+3,y,x+w-3,y+1,0x485f789a); g.fill(x,y+3,x+1,y+h-3,0x285f789a);
    }
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color) { g.text(Minecraft.getInstance().font,text,x,y,color,false); }
    public static String fit(String value,int width) {
        var font=Minecraft.getInstance().font;
        return font.width(value)<=width ? value : font.plainSubstrByWidth(value,Math.max(0,width-10))+"…";
    }
    public static void bar(GuiGraphicsExtractor g,int x,int y,int w,double value) {
        g.fill(x,y,x+w,y+3,0xff293746);
        g.fill(x,y,x+(int)Math.round(w*Math.clamp(value,0,1)),y+3,ACCENT);
    }
    public static boolean over(double mx,double my,int x,int y,int w,int h) { return mx>=x && my>=y && mx<x+w && my<y+h; }
    public static final class Action extends Button {
        private final boolean accent;
        public Action(int x,int y,int w,int h,String label,boolean accent,Runnable run) {
            super(x,y,w,h,Component.literal(label),b -> run.run(),DEFAULT_NARRATION); this.accent=accent; setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
            panel(g,getX(),getY(),getWidth(),getHeight(),!active ? 0xff17212b : isHoveredOrFocused() ? 0xff354c60 : accent ? 0xff244d46 : 0xff233140);
            g.centeredText(Minecraft.getInstance().font,fit(getMessage().getString(),getWidth()-8),getX()+getWidth()/2,getY()+(getHeight()-8)/2,!active ? MUTED : accent ? ACCENT : TEXT);
        }
    }
}
