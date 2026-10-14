package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.block.LockerBlock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * ロッカー内のクライアント処理：移動入力を止め（スニークは退出用に残す）、スリット付きの暗いビネットを描く。
 * 「中にいるか」はサーバー同期せず、足元がロッカー下段かどうかで判定する（中へは壁で入れないため）。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class LockerClient {
    private LockerClient() {}

    public static boolean isLocalPlayerInLocker() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        BlockState st = p.level().getBlockState(p.blockPosition());
        return st.getBlock() instanceof LockerBlock && st.getValue(LockerBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    /** 足元が机なら机の下にいる（立った姿勢では天板に当たって入れないため）。 */
    public static boolean isLocalPlayerUnderTable() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.level().getBlockState(p.blockPosition()).getBlock() instanceof com.example.roomsmod.block.HidingTableBlock;
    }

    private static boolean forcedByUs = false;

    /** クライアント側でも這い姿勢を合わせ、サーバーとの姿勢ずれでガタつかないようにする。 */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { forcedByUs = false; return; }
        if (isLocalPlayerUnderTable()) {
            p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
            forcedByUs = true;
        } else if (forcedByUs) {
            p.setForcedPose(null);
            forcedByUs = false;
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!isLocalPlayerInLocker() && !isLocalPlayerUnderTable()) return;
        Input in = event.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "locker_overlay"), LockerClient::renderOverlay);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "table_overlay"), LockerClient::renderTableOverlay);
    }

    /** 机の下：ロッカーより明るめ。上は天板の影、中央に横長の視界、下（床側）を暗く。 */
    private static void renderTableOverlay(GuiGraphics g, DeltaTracker delta) {
        if (!isLocalPlayerUnderTable() || Minecraft.getInstance().options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        int top = (int) (h * 0.30), bottom = (int) (h * 0.62);
        g.fill(0, 0, w, top, 0xC0080604);          // 天板
        g.fill(0, bottom, w, h, 0xA0000000);       // 下側
        for (int i = 0; i < 6; i++) {              // スリット上下のぼかし
            int a = 0x14 * (6 - i);
            g.fill(0, top + i * 3, w, top + i * 3 + 3, a << 24);
            g.fill(0, bottom - (i + 1) * 3, w, bottom - i * 3, a << 24);
        }
        g.fill(0, top, w / 12, bottom, 0x70000000); // 左右の脚の影
        g.fill(w - w / 12, top, w, bottom, 0x70000000);
    }

    private static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (!isLocalPlayerInLocker() || Minecraft.getInstance().options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        final int dark = 0xF0050505;
        // スリット：画面中央付近に横長の隙間を3本
        int slitW = (int) (w * 0.42), slitH = Math.max(3, h / 40), gap = Math.max(4, h / 22);
        int sx = (w - slitW) / 2;
        int total = 3 * slitH + 2 * gap;
        int sy = (int) (h * 0.42) - total / 2;
        // スリット以外を塗る
        g.fill(0, 0, w, sy, dark);
        for (int i = 0; i < 3; i++) {
            int top = sy + i * (slitH + gap);
            g.fill(0, top, sx, top + slitH, dark);
            g.fill(sx + slitW, top, w, top + slitH, dark);
            // スリット縁のやや暗いぼかし
            g.fill(sx, top, sx + slitW, top + 1, 0x80000000);
            g.fill(sx, top + slitH - 1, sx + slitW, top + slitH, 0x80000000);
            if (i < 2) g.fill(0, top + slitH, w, top + slitH + gap, dark);
        }
        g.fill(0, sy + total, w, h, dark);
        // スリット両端を暗く（ビネット）
        int fade = slitW / 4;
        for (int i = 0; i < 8; i++) {
            int a = 0x18 * (8 - i);
            int x0 = sx + i * fade / 8, x1 = sx + slitW - (i + 1) * fade / 8;
            g.fill(x0, sy, x0 + fade / 8, sy + total, (a << 24));
            g.fill(x1, sy, x1 + fade / 8, sy + total, (a << 24));
        }
    }
}
