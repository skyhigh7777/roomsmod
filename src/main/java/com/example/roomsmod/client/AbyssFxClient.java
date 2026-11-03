package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.world.AbyssFx;
import com.example.roomsmod.world.AbyssRooms;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * A-525 の暗転（クライアント側）。地形・敵を描いたあと、半透明の粒を描く前に画面いっぱいの黒を重ねる：
 * 光る粒（END_ROD・WAX_ON など半透明の粒）だけが見える。手・HUD はそのまま。
 * 普通の描画（Fast / Fancy）では糸の層のあと（不透明の粒は黒の下になる）、Fabulous では粒が別の描画先なので地形の直後に重ねる。
 * 縦穴の外（AbyssRooms.clientActive でない）では描かない。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class AbyssFxClient {
    private AbyssFxClient() {}

    /** 入り・明けのフェード（tick）。 */
    private static final int FADE = 3;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (AbyssFx.clientDarkLeft > 0) AbyssFx.clientDarkLeft--;
    }

    /** 今の黒の濃さ（0..1）。 */
    private static float alpha(float pt) {
        int left = AbyssFx.clientDarkLeft, total = AbyssFx.clientDarkTotal;
        if (left <= 0 || total <= 0 || !AbyssRooms.clientActive) return 0f;
        float elapsed = total - left + pt, rest = left - pt;
        float a = Math.min(1f, Math.min(elapsed / FADE, rest / FADE));
        return Math.max(0f, a);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent e) {
        boolean fab = Minecraft.useShaderTransparency();
        if (e.getStage() != (fab ? RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES : RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS)) return;
        float a = alpha(e.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (a <= 0f) return;
        // 画面いっぱいの黒（正規化デバイス座標で直接描く。深度は書かない＝後で描く粒は地形の前後関係のまま）
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        int al = Math.round(a * 255f);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(-1f, -1f, 0f).setColor(0, 0, 0, al);
        b.addVertex(1f, -1f, 0f).setColor(0, 0, 0, al);
        b.addVertex(1f, 1f, 0f).setColor(0, 0, 0, al);
        b.addVertex(-1f, 1f, 0f).setColor(0, 0, 0, al);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { AbyssFx.clientDarkLeft = 0; AbyssFx.clientDarkTotal = 0; }
}
