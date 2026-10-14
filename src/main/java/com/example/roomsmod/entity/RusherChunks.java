package com.example.roomsmod.entity;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketSet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ラッシャーのチャンク強制読み込み。NeoForge のチケットをエンティティ（UUID）ごとに持つので、
 * 別のラッシャーが離れても他者のチャンクは外れない（旧実装は共有の setChunkForced を上書き解除していた）。
 * チケットはワールドに保存される。ロード時に持ち主がいなくなっていたチケットは少し後に片付ける。
 */
public final class RusherChunks {
    /** ラッシャー中心から何チャンク分（半径）を読み込むか。 */
    public static final int RADIUS = 1;
    private static final int STALE_CHECK_DELAY_TICKS = 200;

    private static final Map<ServerLevel, Map<UUID, TicketSet>> LOADED = new HashMap<>();
    private static final Map<ServerLevel, Long> LOADED_AT = new HashMap<>();

    public static final TicketController CONTROLLER = new TicketController(
            ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "rushers"),
            (level, helper) -> {
                if (helper.getEntityTickets().isEmpty()) return;
                LOADED.put(level, new HashMap<>(helper.getEntityTickets()));
                LOADED_AT.put(level, level.getGameTime());
                RoomsDebug.log("rusher tickets restored dim={} owners={}", level.dimension().location(), helper.getEntityTickets().size());
            });

    private RusherChunks() {}

    /** center を中心に (2R+1)^2 を owner のチケットで保持する。 */
    public static void force(ServerLevel level, Entity owner, ChunkPos center, boolean add) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
                CONTROLLER.forceChunk(level, owner, center.x + dx, center.z + dz, add, true);
    }

    /** 移動：新しい範囲を先に確保してから、古い範囲のうち新しい範囲に含まれないものだけ外す。 */
    public static void move(ServerLevel level, Entity owner, ChunkPos old, ChunkPos now) {
        if (now.equals(old)) return;
        force(level, owner, now, true);
        if (old == null) return;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = old.x + dx, z = old.z + dz;
                if (Math.abs(x - now.x) <= RADIUS && Math.abs(z - now.z) <= RADIUS) continue;
                CONTROLLER.forceChunk(level, owner, x, z, false, true);
            }
    }

    /** 出現前：チケットを張り、中心チャンクを同期的に読み込む（未読込のセクションに追加されて保存行きになるのを防ぐ）。 */
    public static void prepareSpawn(ServerLevel level, Entity owner, ChunkPos center) {
        force(level, owner, center, true);
        level.getChunk(center.x, center.z);
    }

    /** ロード後しばらくして、持ち主がワールドにいないチケットを外す（クラッシュ等の取り残し対策）。 */
    public static void cleanupStale(ServerLevel level) {
        Map<UUID, TicketSet> m = LOADED.get(level);
        if (m == null || level.getGameTime() - LOADED_AT.get(level) < STALE_CHECK_DELAY_TICKS) return;
        LOADED.remove(level);
        LOADED_AT.remove(level);
        for (var e : m.entrySet()) {
            if (level.getEntity(e.getKey()) != null) continue;
            for (var set : new it.unimi.dsi.fastutil.longs.LongSet[]{e.getValue().ticking(), e.getValue().nonTicking()})
                for (long packed : set) {
                    ChunkPos c = new ChunkPos(packed);
                    CONTROLLER.forceChunk(level, e.getKey(), c.x, c.z, false, true);
                    CONTROLLER.forceChunk(level, e.getKey(), c.x, c.z, false, false);
                }
            RoomsDebug.log("rusher tickets removed for missing owner {}", e.getKey());
        }
    }
}
