package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.world.AbyssRooms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A（Abyss）の縦穴の中の落下速度の上限（クライアント側。プレイヤーの移動はクライアントが決めるため）。
 * 約 12 ブロック/秒、スニーク中はその半分。サーバーが AbyssPayload で縦穴の中かを知らせる。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class AbyssClient {
    private AbyssClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !AbyssRooms.clientActive || p.isSpectator() || p.getAbilities().flying) return;
        double cap = p.isShiftKeyDown() ? AbyssRooms.MAX_FALL_SNEAK : AbyssRooms.MAX_FALL;
        Vec3 v = p.getDeltaMovement();
        if (v.y < -cap) p.setDeltaMovement(v.x, -cap, v.z);
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { AbyssRooms.clientActive = false; }
}
