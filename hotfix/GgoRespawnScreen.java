package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** GGO-owned death recap. Vanilla Minecraft death UI never reaches the player. */
public final class GgoRespawnScreen extends Screen {
    public GgoRespawnScreen(){super(Component.literal("COMBAT REPORT"));GgoDeathRecapAdapter.install();}

    @Override protected void init(){
        int x=width/2-110,y=height-54;
        addRenderableWidget(Button.builder(Component.literal("RESPAWN"),b->respawn()).bounds(x,y,220,24).build());
        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"),b->Minecraft.getInstance().setScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES))).bounds(x+228,y,120,24).build());
    }

    private void respawn(){Minecraft mc=Minecraft.getInstance();if(mc.player!=null)mc.player.respawn();GgoDeathRecapState.clear();mc.setScreen(null);}

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        g.fill(0,0,width,height,0xF5080A0E);g.fill(0,0,width,4,0xFFD13B48);
        int margin=Math.max(22,width/18),top=36,bottom=height-78;
        g.drawString(font,"GUNGLORYONLINE",margin,16,0xFF7C8797,false);
        g.drawString(font,"COMBAT REPORT",margin,top,0xFFF1F3F6,false);
        g.drawString(font,"KIA",width-margin-font.width("KIA"),top,0xFFE24958,false);

        int leftW=Math.max(220,(width-margin*2)/3),gap=12;
        int rightX=margin+leftW+gap,rightW=width-margin-rightX;
        panel(g,margin,top+26,leftW,bottom-top-26);
        panel(g,rightX,top+26,rightW,bottom-top-26);

        var s=GgoDeathRecapState.get();
        if(s==null){
            g.drawString(font,"SYNCING AUTHORITATIVE COMBAT DATA...",margin+16,top+48,0xFF7F8A99,false);
            g.drawString(font,"RESPAWN remains available while the report arrives.",margin+16,top+66,0xFF596575,false);
        }else{
            int y=top+48;
            label(g,"KILLED BY",margin+16,y);y+=18;
            g.drawString(font,s.killer(),margin+16,y,0xFFF2F4F7,false);y+=30;
            label(g,"WEAPON",margin+16,y);y+=16;
            g.drawString(font,s.weapon(),margin+16,y,0xFFD6DCE4,false);y+=30;
            label(g,"FINAL HIT",margin+16,y);y+=16;
            String damage=s.finalDamage()>0?String.format(java.util.Locale.ROOT,"%.1f DAMAGE",s.finalDamage()):s.source().toUpperCase(java.util.Locale.ROOT);
            g.drawString(font,damage,margin+16,y,0xFFE2A15A,false);y+=30;
            label(g,"DISTANCE",margin+16,y);y+=16;
            g.drawString(font,s.distance()>=0?String.format(java.util.Locale.ROOT,"%.1f m",s.distance()):"—",margin+16,y,0xFFC7CED8,false);y+=30;
            label(g,"SECTOR",margin+16,y);y+=16;
            g.drawString(font,s.sector(),margin+16,y,0xFFC7CED8,false);

            int rx=rightX+18,ry=top+48;
            label(g,"ATTACKER STATUS",rx,ry);ry+=22;
            if(s.killerHealth()>=0&&s.killerMaxHealth()>0){
                float pct=Math.max(0f,Math.min(1f,s.killerHealth()/s.killerMaxHealth()));
                g.fill(rx,ry,rightX+rightW-18,ry+8,0xFF252D37);
                g.fill(rx,ry,rx+Math.round((rightW-36)*pct),ry+8,0xFFC43A47);
                ry+=16;g.drawString(font,Math.round(s.killerHealth())+" / "+Math.round(s.killerMaxHealth())+" HP",rx,ry,0xFFE5E8EC,false);ry+=34;
            }else{g.drawString(font,"NO LIVING ATTACKER",rx,ry,0xFF687483,false);ry+=34;}
            label(g,"SOURCE",rx,ry);ry+=17;
            g.drawString(font,s.source().toUpperCase(java.util.Locale.ROOT),rx,ry,0xFF9AA5B4,false);ry+=34;
            label(g,"REPORT",rx,ry);ry+=17;
            g.drawString(font,"Server-authoritative final-hit data",rx,ry,0xFF667281,false);ry+=16;
            g.drawString(font,"No guessed headshot/body-part information",rx,ry,0xFF667281,false);
        }
        g.drawString(font,"ESC disabled during KIA state",margin,height-26,0xFF566170,false);
        super.render(g,mouseX,mouseY,partialTick);
    }

    private void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x,y,x+w,y+h,0xD70D1117);g.fill(x,y,x+3,y+h,0xFFC03643);g.fill(x+3,y,x+w,y+1,0xFF303844);}
    private void label(GuiGraphics g,String text,int x,int y){g.drawString(font,text,x,y,0xFF778394,false);}
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public boolean isPauseScreen(){return false;}
}
