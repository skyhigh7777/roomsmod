package com.example.roomsmod.economy;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レーダー（キー既定 K）とスキャナー（キー既定 N）。所持数から1つ使う。部屋ディメンションのみ。調整はここだけ。
 * <ul>
 *   <li>レーダー：RADAR_TICKS（10 秒）の間、RANGE ブロック以内のこの MOD の敵（MONSTER）を使った本人にだけ発光（壁越しの輪郭）で見せる。
 *       クライアント側の発光フラグなので他の人には見えない。効果中に出た敵も RADAR_REFRESH ごとに追加。姿を描かない敵は輪郭も出ない。</li>
 *   <li>スキャナー：RANGE ブロック以内の敵の一覧（名前・部屋・距離、近い順に最大 SCAN_MAX 件）を SCAN_TICKS（5 秒）画面に出す。</li>
 * </ul>
 */
public final class Gadgets {
    private Gadgets() {}

    public static final int RANGE = 64;
    public static final int RADAR_TICKS = 200, RADAR_REFRESH = 10;
    public static final int SCAN_TICKS = 100, SCAN_MAX = 12;
    public static final int RADAR = 0, SCANNER = 1;

    /** クライアント → サーバー：使う（kind = RADAR / SCANNER）。 */
    public record UsePayload(int kind) implements CustomPacketPayload {
        public static final Type<UsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "use_gadget"));
        public static final StreamCodec<ByteBuf, UsePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, UsePayload::kind, UsePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** サーバー → クライアント：発光させるエンティティ id と残り tick。 */
    public record RadarPayload(List<Integer> ids, int ticks) implements CustomPacketPayload {
        public static final Type<RadarPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "radar"));
        public static final StreamCodec<ByteBuf, RadarPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), RadarPayload::ids, ByteBufCodecs.VAR_INT, RadarPayload::ticks, RadarPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** サーバー → クライアント：スキャン結果（1 行 = 名前 \t 部屋 \t 距離）と表示 tick。 */
    public record ScanPayload(List<String> lines, int ticks) implements CustomPacketPayload {
        public static final Type<ScanPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "scan"));
        public static final StreamCodec<ByteBuf, ScanPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ScanPayload::lines, ByteBufCodecs.VAR_INT, ScanPayload::ticks, ScanPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static final Map<UUID, Long> RADAR_UNTIL = new ConcurrentHashMap<>();

    static void use(ServerPlayer p, int kind) {
        if (!p.isAlive() || p.isSpectator() || !p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        Wallet w = Wallet.of(p);
        if (kind == RADAR) {
            if (w.radar <= 0) { deny(p, "message.roomsmod.radar.none"); return; }
            if (radarTicks(p) > 0) { deny(p, "message.roomsmod.radar.active"); return; }
            w.radar--;
            RADAR_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + RADAR_TICKS);
            List<Integer> ids = sendRadar(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
            p.displayClientMessage(Component.translatable("message.roomsmod.radar.used", RADAR_TICKS / 20, ids.size()).withStyle(ChatFormatting.AQUA), true);
            Wallet.sync(p);
            RoomsDebug.log("radar used player={} left={} targets={}", p.getGameProfile().getName(), w.radar, ids.size());
        } else if (kind == SCANNER) {
            if (w.scanner <= 0) { deny(p, "message.roomsmod.scanner.none"); return; }
            w.scanner--;
            List<String> lines = scan(p);
            PacketDistributor.sendToPlayer(p, new ScanPayload(lines, SCAN_TICKS));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
            Wallet.sync(p);
            RoomsDebug.log("scanner used player={} left={} results={}", p.getGameProfile().getName(), w.scanner, lines.size());
        }
    }

    private static void deny(ServerPlayer p, String key) { p.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true); }

    public static int radarTicks(ServerPlayer p) {
        Long u = RADAR_UNTIL.get(p.getUUID());
        return u == null ? 0 : (int) Math.max(0, u - p.serverLevel().getGameTime());
    }

    /** 近くのこの MOD の敵（動いているもの）。 */
    static List<Entity> enemies(ServerPlayer p) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : p.serverLevel().getAllEntities()) {
            if (e.getType().getCategory() != MobCategory.MONSTER || e.isRemoved() || !e.isAlive()) continue;
            if (!RoomsMod.MOD_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) continue;
            if (!com.example.roomsmod.entity.v.VEntity.isActive(e)) continue;
            if (e.distanceToSqr(p) > (double) RANGE * RANGE) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        return out;
    }

    private static List<Integer> sendRadar(ServerPlayer p) {
        List<Integer> ids = new ArrayList<>();
        for (Entity e : enemies(p)) ids.add(e.getId());
        PacketDistributor.sendToPlayer(p, new RadarPayload(ids, radarTicks(p)));
        return ids;
    }

    static String name(Entity e) {
        return e instanceof com.example.roomsmod.entity.v.VEntity v ? v.vName() : e.getDisplayName().getString();
    }

    private static List<String> scan(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        RoomsState rs = RoomsState.get(level);
        List<String> lines = new ArrayList<>();
        for (Entity e : enemies(p)) {
            if (lines.size() >= SCAN_MAX) break;
            int idx = RoomTracker.roomIndexAt(rs, e.blockPosition());
            String room = idx < 0 ? "?" : idx == 0 ? "LOBBY" : Sections.formatRoom(rs, idx);
            lines.add(name(e) + "\t" + room + "\t" + Math.round(Math.sqrt(e.distanceToSqr(p))) + "m");
        }
        return lines;
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !RADAR_UNTIL.containsKey(p.getUUID())) return;
        if (radarTicks(p) <= 0 || !p.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) {
            RADAR_UNTIL.remove(p.getUUID());
            PacketDistributor.sendToPlayer(p, new RadarPayload(List.of(), 0));
            return;
        }
        if (p.tickCount % RADAR_REFRESH == 0) sendRadar(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { RADAR_UNTIL.remove(e.getEntity().getUUID()); }
}
