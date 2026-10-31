package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * タイトル画面。背景のパノラマは assets/minecraft/textures/gui/title/background/panorama_0..5.png（このMODの廊下）で
 * バニラのものを差し替える（リソースの上書き。リソースパックが上に来ればそちらが優先、コードでは何も触らない）。
 * ここでは左上に MOD 名を薄く描くだけ（ボタン・ほかの画面には触らない）。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class TitleScreenClient {
    private TitleScreenClient() {}
    public static final String TITLE = "SRT's Rooms : Deep Corridors";

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof TitleScreen s)) return;
        e.getGuiGraphics().drawString(s.getMinecraft().font, TITLE, 4, 4, 0x99FFFFFF, true);
    }
}
