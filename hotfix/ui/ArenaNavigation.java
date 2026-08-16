package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Main/lobby navigation only. The in-match G weapon shop intentionally has no access to this navigation. */
final class ArenaNavigation {
    private ArenaNavigation() {}
    static void install(AbstractArenaScreen screen, UiRoute current) {
        UiLayout layout=UiLayout.of(screen.width,screen.height);int total=Math.min(400,Math.max(264,layout.panelWidth()-34));int gap=4,w=(total-gap*3)/4,start=layout.centerX()-total/2,y=14;
        add(screen,current,UiRoute.MAIN,new UiLayout.Rect(start,y,w,19),"ГЛАВНАЯ");
        add(screen,current,UiRoute.SHOP,new UiLayout.Rect(start+w+gap,y,w,19),"СКИНЫ");
        add(screen,current,UiRoute.PROFILE,new UiLayout.Rect(start+(w+gap)*2,y,w,19),"ПРОФИЛЬ");
        add(screen,current,UiRoute.SKILLS,new UiLayout.Rect(start+(w+gap)*3,y,w,19),"НАВЫКИ");
    }
    private static void add(AbstractArenaScreen s,UiRoute cur,UiRoute target,UiLayout.Rect r,String text){
        ArenaButton b=new ArenaButton(r,Component.literal(text),x->navigate(target));b.active=cur!=target;s.addArenaWidget(b);
    }
    static void navigate(UiRoute target){Minecraft mc=Minecraft.getInstance();if(mc.player==null||target==null)return;switch(target){case MAIN->mc.setScreen(new MainArenaScreen());case SHOP->mc.setScreen(new LobbyShopScreen());case PROFILE->mc.setScreen(new ProfileScreen());case SKILLS->mc.setScreen(new SkillsScreen());}}
}
