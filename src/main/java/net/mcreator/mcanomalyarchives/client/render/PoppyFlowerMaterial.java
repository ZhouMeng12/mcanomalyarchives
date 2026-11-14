package net.mcreator.mcanomalyarchives.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyAddictionRules;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyFlowerGeometry;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** One atlas, stable crown-space UVs, separate petal/leaf/stem/throat/tooth materials. */
public final class PoppyFlowerMaterial {
    public static final RenderType TYPE=RenderType.entityTranslucent(ResourceLocation.fromNamespaceAndPath("mcanomalyarchives","textures/entity/poppy_illusion.png"));
    private PoppyFlowerMaterial() {}
    public static void draw(VertexConsumer v,PoseStack pose,PoppyFlowerGeometry.Mesh mesh,float shape,float alpha,boolean headOnly) {
        for(var face:mesh.triangles()) {
            if(headOnly && (face.material()==PoppyFlowerGeometry.STEM || face.material()==PoppyFlowerGeometry.LEAF))continue;
            var normal=face.b().sub(face.a()).cross(face.c().sub(face.a())).normal();
            int brightest=Math.max((face.color()>>16)&255,Math.max((face.color()>>8)&255,face.color()&255));
            int reference=switch(face.material()){case 1->146;case 2->106;case 3->84;case 4,5->255;default->238;};
            int gray=Math.min(255,brightest*255/reference);
            int color=((int)(alpha*255)<<24)|(gray<<16)|(gray<<8)|gray;
            vertex(v,pose,mesh,face.a(),normal,face.material(),shape,0,0,color);
            vertex(v,pose,mesh,face.b(),normal,face.material(),shape,1,0,color);
            vertex(v,pose,mesh,face.c(),normal,face.material(),shape,.5f,1,color);
            vertex(v,pose,mesh,face.c(),normal,face.material(),shape,.5f,1,color);
        }
    }
    private static void vertex(VertexConsumer v,PoseStack pose,PoppyFlowerGeometry.Mesh mesh,PoppyFlowerGeometry.Point p,
            PoppyFlowerGeometry.Point normal,int material,float shape,float u,float t,int color) {
        double radius=PoppyAddictionRules.width(shape)*.5;
        if(material==5) {
            u=(float)(.5+(p.x()-mesh.head().x())/(radius*2.4));
            t=(float)(.5-(p.y()-mesh.head().y())/(radius*2.4));
        } else if(material==0 || material==3) {
            double span=radius*(material==3?.8:2.5);
            u=(float)(.5+(p.x()-mesh.head().x())/span);
            t=(float)(.5-(p.y()-mesh.head().y())/span);
        } else if(material==1) { u=(float)((p.x()+p.z())*3);t=(float)(p.y()*.8);u-=Math.floor(u);t-=Math.floor(t); }
        else if(material==2) { u=(float)(.5+p.x()/2.8);t=(float)(.5+p.z()/1.4); }
        else if(material==4) {
            u=(float)(.5+(p.x()-mesh.head().x())/radius*.7);
            t=(float)(.5-(p.y()-mesh.head().y())/radius*.7);
        }
        u=Math.max(0,Math.min(1,u));t=Math.max(0,Math.min(1,t));
        float atlasU=material==5?(128.5f+u*127)/256f:((material%4)*64+.5f+u*63)/256f;
        float atlasV=material==5?(128.5f+t*127)/256f:((material/4)*64+.5f+t*63)/256f;
        v.addVertex(pose.last(),(float)p.x(),(float)p.y(),(float)p.z()).setColor(color).setUv(atlasU,atlasV)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose.last(),(float)normal.x(),(float)normal.y(),(float)normal.z());
    }
}
