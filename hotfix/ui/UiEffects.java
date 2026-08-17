package arena.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Small allocation-free neon effects shared by GGO screens. */
public final class UiEffects {
    private UiEffects() {}

    public static void verticalGradient(GuiGraphics g,int x1,int y1,int x2,int y2,int top,int bottom){
        int h=Math.max(1,y2-y1),steps=Math.min(24,h);
        for(int i=0;i<steps;i++){
            int ya=y1+(h*i)/steps,yb=y1+(h*(i+1))/steps;
            double t=steps<=1?0.0:(double)i/(steps-1);
            g.fill(x1,ya,x2,yb,lerp(top,bottom,t));
        }
    }

    public static void animatedSheen(GuiGraphics g,int x,int y,int w,int h,long now,int rgb){
        if(w<=6||h<=2)return;
        int travel=w+70;int center=x-35+(int)((now%2600L)*travel/2600L);
        for(int i=-3;i<=3;i++){
            int xx=center+i*4;if(xx<=x||xx>=x+w)continue;
            int a=Math.max(5,24-Math.abs(i)*5);
            g.fill(xx,y,Math.min(xx+4,x+w),y+h,(a<<24)|(rgb&0xFFFFFF));
        }
    }

    public static void pulseBorder(GuiGraphics g,int x,int y,int w,int h,long now,int rgb){
        double wave=(Math.sin(now/360.0)+1.0)*0.5;int a=32+(int)(44*wave);int c=(a<<24)|(rgb&0xFFFFFF);
        g.fill(x,y,x+w,y+1,c);g.fill(x,y+h-1,x+w,y+h,c);g.fill(x,y,x+1,y+h,c);g.fill(x+w-1,y,x+w,y+h,c);
    }

    private static int lerp(int a,int b,double t){
        int aa=(a>>>24)&255,ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255;
        int ba=(b>>>24)&255,br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255;
        int oa=(int)(aa+(ba-aa)*t),or=(int)(ar+(br-ar)*t),og=(int)(ag+(bg-ag)*t),ob=(int)(ab+(bb-ab)*t);
        return (oa<<24)|(or<<16)|(og<<8)|ob;
    }
}
