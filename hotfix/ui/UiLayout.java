package arena.client.ui;

/** Responsive layout math. Designed to stay usable from GUI scale 1 through 4. */
public final class UiLayout {
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double mx,double my){ return mx>=x&&mx<x+width&&my>=y&&my<y+height; }
    }
    private final int width,height,centerX,panelWidth;
    private UiLayout(int width,int height){
        this.width=Math.max(300,width); this.height=Math.max(170,height); this.centerX=this.width/2;
        int side = this.width < 520 ? 24 : this.width < 760 ? 44 : 72;
        int cap = this.width < 520 ? 360 : this.width < 760 ? 430 : 470;
        this.panelWidth=Math.min(cap,Math.max(276,this.width-side));
    }
    public static UiLayout of(int w,int h){return new UiLayout(w,h);} public int width(){return width;} public int height(){return height;}
    public int centerX(){return centerX;} public int panelWidth(){return panelWidth;} public int panelLeft(){return centerX-panelWidth/2;}
    public int titleY(){return Math.max(20,Math.min(48,height/9));}
    public Rect primaryButton(int index){
        int bw=Math.min(276,panelWidth-32), bh=height<260?25:29, gap=height<260?6:8;
        int sy=Math.max(titleY()+48,height/2-46);
        return new Rect(centerX-bw/2,sy+index*(bh+gap),bw,bh);
    }
    public Rect contentPanel(){
        int top=Math.max(45,Math.min(76,height/9)); int bottom=height<280?18:30;
        int available=Math.max(118,height-top-bottom); int ph=Math.min(390,available);
        return new Rect(panelLeft(),top,panelWidth,ph);
    }
}
