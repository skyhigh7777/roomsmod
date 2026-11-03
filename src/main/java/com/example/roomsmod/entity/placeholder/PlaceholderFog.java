package com.example.roomsmod.entity.placeholder;

import com.example.roomsmod.RoomsMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * PLACEHOLDER-2147483648 の青い霧（サーバー側）。生きている間、{@value #RANGE} ブロック以内（と狙っているプレイヤー）へ
 * {@value #INTERVAL} tick ごとに「霧あり」を送る（心拍）。消えるときは同じ次元の全員へ「霧なし」。
 * クライアント（client/PlaceholderFogClient）は心拍が {@value #TIMEOUT} tick 来なければ自分で霧を消すので、
 * 消える合図が届かなくても（チャンクの読み込み解除・サーバー停止など）霧は残らない。
 */
public final class PlaceholderFog {
    private PlaceholderFog() {}

    public static final double RANGE = 160.0;
    public static final int INTERVAL = 10, TIMEOUT = 40;

    public record FogPayload(boolean on) implements CustomPacketPayload {
        public static final Type<FogPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "placeholder_fog"));
        public static final StreamCodec<ByteBuf, FogPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, FogPayload::on, FogPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側の受け口（クライアント専用クラスに依存しない）。 */
    public static volatile java.util.function.Consumer<Boolean> clientHandler = b -> {};

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(FogPayload.TYPE, FogPayload.STREAM_CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(msg.on()))));
    }

    /** 生きている間、毎 tick から（INTERVAL ごとに送る）。 */
    static void heartbeat(ServerLevel level, Entity e, ServerPlayer target, int tick) {
        if (tick % INTERVAL != 0) return;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() && p != target) continue;
            if (p == target || p.distanceToSqr(e) <= RANGE * RANGE) PacketDistributor.sendToPlayer(p, new FogPayload(true));
        }
    }

    /** 消えるとき：同じ次元の全員へ「霧なし」（ほかに生きている PLACEHOLDER-2147483648 がいれば次の心拍でまた付く）。 */
    static void clear(ServerLevel level) {
        for (ServerPlayer p : level.players()) PacketDistributor.sendToPlayer(p, new FogPayload(false));
    }
}
