package arena.client.ui;

import arena.client.net.ClientSnapshotStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Lobby-only cosmetic storefront. Weapon purchases are intentionally absent here. */
final class LobbyShopScreen extends AbstractArenaScreen {
    LobbyShopScreen() { super(Component.literal("Лобби-магазин"), UiRoute.SHOP); }

    @Override protected void init() { installNavigation(); arena.client.net.ArenaClientNetwork.requestSnapshot(); }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        drawBackdrop(g); UiLayout.Rect panel=UiLayout.of(width,height).contentPanel(); drawPanel(g,panel);
        int x=panel.x()+22, y=panel.y()+18;
        g.drawString(font,Component.literal("✦ ЛОББИ-МАГАЗИН"),x,y,UiTheme.PINK); y+=28;
        var snap=ClientSnapshotStore.get();
        g.drawString(font,Component.literal("◆ "+snap.crystals()+"   ◇ "+snap.coins()),x,y,UiTheme.BLUE); y+=28;
        g.drawString(font,Component.literal("КОСМЕТИКА"),x,y,UiTheme.ACCENT); y+=22;
        card(g,x,y,"NEON PULSE","скин оружия • голубой/фиолетовый","ПРОТОТИП"); y+=54;
        card(g,x,y,"CRIMSON GRID","скин оружия • красный/чёрный","ПРОТОТИП"); y+=64;
        g.drawString(font,Component.literal("Скины подготовлены как отдельная косметическая ветка;"),x,y,UiTheme.MUTED); y+=14;
        g.drawString(font,Component.literal("визуальное применение к JEG включим после проверки их model overrides."),x,y,UiTheme.MUTED); y+=24;
        g.drawString(font,Component.literal("Оружие покупается только в матче: нажми G."),x,y,UiTheme.ACCENT_2);
        super.render(g,mouseX,mouseY,partialTick);
    }

    private void card(GuiGraphics g,int x,int y,String name,String sub,String state){
        int w=Math.min(420,UiLayout.of(width,height).contentPanel().width()-44);
        g.fill(x,y,x+w,y+44,0xA0182238); g.renderOutline(x,y,w,44,UiTheme.BLUE);
        g.drawString(font,Component.literal("◆ "+name),x+12,y+9,UiTheme.TEXT);
        g.drawString(font,Component.literal(sub),x+12,y+25,UiTheme.MUTED);
        int sw=font.width(state); g.drawString(font,Component.literal(state),x+w-sw-12,y+17,UiTheme.PINK);
    }

    @Override public boolean isPauseScreen(){return false;}
}
