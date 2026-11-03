package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.placeholder.PlaceholderFog;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * PLACEHOLDER-2147483648 の青い霧（クライアント側）。ネザーの濃い霧のように、見える距離を {@value #FAR} ブロックまで縮め、霧の色を青に。
 * 強さ 0..1 を {@value #FADE_IN} tick で上げ、{@value #FADE_OUT} tick で下げる（なめらかに出入り）。粒は出さない（描画の負担なし）。
 * 消える：サーバーの「霧なし」、心拍が {@link PlaceholderFog#TIMEOUT} tick 来ない、ログアウト、次元（ワールド）が変わったとき。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class PlaceholderFogClient {
    private PlaceholderFogClient() {}

    public static final float NEAR = 0.0f, FAR = 14.0f;
    public static final int FADE_IN = 40, FADE_OUT = 50;
    /** 霧の色（濃い青。歪んだ森・ソウルサンドの谷寄り）。 */
    static final float R = 0.06f, G = 0.16f, B = 0.42f;

    private static boolean want = false;
    private static int sinceBeat = 0;
    private static float strength = 0f, prevStrength = 0f;
    private static Object lastLevel = null;

    static { PlaceholderFog.clientHandler = on -> { want = on; sinceBeat = 0; }; }

    /** ClientSetup から（クラスを読み込んで受け口をつなぐ）。 */
    public static void init() {}

    private static void reset() { want = false; sinceBeat = 0; strength = 0f; prevStrength = 0f; }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { lastLevel = mc.level; reset(); } // 次元・ワールドが変わった
        if (mc.level == null) return;
        if (want && ++sinceBeat > PlaceholderFog.TIMEOUT) want = false; // 心拍が来ない：消えた・離れた
        prevStrength = strength;
        strength = want ? Math.min(1f, strength + 1f / FADE_IN) : Math.max(0f, strength - 1f / FADE_OUT);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { reset(); lastLevel = null; }

    private static float k(double partial) {
        float s = Mth.lerp((float) partial, prevStrength, strength);
        return s * s * (3 - 2 * s); // なめらかに
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog e) {
        if (strength <= 0f && prevStrength <= 0f) return;
        float s = k(e.getPartialTick());
        if (s <= 0.001f) return;
        e.setNearPlaneDistance(Mth.lerp(s, e.getNearPlaneDistance(), NEAR));
        e.setFarPlaneDistance(Mth.lerp(s, e.getFarPlaneDistance(), Math.min(FAR, e.getFarPlaneDistance())));
        if (s > 0.5f) e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true); // 変更を使う
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        if (strength <= 0f && prevStrength <= 0f) return;
        float s = k(e.getPartialTick());
        e.setRed(Mth.lerp(s, e.getRed(), R));
        e.setGreen(Mth.lerp(s, e.getGreen(), G));
        e.setBlue(Mth.lerp(s, e.getBlue(), B));
    }
}
