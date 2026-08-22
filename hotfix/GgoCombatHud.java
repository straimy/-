package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** PUBG/STALCRAFT-inspired GGO combat presentation without a Minecraft hotbar. */
@Mod.EventBusSubscriber(value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoCombatHud {
    private static boolean medicineHeld;
    private static int selectedMedicineSlot=-1;
    private GgoCombatHud(){}

    @SubscribeEvent public static void hideVanillaHud(RenderGuiOverlayEvent.Pre event){
        var id=event.getOverlay().id();
        if(id.equals(VanillaGuiOverlay.HOTBAR.id())||id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                ||id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())||id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                ||id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())||id.equals(VanillaGuiOverlay.AIR_LEVEL.id()))event.setCanceled(true);
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null){medicineHeld=false;selectedMedicineSlot=-1;return;}
        boolean down=GLFW.glfwGetKey(mc.getWindow().getWindow(),GLFW.GLFW_KEY_H)==GLFW.GLFW_PRESS;
        if(down){
            medicineHeld=true;
            while(mc.options.keyAttack.consumeClick()){}
            while(mc.options.keyUse.consumeClick()){}
            selectedMedicineSlot=medicineSelection(mc);
        }else if(medicineHeld){
            medicineHeld=false;
            if(selectedMedicineSlot>=18&&selectedMedicineSlot<=35&&mc.getConnection()!=null){
                mc.getConnection().sendCommand("ggomed use "+selectedMedicineSlot);
            }
            selectedMedicineSlot=-1;
        }
    }

    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Post event){
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null)return;
        GuiGraphics g=event.getGuiGraphics();
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
        renderVitals(g,mc,h);
        renderWeaponPanel(g,mc,w,h);
        renderWorldStatus(g,mc,w);
        if(medicineHeld)renderMedicineWheel(g,mc,w,h);
        else renderMedicineHint(g,mc,w,h);
    }

    private static void renderVitals(GuiGraphics g,Minecraft mc,int height){
        int x=20,y=height-66,w=216,h=44;
        panel(g,x,y,w,h,0xC9080C11);
        float health=mc.player.getHealth(),max=Math.max(1f,mc.player.getMaxHealth());
        float hp=Math.max(0f,Math.min(1f,health/max));
        int armor=mc.player.getArmorValue();
        g.drawString(mc.font,"HP",x+10,y+8,0xFF8F9AAA,false);
        g.fill(x+34,y+9,x+150,y+15,0xFF242A32);
        g.fill(x+34,y+9,x+34+Math.round(116*hp),y+15,0xFFCE3948);
        g.drawString(mc.font,Math.round(health)+" / "+Math.round(max),x+158,y+7,0xFFF0F2F5,false);
        g.drawString(mc.font,"ARMOR  "+armor,x+10,y+26,0xFFC1CAD5,false);
        g.drawString(mc.font,"COMBAT READY",x+116,y+26,0xFF687484,false);
    }

    private static void renderWeaponPanel(GuiGraphics g,Minecraft mc,int width,int height){
        int panelW=300,panelH=78,x=width-panelW-20,y=height-panelH-18;
        panel(g,x,y,panelW,panelH,0xD0080C11);
        ItemStack held=mc.player.getMainHandItem();
        GgoWeaponTelemetry.Snapshot t=GgoWeaponTelemetry.current(mc);
        String name=t.weaponName();if(name.length()>28)name=name.substring(0,25)+"...";
        g.drawString(mc.font,name.toUpperCase(Locale.ROOT),x+12,y+10,0xFFF1F3F6,false);
        String ammo=GgoWeaponTelemetry.ammoText(t);
        g.drawString(mc.font,ammo,x+12,y+30,0xFFF5F6F8,false);
        String mode=t.reloading()?"RELOADING":(t.available()?t.fireMode():"--");
        g.drawString(mc.font,mode,x+82,y+30,t.reloading()?0xFFE2A64D:0xFF8490A0,false);
        if(held!=null&&!held.isEmpty())g.renderItem(held,x+panelW-31,y+7);

        int selected=mc.player.getInventory().selected;
        int sx=x+12,sy=y+50;
        for(int i=0;i<3;i++){
            ItemStack s=mc.player.getInventory().getItem(i);
            int sw=82,ix=sx+i*(sw+6);
            int border=selected==i?0xFFD13C49:0xFF2E3742;
            g.fill(ix,sy,ix+sw,sy+20,border);g.fill(ix+1,sy+1,ix+sw-1,sy+19,0xE00C1117);
            g.drawString(mc.font,String.valueOf(i+1),ix+5,sy+6,selected==i?0xFFFFFFFF:0xFF707C8D,false);
            if(!s.isEmpty()){g.renderItem(s,ix+19,sy+2);String n=s.getHoverName().getString();if(n.length()>8)n=n.substring(0,8);g.drawString(mc.font,n,ix+38,sy+6,0xFFB7C0CC,false);}
            else g.drawString(mc.font,"EMPTY",ix+24,sy+6,0xFF566171,false);
        }
    }

    private static void renderWorldStatus(GuiGraphics g,Minecraft mc,int width){
        int ping=0;if(mc.getConnection()!=null&&mc.getConnection().getPlayerInfo(mc.player.getUUID())!=null)ping=mc.getConnection().getPlayerInfo(mc.player.getUUID()).getLatency();
        String sector=sectorFor(mc.player.getBlockX(),mc.player.getBlockZ());
        String status="GGO // "+sector+" // "+ping+" ms";
        var waypoint=GgoNavigationState.waypoint();
        if(waypoint!=null)status+=" // "+waypoint.label()+" "+(int)Math.round(GgoNavigationState.distanceTo(mc.player.getX(),mc.player.getY(),mc.player.getZ()))+"m";
        g.drawString(mc.font,status,width-mc.font.width(status)-18,14,0xFF808B9A,false);
    }

    private static void renderMedicineHint(GuiGraphics g,Minecraft mc,int width,int height){
        List<Integer> meds=medicineSlots(mc);
        String text=meds.isEmpty()?"H  MEDICAL — EMPTY":"H  MEDICAL  "+meds.size();
        int x=width-300,y=height-112;
        g.drawString(mc.font,text,x,y,meds.isEmpty()?0xFF596373:0xFF8CA693,false);
    }

    private static void renderMedicineWheel(GuiGraphics g,Minecraft mc,int width,int height){
        List<Integer> meds=medicineSlots(mc);
        int cx=width/2,cy=height/2;
        g.fill(0,0,width,height,0x66000000);
        g.fill(cx-42,cy-42,cx+42,cy+42,0xDC0A0E13);
        g.drawCenteredString(mc.font,"MEDICAL",cx,cy-7,0xFFF0F3F6);
        g.drawCenteredString(mc.font,"RELEASE H",cx,cy+8,0xFF737F90);
        if(meds.isEmpty()){g.drawCenteredString(mc.font,"NO MEDICINE",cx,cy+58,0xFFD14A56);return;}
        int shown=Math.min(6,meds.size());
        for(int i=0;i<shown;i++){
            double a=-Math.PI/2.0+(Math.PI*2.0*i/shown);
            int bx=cx+(int)Math.round(Math.cos(a)*104)-38;
            int by=cy+(int)Math.round(Math.sin(a)*72)-18;
            int slot=meds.get(i);boolean selected=slot==selectedMedicineSlot;
            g.fill(bx,by,bx+76,by+36,selected?0xFFD13B48:0xFF27303B);
            g.fill(bx+1,by+1,bx+75,by+35,0xED0A0F15);
            ItemStack s=mc.player.getInventory().getItem(slot);g.renderItem(s,bx+7,by+10);
            String name=s.getHoverName().getString();if(name.length()>9)name=name.substring(0,9);
            g.drawString(mc.font,name,bx+27,by+8,selected?0xFFFFFFFF:0xFFC0C8D2,false);
            g.drawString(mc.font,"x"+s.getCount(),bx+27,by+21,0xFF7F8B99,false);
        }
    }

    private static int medicineSelection(Minecraft mc){
        List<Integer> meds=medicineSlots(mc);int n=Math.min(6,meds.size());if(n==0)return -1;
        double mx=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/Math.max(1,mc.getWindow().getScreenWidth());
        double my=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/Math.max(1,mc.getWindow().getScreenHeight());
        double dx=mx-mc.getWindow().getGuiScaledWidth()/2.0,dy=my-mc.getWindow().getGuiScaledHeight()/2.0;
        if(dx*dx+dy*dy<900)return -1;
        double angle=Math.atan2(dy,dx)+Math.PI/2.0;if(angle<0)angle+=Math.PI*2.0;
        int index=(int)Math.floor((angle+Math.PI/n)/(Math.PI*2.0)*n)%n;
        return meds.get(index);
    }

    private static List<Integer> medicineSlots(Minecraft mc){
        List<Integer> out=new ArrayList<>();if(mc.player==null)return out;
        for(int i=18;i<=35;i++){ItemStack s=mc.player.getInventory().getItem(i);if(isMedicine(s))out.add(i);}
        return out;
    }

    private static boolean isMedicine(ItemStack s){
        if(s==null||s.isEmpty())return false;
        if(s.hasTag()&&s.getTag().contains("GgoMedicine"))return true;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(s.getItem());if(id==null)return false;
        String p=id.getPath().toLowerCase(Locale.ROOT);
        return p.contains("bandage")||p.contains("medkit")||p.contains("first_aid")||p.contains("firstaid")||p.contains("syringe")||p.contains("stim")||p.contains("injector");
    }

    private static void panel(GuiGraphics g,int x,int y,int w,int h,int fill){g.fill(x,y,x+w,y+h,fill);g.fill(x,y,x+3,y+h,0xFFB92F3C);g.fill(x+3,y,x+w,y+1,0xFF2A333E);}
    private static String sectorFor(int x,int z){int sx=Math.floorDiv(x,256),sz=Math.floorDiv(z,256);char col=(char)('A'+Math.floorMod(sx,26));return col+"-"+Math.abs(sz);}
}
