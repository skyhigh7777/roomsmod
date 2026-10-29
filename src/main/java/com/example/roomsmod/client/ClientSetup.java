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
        com.example.roomsmod.util.ApproachSoundHook.setLoop((e, s) -> { if (e instanceof com.example.roomsmod.entity.v.V100Entity v) WatchSoundInstance.start(v, s); });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RoomsMod.A20.get(), A20Renderer::new);
        event.registerEntityRenderer(RoomsMod.A40.get(), A40Renderer::new);
        event.registerEntityRenderer(RoomsMod.V10.get(), c -> new VRenderer<>(c, "v10", 2.0f, 0.5f));
        event.registerEntityRenderer(RoomsMod.V25T.get(), c -> new VRenderer<>(c, "v25t", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.V25L.get(), c -> new VRenderer<>(c, "v25l", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.V25_BUFFED.get(), c -> new VRenderer<>(c, "v25_buffed", 2.6f, 0.15f));
        event.registerEntityRenderer(RoomsMod.V100.get(), c -> new VRenderer<>(c, "v100", 1.1f, 0.05f).withEyes("v100_eyes"));
        event.registerEntityRenderer(RoomsMod.V50.get(), c -> new VRenderer<>(c, "v50", 2.8f, 0.08f));
        event.registerEntityRenderer(RoomsMod.GK_CHIME.get(), c -> new VRenderer<>(c, "gk_chime", 2.0f, 0.4f));
        event.registerEntityRenderer(RoomsMod.GK_MIMIC.get(), c -> new VRenderer<>(c, "gk_mimic", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.GK_MOTH.get(), c -> new VRenderer<>(c, "gk_moth", 1.4f, 0.3f));
        event.registerEntityRenderer(RoomsMod.GK_STARE.get(), c -> new VRenderer<>(c, "gk12", 2.2f, 0.02f));
        event.registerEntityRenderer(RoomsMod.GK_WHISPER.get(), c -> new VRenderer<>(c, "gk_whisper", 2.0f, 0.3f));
        event.registerEntityRenderer(RoomsMod.GK_DETENTION.get(), c -> new VRenderer<>(c, "gk_detention", 2.4f, 0.02f));
        event.registerEntityRenderer(RoomsMod.GK_SUBSTITUTE.get(), c -> new VRenderer<>(c, "gk_substitute", 2.2f, 0.05f).outlineOnly()); // GK-105 は姿を描かない。デバッグ時だけ光る輪郭のみ。手がかりは偽の扉だけ
        event.registerEntityRenderer(RoomsMod.GK_ERASER.get(), c -> new VRenderer<>(c, "gk_eraser", 2.2f, 0.4f));
        event.registerEntityRenderer(RoomsMod.GK_QUIZ.get(), c -> new VRenderer<>(c, "gk_quiz", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.GK_PA.get(), c -> new VRenderer<>(c, "gk_pa", 1.0f, 0.05f));
        event.registerEntityRenderer(RoomsMod.GK_JANITOR.get(), c -> new VRenderer<>(c, "gk_janitor", 2.4f, 0.1f));
        event.registerEntityRenderer(RoomsMod.GK_MIRROR.get(), c -> new VRenderer<>(c, "gk_mirror", 2.0f, 0.02f));
        event.registerEntityRenderer(RoomsMod.GK_07.get(), c -> new VRenderer<>(c, "gk07", 2.2f, 0.03f));
        event.registerEntityRenderer(RoomsMod.B_GRIN.get(), c -> new BRenderer<>(c, "b_grin", 2.8f));
        event.registerEntityRenderer(RoomsMod.B_MASK.get(), c -> new BRenderer<>(c, "b_mask", 2.0f));
        event.registerEntityRenderer(RoomsMod.B_EYE.get(), c -> new BRenderer<>(c, "b_eye", 2.4f));
        event.registerEntityRenderer(RoomsMod.B_CHORUS.get(), c -> new BRenderer<>(c, "b_chorus", 2.2f));
        event.registerEntityRenderer(RoomsMod.B_STATIC.get(), c -> new BRenderer<>(c, "b_static", 2.4f));
        event.registerEntityRenderer(RoomsMod.B_ERROR.get(), c -> new BRenderer<>(c, "b_error", 1.8f));
        event.registerEntityRenderer(RoomsMod.B_ARGUS.get(), c -> new BRenderer<>(c, "b_argus", 2.8f));
        event.registerEntityRenderer(RoomsMod.B_MAW.get(), c -> new BRenderer<>(c, "b_maw", 2.4f));
        event.registerEntityRenderer(RoomsMod.B_SCATTER.get(), c -> new BRenderer<>(c, "b_scatter", 2.2f));
        event.registerEntityRenderer(RoomsMod.B_REACH.get(), c -> new BRenderer<>(c, "b_reach", 3.0f));
        event.registerEntityRenderer(RoomsMod.B_SMILE.get(), c -> new BRenderer<>(c, "b_smile", 1.8f));
        event.registerEntityRenderer(RoomsMod.B_VOID.get(), c -> new BRenderer<>(c, "b_void", 2.8f));
        event.registerEntityRenderer(RoomsMod.B_17.get(), c -> new BRenderer<>(c, "b_17", 1.4f));
        event.registerEntityRenderer(RoomsMod.GK_BELL2.get(), c -> new VRenderer<>(c, "gk_bell2", 2.0f, 0.5f));
        event.registerEntityRenderer(RoomsMod.GK_REWIND.get(), c -> new VRenderer<>(c, "gk_rewind", 1.8f, 0.1f));
        event.registerEntityRenderer(RoomsMod.U20.get(), c -> new BRenderer<>(c, "u20", 2.4f));
        event.registerEntityRenderer(RoomsMod.U60.get(), c -> new BRenderer<>(c, "u60", 2.4f));
        event.registerEntityRenderer(RoomsMod.U120.get(), c -> new BRenderer<>(c, "u120", 2.2f));
        event.registerEntityRenderer(RoomsMod.U200.get(), c -> new BRenderer<>(c, "u200", 2.0f));
        event.registerEntityRenderer(RoomsMod.U300.get(), c -> new BRenderer<>(c, "u300", 2.2f));
        event.registerEntityRenderer(RoomsMod.E332.get(), net.minecraft.client.renderer.entity.NoopRenderer::new); // 姿なし
        // PLACEHOLDER：単色の正方形（揺れなし）
        event.registerEntityRenderer(RoomsMod.PLACEHOLDER_32767.get(), c -> new VRenderer<>(c, "placeholder_32767", 1.5f, 0f));
        event.registerEntityRenderer(RoomsMod.PLACEHOLDER_65535.get(), c -> new VRenderer<>(c, "placeholder_65535", 1.5f, 0f));
        event.registerEntityRenderer(RoomsMod.PLACEHOLDER_2147483648.get(), c -> new VRenderer<>(c, "placeholder_2147483648", 1.5f, 0f));
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

    /** V エンティティ共通：カメラを向く板（テクスチャで区別、仮）。V-100 はノック中は描画しない。 */
    /**
     * B のグリッチ系エンティティ：平面のビルボード（常にカメラを向く2Dスプライト）。テクスチャは縦に4コマ（64x256）。
     * ときどきコマ飛び・横ずれ・点滅して、映像が乱れたように見せる。
     */
    private static class BRenderer<T extends com.example.roomsmod.entity.v.VEntity> extends EntityRenderer<T> {
        private final ResourceLocation tex;
        private final float size;
        static final int FRAMES = 4;
        BRenderer(EntityRendererProvider.Context context, String name, float size) {
            super(context);
            this.tex = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/" + name + ".png");
            this.size = size;
            this.shadowRadius = 0.0f;
        }
        @Override
        public void render(T entity, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            int t = entity.tickCount;
            long h = (t / 2) * 0x9E3779B97F4A7C15L + entity.getId() * 31L;
            int glitch = (int) ((h >>> 40) & 31); // 0..31：ときどき乱れる
            if (glitch == 0) return; // 一瞬消える
            int frame = glitch < 4 ? glitch % FRAMES : (t / 6) % 2 == 0 ? 0 : (glitch < 8 ? 1 + glitch % 3 : 0);
            float jx = glitch < 3 ? ((glitch & 1) == 0 ? 0.12f : -0.12f) : 0f;
            ps.pushPose();
            ps.translate(0.0f, size * 0.5f, 0.0f);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            ps.scale(size, size, size);
            ps.translate(jx, 0f, 0f);
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(tex));
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            float v0 = frame / (float) FRAMES, v1 = (frame + 1) / (float) FRAMES;
            int l = 15728880;
            vc.addVertex(m, -0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, 0.5f, -0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, 0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(1f, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            vc.addVertex(m, -0.5f, 0.5f, 0f).setColor(255, 255, 255, 255).setUv(0f, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(l).setNormal(pose, 0f, 1f, 0f);
            ps.popPose();
            super.render(entity, yaw, pt, ps, buf, light);
        }
        @Override public ResourceLocation getTextureLocation(T entity) { return tex; }
    }

    private static class VRenderer<T extends com.example.roomsmod.entity.v.VEntity> extends EntityRenderer<T> {
        private final ResourceLocation tex;
        private final float size, wobble;
        private ResourceLocation eyes = null;
        private boolean outlineOnly = false;
        VRenderer<T> outlineOnly() { outlineOnly = true; return this; } // 本体は描かず、発光（デバッグ）時の輪郭だけ
        VRenderer<T> withEyes(String name) { eyes = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/" + name + ".png"); return this; }

        VRenderer(EntityRendererProvider.Context context, String name, float size, float wobble) {
            super(context);
            this.tex = ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "textures/entity/" + name + ".png");
            this.size = size; this.wobble = wobble;
            this.shadowRadius = 0.0f;
        }

        @Override
        public void render(T entity, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
            if (entity instanceof com.example.roomsmod.entity.v.V100Entity v && !v.isPeeking()) return;
            if (entity instanceof com.example.roomsmod.entity.v.V25BuffedEntity b && !b.isRevealed()) return; // 捜索開始までは実体なし
            if (outlineOnly && !entity.isCurrentlyGlowing()) return; // 輪郭のみ型：デバッグ以外では何も描かない
            float age = entity.tickCount + pt;
            ps.pushPose();
            ps.translate(0.0f, size * 0.5f, 0.0f);
            ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
            float s = size * (1.0f + Mth.sin(age * 0.5f) * wobble * 0.2f);
            ps.scale(s, s, s);
            VertexConsumer vc = buf.getBuffer(outlineOnly ? RenderType.outline(tex) : RenderType.entityTranslucentEmissive(tex)); // outline 型は輪郭バッファにだけ書かれる
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
