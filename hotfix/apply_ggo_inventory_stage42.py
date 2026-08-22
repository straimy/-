#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SCREEN = JAVA / "GgoShellScreen.java"

if not SCREEN.is_file():
    raise SystemExit("GgoShellScreen.java is missing")

s = SCREEN.read_text(encoding="utf-8")

field = "    private final Page page;\n"
if field not in s:
    raise SystemExit("stage42 page field marker missing")
if "selectedInventorySlot" not in s:
    s = s.replace(field, field + "    private int selectedInventorySlot=-1;\n", 1)

init_anchor = "    protected void init() {\n"
if init_anchor not in s:
    raise SystemExit("stage42 init marker missing")
if "initInventoryActions();" not in s:
    s = s.replace(init_anchor, init_anchor + "        if(page==Page.INVENTORY)initInventoryActions();\n", 1)

open_anchor = "    private void open(Page next) {\n"
if open_anchor not in s:
    raise SystemExit("stage42 open marker missing")
if "private void initInventoryActions()" not in s:
    actions = r'''    private void initInventoryActions() {
        int gap=6;
        int bw=Math.max(90,Math.min(132,(this.width-48-gap*3)/4));
        int total=bw*4+gap*3;
        int x=Math.max(24,(this.width-total)/2);
        int y=Math.max(92,this.height-76);
        addRenderableWidget(Button.builder(Component.literal("СОБРАТЬ ПАТРОНЫ"),b->runClientCommand("ggoinv ammo")).bounds(x,y,bw,22).build());
        addRenderableWidget(Button.builder(Component.literal("ВЫБРОСИТЬ"),b->{if(selectedInventorySlot>=0){runClientCommand("ggoinv drop "+selectedInventorySlot);selectedInventorySlot=-1;}}).bounds(x+bw+gap,y,bw,22).build());
        addRenderableWidget(Button.builder(Component.literal("ПАТРОНЫ ↓"),b->runClientCommand("ggoinv dropammo")).bounds(x+(bw+gap)*2,y,bw,22).build());
        addRenderableWidget(Button.builder(Component.literal("МУСОР ↓"),b->runClientCommand("ggoinv clear")).bounds(x+(bw+gap)*3,y,bw,22).build());
    }

'''
    s = s.replace(open_anchor, actions + open_anchor, 1)

start = s.find("    private void renderInventory(GuiGraphics g, Minecraft mc) {")
end = s.find("\n    private void renderActivities(GuiGraphics g)", start)
if start < 0 or end < 0:
    raise SystemExit("stage42 inventory render block missing")

inventory = r'''    private void renderInventory(GuiGraphics g, Minecraft mc) {
        int left=24,top=82;
        int panelH=Math.max(210,this.height-144);
        int profileW=Math.max(190,Math.min(240,this.width/4));
        int bagX=left+profileW+14;
        int bagW=Math.max(330,this.width-bagX-24);

        panel(g,left,top,profileW,panelH,"LOADOUT");
        panel(g,bagX,top,bagW,panelH,"FIELD INVENTORY");

        g.drawString(this.font,mc.player.getGameProfile().getName(),left+14,top+32,0xFFF1F3F7,false);
        g.drawString(this.font,"HP  "+Math.round(mc.player.getHealth())+" / "+Math.round(mc.player.getMaxHealth()),left+14,top+50,0xFF78D49A,false);
        g.drawString(this.font,"ARMOR  "+mc.player.getArmorValue(),left+14,top+65,0xFF94A1B4,false);
        g.drawString(this.font,"COMBAT",left+14,top+88,0xFF7F8B9E,false);

        int combatX=left+14,combatY=top+104;
        String[] labels={"PRIMARY","SECONDARY","SIDEARM"};
        for(int i=0;i<3;i++){
            int y=combatY+i*48;
            boolean active=mc.player.getInventory().selected==i;
            ggoSlot(g,combatX,y,36,active||selectedInventorySlot==i);
            renderStack(g,mc.player.getInventory().getItem(i),combatX+10,y+10);
            g.drawString(this.font,labels[i],combatX+46,y+7,active?0xFFD94B58:0xFF9AA6B7,false);
            g.drawString(this.font,active?"ACTIVE":"CLICK TO SELECT",combatX+46,y+21,active?0xFF78D49A:0xFF5E6A7B,false);
        }

        int ammoX=bagX+16,ammoY=top+48;
        g.drawString(this.font,"AMMO POUCH  •  9",ammoX,top+30,0xFF9AA6B7,false);
        for(int i=0;i<9;i++){
            int slot=9+i,x=ammoX+i*34;
            ggoSlot(g,x,ammoY,30,selectedInventorySlot==slot);
            renderStack(g,mc.player.getInventory().getItem(slot),x+7,ammoY+7);
        }

        int fieldY=top+124;
        int supplies=0;
        for(int i=18;i<=35;i++){
            ItemStack st=mc.player.getInventory().getItem(i);
            if(st!=null&&!st.isEmpty()&&st.hasTag()&&st.getTag()!=null&&st.getTag().getBoolean("ggo_supply"))supplies+=st.getCount();
        }
        g.drawString(this.font,"FIELD ITEMS  •  SUPPLIES "+supplies,ammoX,fieldY-20,0xFF9AA6B7,false);
        for(int i=18;i<=35;i++){
            int n=i-18,col=n%9,row=n/9,x=ammoX+col*34,y=fieldY+row*34;
            ggoSlot(g,x,y,30,selectedInventorySlot==i);
            renderStack(g,mc.player.getInventory().getItem(i),x+7,y+7);
        }

        int hintY=Math.min(top+panelH-24,this.height-96);
        g.drawString(this.font,"LMB: select / swap inside compartment   RMB: drop stack",bagX+16,hintY,0xFF667386,false);
        g.drawString(this.font,"Minecraft crafting / armor grid / recipe book are not part of GGO.",bagX+16,hintY+13,0xFF566273,false);
    }

    private void ggoSlot(GuiGraphics g,int x,int y,int size,boolean selected){
        g.fill(x,y,x+size,y+size,selected?0xFFD34B57:0xFF283341);
        g.fill(x+1,y+1,x+size-1,y+size-1,0xFF0B1016);
        if(selected)g.renderOutline(x,y,size,size,0xFFE45A67);
    }

    private int inventorySlotAt(double mouseX,double mouseY){
        int left=24,top=82;
        int profileW=Math.max(190,Math.min(240,this.width/4));
        int bagX=left+profileW+14;
        int combatX=left+14,combatY=top+104;
        for(int i=0;i<3;i++){int y=combatY+i*48;if(mouseX>=combatX&&mouseX<combatX+36&&mouseY>=y&&mouseY<y+36)return i;}
        int ammoX=bagX+16,ammoY=top+48;
        for(int i=0;i<9;i++){int x=ammoX+i*34;if(mouseX>=x&&mouseX<x+30&&mouseY>=ammoY&&mouseY<ammoY+30)return 9+i;}
        int fieldY=top+124;
        for(int i=18;i<=35;i++){int n=i-18,col=n%9,row=n/9,x=ammoX+col*34,y=fieldY+row*34;if(mouseX>=x&&mouseX<x+30&&mouseY>=y&&mouseY<y+30)return i;}
        return -1;
    }

    private static boolean sameInventoryCompartment(int a,int b){
        boolean aa=a>=9&&a<=17,ab=b>=9&&b<=17;if(aa||ab)return aa&&ab;
        boolean fa=a>=18&&a<=35,fb=b>=18&&b<=35;return fa&&fb;
    }
'''
s = s[:start] + inventory + s[end:]

mouse = "    public boolean mouseClicked(double mouseX,double mouseY,int button){\n"
if mouse in s:
    inject = r'''        if(page==Page.INVENTORY){
            int slot=inventorySlotAt(mouseX,mouseY);
            if(slot>=0){
                if(button==1){runClientCommand("ggoinv drop "+slot);selectedInventorySlot=-1;return true;}
                if(button==0){
                    Minecraft mc=Minecraft.getInstance();
                    if(slot>=0&&slot<=2){selectedInventorySlot=slot;if(mc.player!=null)mc.player.getInventory().selected=slot;runClientCommand("ggoinv select "+slot);return true;}
                    if(selectedInventorySlot>=9&&sameInventoryCompartment(selectedInventorySlot,slot)){
                        if(selectedInventorySlot!=slot)runClientCommand("ggoinv swap "+selectedInventorySlot+" "+slot);
                        selectedInventorySlot=-1;return true;
                    }
                    selectedInventorySlot=slot;return true;
                }
            }
        }
'''
    if "inventorySlotAt(mouseX,mouseY)" not in s[s.find(mouse):s.find(mouse)+1600]:
        s = s.replace(mouse, mouse + inject, 1)
else:
    pause_marker = "    @Override\n    public boolean isPauseScreen() {\n"
    if pause_marker not in s:
        raise SystemExit("stage42 mouse insertion marker missing")
    method = r'''    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(page==Page.INVENTORY){
            int slot=inventorySlotAt(mouseX,mouseY);
            if(slot>=0){
                if(button==1){runClientCommand("ggoinv drop "+slot);selectedInventorySlot=-1;return true;}
                if(button==0){
                    Minecraft mc=Minecraft.getInstance();
                    if(slot>=0&&slot<=2){selectedInventorySlot=slot;if(mc.player!=null)mc.player.getInventory().selected=slot;runClientCommand("ggoinv select "+slot);return true;}
                    if(selectedInventorySlot>=9&&sameInventoryCompartment(selectedInventorySlot,slot)){
                        if(selectedInventorySlot!=slot)runClientCommand("ggoinv swap "+selectedInventorySlot+" "+slot);
                        selectedInventorySlot=-1;return true;
                    }
                    selectedInventorySlot=slot;return true;
                }
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

'''
    s = s.replace(pause_marker, method + pause_marker, 1)

# Stage 3 exposed Minecraft XP in the GGO inventory. That progression is now fully replaced.
if 'g.drawString(this.font, "LEVEL"' in s:
    raise SystemExit("stage42 stale vanilla LEVEL UI survived inventory replacement")

for required in [
    'FIELD INVENTORY',
    'AMMO POUCH',
    'FIELD ITEMS',
    'ggoinv swap',
    'ggoinv select',
    'Minecraft crafting / armor grid / recipe book are not part of GGO.',
]:
    if required not in s:
        raise SystemExit(f"stage42 client inventory behavior missing: {required}")

SCREEN.write_text(s, encoding="utf-8")
print("Applied GGO Stage 42 interactive first-party inventory")
