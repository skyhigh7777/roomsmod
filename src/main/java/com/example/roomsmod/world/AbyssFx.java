package com.example.roomsmod.world;

import com.example.roomsmod.RoomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A（Abyss）の画面の演出（サーバー → クライアント）。今は暗転（A-525）だけ。
 * 暗転の間、クライアントは光る粒（END_ROD など半透明の粒）以外を真っ黒にする（client/AbyssFxClient）。
 * 長さはクライアントが数えるので、送りそこねても必ず元に戻る（0 を送ればすぐ戻る）。
 */
public final class AbyssFx {
    private AbyssFx() {}

    /** 暗転の長さの上限（tick）。これより長い値は切り詰める（取り残し防止）。 */
    public static final int MAX_DARK = 200;

    /** サーバー → クライアント：ticks tick のあいだ暗転（0 で解除）。 */
    public record DarkPayload(int ticks) implements CustomPacketPayload {
        public static final Type<DarkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "abyss_dark"));
        public static final StreamCodec<ByteBuf, DarkPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, DarkPayload::ticks, DarkPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側：暗転の残り tick と、始めたときの長さ（フェード用）。 */
    public static volatile int clientDarkLeft = 0, clientDarkTotal = 0;

    static void onClientDark(int ticks) {
        int t = Math.max(0, Math.min(MAX_DARK, ticks));
        clientDarkLeft = t;
        clientDarkTotal = t;
    }

    /** プレイヤー sp の画面を ticks tick 暗転（0 で解除）。 */
    public static void dark(ServerPlayer sp, int ticks) { PacketDistributor.sendToPlayer(sp, new DarkPayload(ticks)); }
}
