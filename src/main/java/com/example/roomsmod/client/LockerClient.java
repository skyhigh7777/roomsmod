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
 * ロッカー内のクライアント処理：移動入力を止め（スニークは退出用に残す）、暗いビネットを描く（外は扉の本物のスリットから見える）。
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
            // 水の中（F）だと浮力で上下に動き、サーバーの位置固定と引っ張り合って震える。クライアントでも机の中央に固定する。
            if (p.isInWater()) {
                net.minecraft.core.BlockPos b = p.blockPosition();
                p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                p.setPos(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            }
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

    /**
     * ロッカーの中：外はロッカーの扉の本物の隙間（locker_upper モデルの3本のスリット）から見える。
     * ここでは全体を少し暗くして、画面の縁へ向かって濃くなるビネットだけ描く（以前は画面のほぼ全部を塗っていて外が見えなかった）。
     */
    private static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (!isLocalPlayerInLocker() || Minecraft.getInstance().options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, 0x30000000);
        int steps = 12, bw = Math.max(2, w / 48), bh = Math.max(2, h / 32);
        for (int i = 0; i < steps; i++) {
            int a = (0x16 * (steps - i)) / 2;
            int c = Math.min(0xFF, a) << 24;
            g.fill(i * bw, i * bh, w - i * bw, i * bh + bh, c);            // 上
            g.fill(i * bw, h - i * bh - bh, w - i * bw, h - i * bh, c);    // 下
            g.fill(i * bw, i * bh + bh, i * bw + bw, h - i * bh - bh, c);  // 左
            g.fill(w - i * bw - bw, i * bh + bh, w - i * bw, h - i * bh - bh, c); // 右
        }
    }
}
