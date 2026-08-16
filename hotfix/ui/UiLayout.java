package arena.client.ui;

/** Compact responsive layout math. Designed to stay usable from GUI scale 1 through 4. */
public final class UiLayout {
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double mx,double my){ return mx>=x&&mx<x+width&&my>=y&&my<y+height; }
    }
    private final int width,height,centerX,panelWidth;
    private UiLayout(int width,int height){
        this.width=Math.max(300,width); this.height=Math.max(170,height); this.centerX=this.width/2;
        int side=this.width<520?34:this.width<760?70:110;
        int cap=this.width<520?340:this.width<760?390:420;
        this.panelWidth=Math.min(cap,Math.max(270,this.width-side));
    }
    public static UiLayout of(int w,int h){return new UiLayout(w,h);} public int width(){return width;} public int height(){return height;}
    public int centerX(){return centerX;} public int panelWidth(){return panelWidth;} public int panelLeft(){return centerX-panelWidth/2;}
    public int titleY(){return Math.max(16,Math.min(38,height/11));}
    public Rect primaryButton(int index){
        int bw=Math.min(244,panelWidth-34),bh=height<260?22:25,gap=height<260?5:6;
        int sy=Math.max(titleY()+39,height/2-42);
        return new Rect(centerX-bw/2,sy+index*(bh+gap),bw,bh);
    }
    public Rect contentPanel(){
        int top=Math.max(38,Math.min(62,height/11));int bottom=height<280?14:22;
        int available=Math.max(116,height-top-bottom);int ph=Math.min(340,available);
        return new Rect(panelLeft(),top,panelWidth,ph);
    }
}
