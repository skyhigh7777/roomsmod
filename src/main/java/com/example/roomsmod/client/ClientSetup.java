package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.A60Entity;
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
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RoomsMod.A60.get(), A60Renderer::new);
        event.registerEntityRenderer(RoomsMod.A75.get(), A75Renderer::new);
    }

    private static class A60Renderer extends EntityRenderer<A60Entity> {
        private static final ResourceLocation TEX =
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/a60.png");
        private static final int FULLBRIGHT = 15728880;

        public A60Renderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(A60Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
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
        public ResourceLocation getTextureLocation(A60Entity entity) {
            return TEX;
        }
    }

    private static class A75Renderer extends EntityRenderer<com.example.roomsmod.entity.A75Entity> {
        private static final ResourceLocation TEX =
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/a75.png");
        private static final int FULLBRIGHT = 15728880;

        public A75Renderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(com.example.roomsmod.entity.A75Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
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
        public ResourceLocation getTextureLocation(com.example.roomsmod.entity.A75Entity entity) {
            return TEX;
        }
    }
}
