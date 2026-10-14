package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.A20Entity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        com.example.roomsmod.util.ApproachSoundHook.set(ApproachSoundInstance::start);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RoomsMod.A20.get(), A20Renderer::new);
        event.registerEntityRenderer(RoomsMod.A40.get(), A40Renderer::new);
        event.registerEntityRenderer(RoomsMod.V10.get(), c -> new VRenderer<>(c, "v10", 2.0f, 0.5f));
        event.registerEntityRenderer(RoomsMod.V25T.get(), c -> new VRenderer<>(c, "v25t", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.V25L.get(), c -> new VRenderer<>(c, "v25l", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.V25_BUFFED.get(), c -> new VRenderer<>(c, "v25_buffed", 2.6f, 0.15f));
        event.registerEntityRenderer(RoomsMod.V35.get(), c -> new VRenderer<>(c, "v35", 1.1f, 0.05f).withEyes("v35_eyes"));
        event.registerEntityRenderer(RoomsMod.V50.get(), c -> new VRenderer<>(c, "v50", 2.8f, 0.08f));
    }

    private static class A20Renderer extends EntityRenderer<A20Entity> {
        private static final ResourceLocation TEX =
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/a20.png");
        private static final int FULLBRIGHT = 15728880;

        public A20Renderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(A20Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            float age = entity.tickCount + partialTick;
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.2f, 0.0f);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            float s = 2.5f * (1.0f + Mth.sin(age * 0.6f) * 0.06f + Mth.sin(age * 3.1f) * 0.02f);
            poseStack.scale(s, s, s);

            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEX));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f m = pose.pose();
            vertex(vc, pose, m, -0.5f, -0.5f, 0f, 1f);
            vertex(vc, pose, m, 0.5f, -0.5f, 1f, 1f);
            vertex(vc, pose, m, 0.5f, 0.5f, 1f, 0f);
            vertex(vc, pose, m, -0.5f, 0.5f, 0f, 0f);
            poseStack.popPose();
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        }

        private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
            vc.addVertex(m, x, y, 0f).setColor(255, 255, 255, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT).setNormal(pose, 0f, 1f, 0f);
        }

        @Override
        public ResourceLocation getTextureLocation(A20Entity entity) {
            return TEX;
        }
    }

    private static class A40Renderer extends EntityRenderer<com.example.roomsmod.entity.A40Entity> {
        private static final ResourceLocation TEX =
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/a40.png");
        private static final int FULLBRIGHT = 15728880;

        public A40Renderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(com.example.roomsmod.entity.A40Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            float age = entity.tickCount + partialTick;
            poseStack.pushPose();
            poseStack.translate(0.0f, 1.2f, 0.0f);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            // 揺れ・傾きなし、穏やかな拡縮のみ
            float s = 2.4f * (1.0f + Mth.sin(age * 0.25f) * 0.05f);
            poseStack.scale(s, s, s);

            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(TEX));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f m = pose.pose();
            vertex(vc, pose, m, -0.5f, -0.5f, 0f, 1f);
            vertex(vc, pose, m, 0.5f, -0.5f, 1f, 1f);
            vertex(vc, pose, m, 0.5f, 0.5f, 1f, 0f);
            vertex(vc, pose, m, -0.5f, 0.5f, 0f, 0f);
            poseStack.popPose();
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        }

        private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float u, float v) {
            vc.addVertex(m, x, y, 0f).setColor(255, 255, 255, 255).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULLBRIGHT).setNormal(pose, 0f, 1f, 0f);
        }

        @Override
        public ResourceLocation getTextureLocation(com.example.roomsmod.entity.A40Entity entity) {
            return TEX;
        }
    }

    /** V エンティティ共通：カメラを向く板（テクスチャで区別、仮）。V-35 はノック中は描画しない。 */
    private static class VRenderer<T extends com.example.roomsmod.entity.v.VEntity> extends EntityRenderer<T> {
        private final ResourceLocation tex;
        private final float size, wobble;
        private ResourceLocation eyes = null;
        VRenderer<T> withEyes(String name) { eyes = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/" + name + ".png"); return this; }

        VRenderer(EntityRendererProvider.Context context, String name, float size, float wobble) {
            super(context);
            this.tex = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/" + name + ".png");
            this.size = size; this.wobble = wobble;
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(T entity, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            if (entity instanceof com.example.roomsmod.entity.v.V35Entity v && !v.isPeeking()) return;
            if (entity instanceof com.example.roomsmod.entity.v.V25BuffedEntity b && !b.isRevealed()) return; // 捜索開始までは実体なし
            float age = entity.tickCount + pt;
            ps.pushPose();
            ps.translate(0.0f, size * 0.5f, 0.0f);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            float s = size * (1.0f + Mth.sin(age * 0.5f) * wobble * 0.2f);
            ps.scale(s, s, s);
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(tex));
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            int l = 15728880;
            vc.addVertex(m, -0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, 0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, 0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, -0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            if (eyes != null) { // 発光する目（クモの目と同じ eyes レイヤー）。少し手前に重ねる
                ps.translate(0f, 0f, 0.01f);
                PoseStack.Pose pe = ps.last(); Matrix4f me = pe.pose();
                VertexConsumer ve = buf.getBuffer(RenderType.eyes(eyes));
                ve.addVertex(me, -0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pe, 0f, 1f, 0f);
                ve.addVertex(me, 0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pe, 0f, 1f, 0f);
                ve.addVertex(me, 0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pe, 0f, 1f, 0f);
                ve.addVertex(me, -0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pe, 0f, 1f, 0f);
            }
            ps.popPose();
            super.render(entity, yaw, pt, ps, buf, light);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) { return tex; }
    }
}
