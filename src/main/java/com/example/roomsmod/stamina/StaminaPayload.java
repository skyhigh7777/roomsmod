package com.example.roomsmod.stamina;

import com.example.roomsmod.RoomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** サーバー → クライアント：スタミナ値（0〜100）と息切れ状態。 */
public record StaminaPayload(float value, boolean exhausted) implements CustomPacketPayload {
    public static final Type<StaminaPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "stamina"));
    public static final StreamCodec<ByteBuf, StaminaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaPayload::value, ByteBufCodecs.BOOL, StaminaPayload::exhausted, StaminaPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
