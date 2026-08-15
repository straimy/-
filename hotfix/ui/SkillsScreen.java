package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.net.ArenaClientSkillEntry;
import arena.client.net.ClientSkillTreeStore;
import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

final class SkillsScreen extends AbstractArenaScreen {
    private int page;
    SkillsScreen() { super(Component.literal("Навыки"), UiRoute.SKILLS); }

    @Override protected void init() {
        installNavigation();
        ArenaClientNetwork.requestSkillTree();
        ArenaClientNetwork.requestSnapshot();
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        installNavigation();
        List<ArenaClientSkillEntry> all = ClientSkillTreeStore.entries();
        int perPage = 12;
        int from = Math.min(page * perPage, all.size());
        int to = Math.min(from + perPage, all.size());
        UiLayout.Rect panel = UiLayout.of(width,height).contentPanel();
        int gap = 8;
        int colW = Math.max(180, (panel.width()-48-gap)/2);
        int leftX = panel.x()+20;
        int rightX = leftX + colW + gap;
        int baseY = panel.y()+62;
        for (int i=from;i<to;i++) {
            ArenaClientSkillEntry e=all.get(i); final String id=e.id();
            int local=i-from, col=local/6, row=local%6;
            int x=col==0?leftX:rightX, y=baseY+row*27;
            ArenaButton b=new ArenaButton(new UiLayout.Rect(x,y,colW,22), Component.literal(label(e)), ignored->{ArenaClientNetwork.unlockSkill(id);ArenaClientNetwork.requestSkillTree();ArenaClientNetwork.requestSnapshot();});
            b.active=e.available() && !e.unlocked(); addRenderableWidget(b);
        }
        int navY=panel.y()+panel.height()-28;
        if (page>0) addRenderableWidget(new ArenaButton(new UiLayout.Rect(leftX,navY,88,20),Component.literal("‹ НАЗАД"),i->{page--;rebuildButtons();}));
        if (to<all.size()) addRenderableWidget(new ArenaButton(new UiLayout.Rect(leftX+96,navY,88,20),Component.literal("ДАЛЕЕ ›"),i->{page++;rebuildButtons();}));
    }

    private static String label(ArenaClientSkillEntry e) {
        if (e.unlocked()) return "✓ " + e.name();
        int crystals=Math.max(1,(e.cost()+9)/10);
        return e.name()+"  · "+e.cost()+" TP / "+crystals+"◆";
    }

    @Override public void tick() {
        super.tick();
        if (minecraft != null && minecraft.level != null && minecraft.level.getGameTime()%20L==0L) {
            ArenaClientNetwork.requestSkillTree(); ArenaClientNetwork.requestSnapshot();
        }
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        drawBackdrop(g); UiLayout.Rect panel=UiLayout.of(width,height).contentPanel(); drawPanel(g,panel);
        g.drawString(font,Component.literal("✦ НАВЫКИ"),panel.x()+20,panel.y()+15,UiTheme.ACCENT_2);
        if (!ClientSkillTreeStore.fresh(System.currentTimeMillis())) {
            g.drawString(font,Component.literal("обновление…"),panel.x()+20,panel.y()+38,UiTheme.MUTED);
        } else {
            var snap=ClientSnapshotStore.get();
            String top="Lv."+ClientSkillTreeStore.level()+"   •   TP "+ClientSkillTreeStore.points()+"   •   ◆ "+snap.crystals();
            g.drawString(font,Component.literal(top),panel.x()+20,panel.y()+37,UiTheme.TEXT);
            String hint="TP: +1 за 20 мин активной игры   •   ◆: +1 за 30 мин   •   кристаллами можно ускорить открытие";
            g.drawString(font,Component.literal(hint),panel.x()+210,panel.y()+38,UiTheme.MUTED);
        }
        super.render(g,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
