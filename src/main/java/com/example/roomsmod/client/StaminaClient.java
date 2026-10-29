package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.stamina.ClientStamina;
import com.example.roomsmod.stamina.Stamina;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 息切れ中のダッシュ禁止（クライアント側）。
 * サバイバルは満腹度固定でバニラが止めるが、飛行可能（クリエイティブ）だと満腹度に関係なく走れるので、
 * プレイヤーの tick より前にダッシュキーを離した扱いにしてダッシュを解除する。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class StaminaClient {
    private StaminaClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isSpectator() || !ClientStamina.exhausted || !Stamina.inRooms(p)) return;
        mc.options.keySprint.setDown(false);
        if (p.isSprinting()) p.setSprinting(false);
    }
}
