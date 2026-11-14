package net.mcreator.mcanomalyarchives.client.render;

import net.mcreator.mcanomalyarchives.events.AddictionClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyFlowerGeometry;
import net.mcreator.mcanomalyarchives.anomaly.poppy.PoppyScareSequence;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

/** Two hard cuts: a crooked toothed flower, then overlapping full-screen warning signs. */
public final class PoppyScareOverlay {
    private static final PoppyFlowerGeometry.Mesh MOUTH=PoppyFlowerGeometry.build(10);
    private PoppyScareOverlay() {}
    public static void render(GuiGraphics g) {
        Minecraft mc=Minecraft.getInstance();
        if(!AddictionClientHandler.shouldRender() || mc.isPaused() || AddictionClientHandler.cloud())return;
        int w=g.guiWidth(),h=g.guiHeight();
        float jump=AddictionClientHandler.scare();
        var stage=PoppyScareSequence.stage(jump);
        if(stage==PoppyScareSequence.Stage.NONE)return;
        if(stage==PoppyScareSequence.Stage.MOUTH) {
            g.fill(0,0,w,h,0x55260503);
            float scale=h*.58f;
            g.flush();g.pose().pushPose();
            g.pose().translate(w*.32f,h*.40f,600);
            g.pose().mulPose(Axis.ZP.rotationDegrees(-12));
            g.pose().scale(scale,-scale,scale*.12f);
            g.pose().translate(-MOUTH.head().x(),-MOUTH.head().y(),-MOUTH.head().z());
            var buffers=mc.renderBuffers().bufferSource();
            PoppyFlowerMaterial.draw(buffers.getBuffer(PoppyFlowerMaterial.TYPE),g.pose(),MOUTH,10,1,false);
            buffers.endBatch(PoppyFlowerMaterial.TYPE);g.pose().popPose();RenderSystem.enableDepthTest();
        } else {
            g.pose().pushPose();g.pose().translate(0,0,800);
            float cover=Math.max(w/1280f,h/720f),ox=(w-1280*cover)*.5f,oy=(h-720*cover)*.5f;
            for(int y=0;y<36;y++)for(int x=0;x<64;x++) {
                g.fill((int)Math.floor(ox+x*20*cover),(int)Math.floor(oy+y*20*cover),
                    (int)Math.ceil(ox+(x+1)*20*cover),(int)Math.ceil(oy+(y+1)*20*cover),PoppyScareSequence.glitchColor(x,y));
            }
            for(var card:PoppyScareSequence.CARDS)warning(g,ox+card.x()*cover,oy+card.y()*cover,card.size()*cover);
            if(jump>.94f)g.fill(0,0,w,h,Math.min(245,(int)((jump-.94f)/.06f*245))<<24);
            g.pose().popPose();
        }
    }
    private static void warning(GuiGraphics g,float x,float y,float size) {
        rect(g,x,y,size,0,0,1,1,0xff000000);
        rect(g,x,y,size,.10f,.10f,.90f,.90f,0xfff4e900);
        rect(g,x,y,size,.425f,.205f,.605f,.605f,0xff000000);
        rect(g,x,y,size,.425f,.695f,.605f,.865f,0xff000000);
    }
    private static void rect(GuiGraphics g,float x,float y,float size,float left,float top,float right,float bottom,int color) {
        g.fill(Math.round(x+left*size),Math.round(y+top*size),Math.round(x+right*size),Math.round(y+bottom*size),color);
    }
}
