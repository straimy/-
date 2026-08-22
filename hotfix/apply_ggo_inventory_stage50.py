#!/usr/bin/env python3
from pathlib import Path

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
SCREEN=ROOT/"client-ui/src/main/java/arena/client/shell/GgoShellScreen.java"
if not SCREEN.is_file(): raise SystemExit("GgoShellScreen.java missing")
s=SCREEN.read_text(encoding="utf-8")

start=s.find("    private void initInventoryActions() {")
end=s.find("\n    private void open(Page next)",start)
if start<0 or end<0: raise SystemExit("stage50 inventory actions block missing")
actions=r'''    private void initInventoryActions() {
        int bw=142,gap=7;
        int x=Math.max(24,this.width-24-bw*2-gap);
        int y=Math.max(92,this.height-72);
        addRenderableWidget(Button.builder(Component.literal("AUTO-SORT AMMO"),b->runClientCommand("ggoinv ammo")).bounds(x,y,bw,22).build());
        addRenderableWidget(Button.builder(Component.literal("DROP SELECTED"),b->{if(selectedInventorySlot>=0){runClientCommand("ggoinv drop "+selectedInventorySlot);selectedInventorySlot=-1;}}).bounds(x+bw+gap,y,bw,22).build());
    }
'''
s=s[:start]+actions+s[end:]

start=s.find("    private void renderInventory(GuiGraphics g, Minecraft mc) {")
end=s.find("\n    private void renderActivities(GuiGraphics g)",start)
if start<0 or end<0: raise SystemExit("stage50 inventory render block missing")
inventory=r'''    private void renderInventory(GuiGraphics g, Minecraft mc) {
        int left=24,top=76,bottom=Math.max(top+320,this.height-90);
        int loadoutW=Math.max(250,Math.min(310,this.width/3));
        int fieldX=left+loadoutW+14,fieldW=Math.max(360,this.width-fieldX-24);
        panel(g,left,top,loadoutW,bottom-top,"EQUIPMENT");
        panel(g,fieldX,top,fieldW,bottom-top,"BACKPACK / FIELD LOOT");

        g.drawString(this.font,mc.player.getGameProfile().getName(),left+14,top+30,0xFFF2F4F7,false);
        g.drawString(this.font,"HP  "+Math.round(mc.player.getHealth())+" / "+Math.round(mc.player.getMaxHealth())+"   ARMOR  "+mc.player.getArmorValue(),left+14,top+47,0xFF8F9BAB,false);
        g.drawString(this.font,"COMBAT LOADOUT",left+14,top+72,0xFF778496,false);

        int combatX=left+14,combatY=top+90;
        String[] labels={"PRIMARY","SECONDARY","SIDEARM"};
        for(int i=0;i<3;i++){
            int y=combatY+i*45;boolean active=mc.player.getInventory().selected==i;
            ggoSlot(g,combatX,y,34,active||selectedInventorySlot==i);
            renderStack(g,mc.player.getInventory().getItem(i),combatX+9,y+9);
            g.drawString(this.font,labels[i],combatX+44,y+6,active?0xFFE14A58:0xFFA8B2BF,false);
            ItemStack st=mc.player.getInventory().getItem(i);
            String name=st.isEmpty()?"EMPTY":st.getHoverName().getString();if(name.length()>22)name=name.substring(0,22);
            g.drawString(this.font,name,combatX+44,y+20,0xFF687586,false);
        }

        int armorY=combatY+146;
        g.drawString(this.font,"PROTECTED GEAR",left+14,armorY,0xFF778496,false);
        String[] armorLabels={"HELMET","VEST","LEGS","BOOTS"};
        int[] armorIndex={3,2,1,0};
        for(int i=0;i<4;i++){
            int col=i%2,row=i/2,x=left+14+col*((loadoutW-42)/2),y=armorY+18+row*42;
            ggoSlot(g,x,y,30,false);ItemStack st=mc.player.getInventory().armor.get(armorIndex[i]);renderStack(g,st,x+7,y+7);
            g.drawString(this.font,armorLabels[i],x+38,y+10,0xFF8995A5,false);
        }

        int x0=fieldX+16;
        g.drawString(this.font,"AMMO POUCH  //  PROTECTED ON KIA",x0,top+30,0xFF8995A5,false);
        for(int i=0;i<9;i++){
            int slot=9+i,x=x0+i*34,y=top+48;ggoSlot(g,x,y,30,selectedInventorySlot==slot);renderStack(g,mc.player.getInventory().getItem(slot),x+7,y+7);
        }

        int supplies=0,bags=0,meds=0;
        for(int i=18;i<=35;i++){
            ItemStack st=mc.player.getInventory().getItem(i);if(st==null||st.isEmpty())continue;
            if(st.hasTag()&&st.getTag()!=null){if(st.getTag().getBoolean("ggo_supply"))supplies+=st.getCount();if(st.getTag().getBoolean("GgoRecoveryBag"))bags++;if(st.getTag().contains("GgoMedicine"))meds+=st.getCount();}
        }
        int fieldY=top+126;
        g.drawString(this.font,"FIELD ITEMS",x0,fieldY-20,0xFFB4BEC9,false);
        g.drawString(this.font,"DROP ON KIA → ONE SEALED RECOVERY BAG",x0+96,fieldY-20,0xFFD89055,false);
        for(int i=18;i<=35;i++){
            int n=i-18,col=n%9,row=n/9,x=x0+col*34,y=fieldY+row*34;
            ItemStack st=mc.player.getInventory().getItem(i);boolean bag=st!=null&&!st.isEmpty()&&st.hasTag()&&st.getTag().getBoolean("GgoRecoveryBag");
            ggoSlot(g,x,y,30,selectedInventorySlot==i||bag);renderStack(g,st,x+7,y+7);
            if(bag)g.renderOutline(x,y,30,30,0xFFE0A64A);
        }
        g.drawString(this.font,"SUPPLIES  "+supplies+"   MEDICAL  "+meds+"   RECOVERY BAGS  "+bags,x0,fieldY+78,0xFF718093,false);
        String medicalKey=GgoKeyMappings.MEDICAL_WHEEL.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        g.drawString(this.font,"LMB select / move inside section     RMB drop     "+medicalKey+" hold medical wheel",x0,Math.min(bottom-28,fieldY+104),0xFF667384,false);
        g.drawString(this.font,"Crafting, recipe book and Minecraft armor inventory are not exposed.",x0,Math.min(bottom-14,fieldY+118),0xFF566273,false);
    }

    private void ggoSlot(GuiGraphics g,int x,int y,int size,boolean selected){
        g.fill(x,y,x+size,y+size,selected?0xFFD34B57:0xFF283341);g.fill(x+1,y+1,x+size-1,y+size-1,0xFF0B1016);if(selected)g.renderOutline(x,y,size,size,0xFFE45A67);
    }

    private int inventorySlotAt(double mouseX,double mouseY){
        int left=24,top=76,loadoutW=Math.max(250,Math.min(310,this.width/3)),fieldX=left+loadoutW+14;
        int combatX=left+14,combatY=top+90;
        for(int i=0;i<3;i++){int y=combatY+i*45;if(mouseX>=combatX&&mouseX<combatX+34&&mouseY>=y&&mouseY<y+34)return i;}
        int x0=fieldX+16,ammoY=top+48;
        for(int i=0;i<9;i++){int x=x0+i*34;if(mouseX>=x&&mouseX<x+30&&mouseY>=ammoY&&mouseY<ammoY+30)return 9+i;}
        int fieldY=top+126;
        for(int i=18;i<=35;i++){int n=i-18,col=n%9,row=n/9,x=x0+col*34,y=fieldY+row*34;if(mouseX>=x&&mouseX<x+30&&mouseY>=y&&mouseY<y+30)return i;}
        return -1;
    }

    private static boolean sameInventoryCompartment(int a,int b){
        boolean aa=a>=9&&a<=17,ab=b>=9&&b<=17;if(aa||ab)return aa&&ab;
        boolean fa=a>=18&&a<=35,fb=b>=18&&b<=35;return fa&&fb;
    }
'''
s=s[:start]+inventory+s[end:]
for required in ["EQUIPMENT","BACKPACK / FIELD LOOT","PROTECTED GEAR","DROP ON KIA","RECOVERY BAGS","GgoKeyMappings.MEDICAL_WHEEL","AUTO-SORT AMMO"]:
    if required not in s: raise SystemExit(f"stage50 inventory UX missing: {required}")
SCREEN.write_text(s,encoding="utf-8")
print("Applied GGO Stage 50 equipment/field inventory UX")
