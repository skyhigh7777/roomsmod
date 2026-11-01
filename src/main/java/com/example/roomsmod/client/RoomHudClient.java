package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 左下の部屋番号 HUD（ノーマル・ハード共通、ハードは赤い ☠ を前に付ける）。
 * 位置：画面の左端 x=4、下端から 14（ホットバーの最下段の高さ）。チャットの行は下端から 40 より上に出るので重ならない。
 * チャット入力欄（ChatScreen）が開いている間は入力欄と重なるので隠す。オフハンド枠（ホットバーの左）より左に収まる幅。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class RoomHudClient {
    private RoomHudClient() {}
    private static String label = "";
    private static boolean hard;

    public static void set(String l, boolean h) { label = l == null ? "" : l; hard = h; }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Post e) {
        if (!e.getName().equals(VanillaGuiLayers.HOTBAR) || label.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen instanceof ChatScreen) return;
        if (!mc.level.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        GuiGraphics g = e.getGuiGraphics();
        String skull = hard ? "☠ " : "";
        int w = mc.font.width(skull + label);
        int x = 4, y = g.guiHeight() - 14;
        g.fill(x - 2, y - 2, x + w + 2, y + 10, 0x80000000);
        int tx = x;
        if (hard) tx = g.drawString(mc.font, skull, tx, y, 0xFFFF4040, true);
        g.drawString(mc.font, label, tx, y, 0xFFFFFFFF, true);
    }
}
