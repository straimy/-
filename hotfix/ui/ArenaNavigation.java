package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Main/lobby navigation. Six consistent GGO tabs on every major menu. */
final class ArenaNavigation {
    private ArenaNavigation() {}

    static void install(AbstractArenaScreen screen,UiRoute current){
        UiLayout layout=UiLayout.of(screen.width,screen.height);
        int total=Math.min(720,Math.max(510,layout.panelWidth()-24));
        int gap=5,w=(total-gap*5)/6,start=layout.centerX()-total/2,y=14;
        int x=start;
        add(screen,current,UiRoute.MAIN,new UiLayout.Rect(x,y,w,19),"ГЛАВНАЯ");x+=w+gap;
        add(screen,current,UiRoute.SHOP,new UiLayout.Rect(x,y,w,19),"МАГАЗИН");x+=w+gap;
        add(screen,current,UiRoute.PROFILE,new UiLayout.Rect(x,y,w,19),"ПРОФИЛЬ");x+=w+gap;
        add(screen,current,UiRoute.SKILLS,new UiLayout.Rect(x,y,w,19),"НАВЫКИ");x+=w+gap;
        addDirect(screen,new UiLayout.Rect(x,y,w,19),"ДРУЗЬЯ",screen instanceof FriendsScreen,()->new FriendsScreen());x+=w+gap;
        addDirect(screen,new UiLayout.Rect(x,y,w,19),"КЛАНЫ",screen instanceof ClanScreen,()->new ClanScreen());
    }

    private static void add(AbstractArenaScreen s,UiRoute cur,UiRoute target,UiLayout.Rect r,String text){
        ArenaButton b=new ArenaButton(r,Component.literal(text),x->navigate(target));b.active=cur!=target;s.addArenaWidget(b);
    }
    private static void addDirect(AbstractArenaScreen s,UiLayout.Rect r,String text,boolean current,java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory){
        ArenaButton b=new ArenaButton(r,Component.literal(text),x->Minecraft.getInstance().setScreen(factory.get()));b.active=!current;s.addArenaWidget(b);
    }
    static void navigate(UiRoute target){Minecraft mc=Minecraft.getInstance();if(mc.player==null||target==null)return;switch(target){case MAIN->mc.setScreen(new MainArenaScreen());case SHOP->mc.setScreen(new LobbyShopScreen());case PROFILE->mc.setScreen(new ProfileScreen());case SKILLS->mc.setScreen(new SkillsScreen());}}
}
