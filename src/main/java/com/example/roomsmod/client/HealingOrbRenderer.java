package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.orb.HealingOrbEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** 回復のオーブ：カメラを向く光る球の絵（明るさ最大）。ゆっくり上下に揺れて、少し脈打つ。 */
public class HealingOrbRenderer extends EntityRenderer<HealingOrbEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/healing_orb.png");

    public HealingOrbRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0f; }

    @Override
    public void render(HealingOrbEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float bob = Mth.sin(t * 0.08f) * 0.12f, size = 0.8f + Mth.sin(t * 0.15f) * 0.05f;
        ps.pushPose();
        ps.translate(0f, 0.3f + bob, 0f);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.scale(size, size, size);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        int l = 15728880;
        vc.addVertex(m, -0.5f, -0.5f, 0f).setColor(255, 255, 255, 230).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
        vc.addVertex(m, 0.5f, -0.5f, 0f).setColor(255, 255, 255, 230).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
        vc.addVertex(m, 0.5f, 0.5f, 0f).setColor(255, 255, 255, 230).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
        vc.addVertex(m, -0.5f, 0.5f, 0f).setColor(255, 255, 255, 230).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, light);
    }

    @Override public ResourceLocation getTextureLocation(HealingOrbEntity e) { return TEX; }
}
