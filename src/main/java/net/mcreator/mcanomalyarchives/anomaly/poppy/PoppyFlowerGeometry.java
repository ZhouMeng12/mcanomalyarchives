package net.mcreator.mcanomalyarchives.anomaly.poppy;

import java.util.ArrayList;
import java.util.List;

/** Separate stem, basal leaves, scalloped petals and late-stage teeth; all units are blocks. */
public final class PoppyFlowerGeometry {
    public static final int PETAL=0, STEM=1, LEAF=2, THROAT=3, TOOTH=4, PAINTED_HEAD=5;
    private PoppyFlowerGeometry() {}
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x + p.x, y + p.y, z + p.z); }
        public Point sub(Point p) { return new Point(x - p.x, y - p.y, z - p.z); }
        public Point mul(double s) { return new Point(x * s, y * s, z * s); }
        public Point cross(Point p) { return new Point(y * p.z - z * p.y, z * p.x - x * p.z, x * p.y - y * p.x); }
        public Point normal() { double n = Math.sqrt(x*x + y*y + z*z); return n < 1e-9 ? new Point(1,0,0) : mul(1/n); }
    }
    public record Triangle(Point a, Point b, Point c, int color, int material) {
        public Triangle(Point a,Point b,Point c,int color) { this(a,b,c,color,((color>>8)&255) > ((color>>16)&255) ? STEM : PETAL); }
    }
    public record Mesh(List<Triangle> triangles, Point head) {}

    public static Mesh build(float intensity) {
        float i = Math.max(0, Math.min(10, intensity));
        double h = PoppyAddictionRules.height(i), bend = PoppyAddictionRules.bend(i);
        Point head = new Point(PoppyAddictionRules.headSide(i), PoppyAddictionRules.headHeight(i), PoppyAddictionRules.lean(i));
        List<Triangle> faces = new ArrayList<>(800);
        Point attachment = head.add(new Point(0,0,-PoppyAddictionRules.width(i)*.11));
        Point middle=new Point(h*.46*bend,h*(.52+.20*bend),-.08*bend);
        Point[] lower = {new Point(0,0,0),new Point(-h*.08*bend,h*.22,-.02),new Point(h*.38*bend,h*.43,-.04),middle};
        Point[] upper = {middle,new Point(h*.64*bend,h*(.66+.48*bend),-.10*bend),
                new Point(head.x()-h*.08*bend,h*(.84+.34*bend),attachment.z()-.12*bend),attachment};
        int steps = 32, sides = 4;
        Point[] previous = null;
        for (int step = 0; step <= steps; step++) {
            double t = step / (double)steps;
            Point[] controls=t<=.5?lower:upper;double local=t<=.5?t*2:(t-.5)*2;
            Point center = bezier(controls,local), tangent = derivative(controls,local).normal();
            Point right = tangent.cross(new Point(0,0,1)).normal(), out = tangent.cross(right).normal();
            double radius = step == 0 ? 0 : (.055 + i*.0055) * (1 - .16*t);
            Point[] ring = new Point[sides];
            for (int s = 0; s < sides; s++) {
                double angle = s*Math.PI*2/sides+Math.PI*.25;
                ring[s] = center.add(right.mul(Math.cos(angle)*radius)).add(out.mul(Math.sin(angle)*radius));
                if (previous != null && s > 0) quad(faces,previous[s-1],previous[s],ring[s],ring[s-1],shade(0x2e9237,.72+.26*s/sides));
            }
            if (previous != null) quad(faces,previous[sides-1],previous[0],ring[0],ring[sides-1],0x267b2e);
            if(step>0&&step<steps&&step%4==0)for(int side:new int[]{-1,1}) {
                Point base=center.add(right.mul(radius*side));
                Point tip=base.add(right.mul(radius*2*side)).add(tangent.mul(radius*.8));
                Point root=base.sub(tangent.mul(radius*1.1)),ridge=base.add(out.mul(radius*.7));
                faces.add(new Triangle(root,tip,ridge,0x21792d,STEM));
                faces.add(new Triangle(base,ridge,tip,0x185e26,STEM));
            }
            previous = ring;
        }
        leaf(faces,-1,Math.min(1.35,.35+i*.105));
        leaf(faces,1,Math.min(1.35,.35+i*.105));
        if(i>=7)paintedHead(faces,head,PoppyAddictionRules.width(i)*.5);
        else crown(faces,head,PoppyAddictionRules.width(i)*.5,bend,i);
        return new Mesh(List.copyOf(faces),head);
    }

    private static void paintedHead(List<Triangle> faces,Point head,double r) {
        int size=PoppyFlowerOutline.SIZE;double step=2.4/size,front=.13*r,back=-.10*r;
        Point a=head.add(new Point(-1.2*r,-1.2*r,front)),b=head.add(new Point(1.2*r,-1.2*r,front));
        Point c=head.add(new Point(1.2*r,1.2*r,front)),d=head.add(new Point(-1.2*r,1.2*r,front));
        faces.add(new Triangle(a,b,c,0xffffff,PAINTED_HEAD));faces.add(new Triangle(a,c,d,0xffffff,PAINTED_HEAD));
        for(int y=0;y<size;y++)for(int x=0;x<size;x++) {
            if(!PoppyFlowerOutline.filled(x,y))continue;
            int start=x;while(x+1<size&&PoppyFlowerOutline.filled(x+1,y))x++;
            double left=(-1.2+start*step)*r,right=(-1.2+(x+1)*step)*r,top=(1.2-y*step)*r,bottom=top-step*r;
            quad(faces,head.add(new Point(left,top,back)),head.add(new Point(right,top,back)),
                    head.add(new Point(right,bottom,back)),head.add(new Point(left,bottom,back)),0xc52b1b);
            for(int px=start;px<=x;px++) {
                double l=(-1.2+px*step)*r,rr=l+step*r;
                double[][] edges={{l,bottom,rr,bottom,px,y+1},{rr,bottom,rr,top,px+1,y},
                        {rr,top,l,top,px,y-1},{l,top,l,bottom,px-1,y}};
                for(double[] e:edges)if(!PoppyFlowerOutline.filled((int)e[4],(int)e[5]))quad(faces,
                        head.add(new Point(e[0],e[1],front)),head.add(new Point(e[2],e[3],front)),
                        head.add(new Point(e[2],e[3],back)),head.add(new Point(e[0],e[1],back)),0xab241b);
            }
        }
    }

    private static Point bezier(Point[] p,double t) {
        double s=1-t;
        return p[0].mul(s*s*s).add(p[1].mul(3*s*s*t)).add(p[2].mul(3*s*t*t)).add(p[3].mul(t*t*t));
    }
    private static Point derivative(Point[] p,double t) {
        double s=1-t;
        return p[1].sub(p[0]).mul(3*s*s).add(p[2].sub(p[1]).mul(6*s*t)).add(p[3].sub(p[2]).mul(3*t*t));
    }
    private static void leaf(List<Triangle> faces,int side,double length) {
        double[][] edge={{0,0},{.16,.16},{.27,.1},{.39,.26},{.49,.18},{.65,.3},{.75,.19},{1,.04},
                {.77,-.15},{.65,-.1},{.49,-.24},{.37,-.15},{.23,-.19},{.13,-.07}};
        Point mid=new Point(side*length*.42,.12+length*.3,0);
        double thickness=.025+length*.02;
        for(int n=0;n<edge.length;n++) {
            double[] a=edge[n],b=edge[(n+1)%edge.length];
            Point pa=new Point(side*a[0]*length,.055+Math.sin(a[0]*Math.PI)*length*.3+a[1]*length*.55,a[1]*length*.65);
            Point pb=new Point(side*b[0]*length,.055+Math.sin(b[0]*Math.PI)*length*.3+b[1]*length*.55,b[1]*length*.65);
            faces.add(new Triangle(mid,pa,pb,shade(0x236a30,n<7?1.12:.77),LEAF));
            Point down=new Point(0,-thickness,0);
            faces.add(new Triangle(mid.add(down),pb.add(down),pa.add(down),0x1d5728,LEAF));
            faces.add(new Triangle(pa,pb,pb.add(down),0x1e5d2a,LEAF));
            faces.add(new Triangle(pa,pb.add(down),pa.add(down),0x1e5d2a,LEAF));
        }
    }
    private static void crown(List<Triangle> faces,Point head,double r,double bend,float intensity) {
        double opening=Math.max(0,Math.min(1,(intensity-5)/4.0));
        opening=opening*opening*(3-2*opening);
        double mouth=.10+.45*opening;
        int pixels=16;double step=2.4/pixels;
        for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++) {
            double px=-1.2+x*step,py=-1.2+y*step,cx=px+step*.5,cy=py+step*.5;
            double depth=pixelDepth(cx,cy,mouth);
            if(Double.isNaN(depth))continue;
            boolean throat=insideMouth(cx,cy,mouth);
            int material=throat?THROAT:PETAL;
            int color=throat?0x541311:depth>.1?0xee3628:0xdb2b20;
            Point a=pixelPoint(head,r,px,py,depth,bend),b=pixelPoint(head,r,px+step,py,depth,bend);
            Point c=pixelPoint(head,r,px+step,py+step,depth,bend),d=pixelPoint(head,r,px,py+step,depth,bend);
            faces.add(new Triangle(a,b,c,color,material));faces.add(new Triangle(a,c,d,color,material));
            double[][] edge={{px,py,px+step,py,cx,cy-step},{px+step,py,px+step,py+step,cx+step,cy},
                    {px+step,py+step,px,py+step,cx,cy+step},{px,py+step,px,py,cx-step,cy}};
            for(double[] e:edge) {
                double adjacent=pixelDepth(e[4],e[5],mouth);
                if(Double.isNaN(adjacent))adjacent=-.13;
                if(adjacent>=depth-.001)continue;
                quad(faces,pixelPoint(head,r,e[0],e[1],depth,bend),pixelPoint(head,r,e[2],e[3],depth,bend),
                        pixelPoint(head,r,e[2],e[3],adjacent,bend),pixelPoint(head,r,e[0],e[1],adjacent,bend),0xa71b16);
            }
        }
    }
    private static boolean insideMouth(double x,double y,double mouth) {
        double mx=(x+.018)/mouth,my=(y-.015)/(mouth*.86);
        return Math.pow(Math.abs(mx),4)+Math.pow(Math.abs(my),4)<1;
    }
    private static double pixelDepth(double x,double y,double mouth) {
        double radius=Math.hypot(x,y/.86),angle=Math.atan2(y/.86,x);
        double boundary=1+.12*Math.cos(4*angle+.35)+.04*Math.sin(3*angle+.2);
        if(radius>boundary)return Double.NaN;
        if(insideMouth(x,y,mouth))return -.10;
        int petal=(int)Math.floor((angle+Math.PI+.35)/(Math.PI*.5));
        return (radius>.72?.15:.045)+(petal%2)*.065;
    }
    private static Point pixelPoint(Point head,double radius,double x,double y,double depth,double bend) {
        double tilt=.55*(1-bend),py=y*radius,z=depth*radius;
        return head.add(new Point(x*radius,py*Math.cos(tilt)-z*Math.sin(tilt),py*Math.sin(tilt)+z*Math.cos(tilt)));
    }
    private static int shade(int color,double amount) {
        int r=(int)Math.min(255,((color>>16)&255)*amount),g=(int)Math.min(255,((color>>8)&255)*amount),b=(int)Math.min(255,(color&255)*amount);
        return(r<<16)|(g<<8)|b;
    }
    private static void quad(List<Triangle> faces,Point a,Point b,Point c,Point d,int color) {
        faces.add(new Triangle(a,b,c,color));faces.add(new Triangle(a,c,d,color));
    }
}
