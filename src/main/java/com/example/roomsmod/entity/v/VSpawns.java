package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.RusherChunks;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.function.Supplier;

/** V エンティティの出現処理・生存確認（同種は同時に1体まで）。 */
public final class VSpawns {
    private VSpawns() {}

    /** 名前 → (エンティティ型, 生存確認, 出現)。コマンドとセクション表で共用。名前を変えるならここと Sections.V。 */
    public enum Kind {
        V10("V-10", "v10", null, RoomsMod.V10),
        V25T("V-25-T", "v25t", "searcher", RoomsMod.V25T), V25L("V-25-L", "v25l", "searcher", RoomsMod.V25L),
        V25_BUFFED("V25-BUFFED", "v25buffed", "searcher,buffed_v100", RoomsMod.V25_BUFFED),
        V100("V-100", "v100", "buffed_v100", RoomsMod.V100), V50("V-50", "v50", null, RoomsMod.V50),
        // GK（学校）区画
        GK_CHIME("GK-Chime", "gkchime", null, RoomsMod.GK_CHIME),
        GK_MIMIC("GK-Mimic", "gkmimic", "searcher", RoomsMod.GK_MIMIC),
        GK_MOTH("GK-Moth", "gkmoth", null, RoomsMod.GK_MOTH),
        GK_STARE("GK-12", "gk12", "gk_sense", RoomsMod.GK_STARE),
        GK_WHISPER("GK-Whisper", "gkwhisper", "gk_place", RoomsMod.GK_WHISPER),
        GK_DETENTION("GK-Detention", "gkdetention", "gk_door", RoomsMod.GK_DETENTION),
        GK_SUBSTITUTE("GK-Substitute", "gksubstitute", "gk_door", RoomsMod.GK_SUBSTITUTE),
        GK_ERASER("GK-Eraser", "gkeraser", "gk_rush", RoomsMod.GK_ERASER),
        GK_QUIZ("GK-Quiz", "gkquiz", "gk_place", RoomsMod.GK_QUIZ),
        GK_PA("GK-PA", "gkpa", "gk_place,gk_door", RoomsMod.GK_PA),
        GK_JANITOR("GK-Janitor", "gkjanitor", "gk_rush", RoomsMod.GK_JANITOR),
        GK_MIRROR("GK-Mirror", "gkmirror", "gk_place,gk_rush", RoomsMod.GK_MIRROR),
        GK_07("GK-07", "gk07", "gk_back", RoomsMod.GK_07),
        GK_BELL2("GK-Bell2", "gkbell2", "gk_rush", RoomsMod.GK_BELL2),
        GK_REWIND("GK-Rewind", "gkrewind", "gk_place,gk_back", RoomsMod.GK_REWIND),
        // B 区画
        B_GRIN("B-Grin", "bgrin", "b_motion", RoomsMod.B_GRIN),
        B_MASK("B-Mask", "bmask", null, RoomsMod.B_MASK),
        B_EYE("B-Eye", "beye", null, RoomsMod.B_EYE),
        B_CHORUS("B-Chorus", "bchorus", null, RoomsMod.B_CHORUS),
        B_STATIC("B-Static", "bstatic", "b_rush", RoomsMod.B_STATIC),
        B_ERROR("B-Error", "berror", null, RoomsMod.B_ERROR),
        B_ARGUS("B-Argus", "bargus", "b_motion", RoomsMod.B_ARGUS),
        B_MAW("B-Maw", "bmaw", "gk_door", RoomsMod.B_MAW),
        B_SCATTER("B-Scatter", "bscatter", null, RoomsMod.B_SCATTER),
        B_REACH("B-Reach", "breach", "b_rush", RoomsMod.B_REACH),
        B_SMILE("B-Smile", "bsmile", "b_rush", RoomsMod.B_SMILE),
        B_VOID("B-Void", "bvoid", null, RoomsMod.B_VOID),
        B_17("B-17", "b17", null, RoomsMod.B_17);
        public final String display, id;
        /**
         * 排他グループ（null=なし）。同じグループの種類は同時に1体まで（例：V-25-T と V-25-L は一緒に出ない）。
         * カンマ区切りで複数のグループに入れる（V25-BUFFED は searcher と buffed_v100：V-25-T/L とも V-100 とも同時に出ない）。
         * それ以外は通常ルール（同種は1体まで、別種は共存可）。
         */
        public final String exclusionGroup;
        public final Supplier<? extends EntityType<? extends VEntity>> type;
        Kind(String display, String id, String group, Supplier<? extends EntityType<? extends VEntity>> type) { this.display = display; this.id = id; this.exclusionGroup = group; this.type = type; }
        /** 同種、または同じ排他グループの種類が生存中か。 */
        public boolean alive(ServerLevel level) {
            for (Kind k : values())
                if ((k == this || sharesGroup(exclusionGroup, k.exclusionGroup)) && isAlive(level, k.type.get())) return true;
            return false;
        }
        public static Kind byId(String id) { for (Kind k : values()) if (k.id.equals(id)) return k; return null; }
        public boolean spawn(ServerLevel level, RoomsState rs, Player player) { return VSpawns.spawn(this, level, rs, player); }
    }

    /** 排他グループ（カンマ区切り）が1つでも共通か。 */
    public static boolean sharesGroup(String a, String b) {
        if (a == null || b == null) return false;
        for (String x : a.split(",")) for (String y : b.split(",")) if (x.trim().equals(y.trim())) return true;
        return false;
    }

    public static boolean isAlive(ServerLevel level, EntityType<?> type) {
        for (Entity e : level.getAllEntities()) if (e.getType() == type && e.isAlive()) return true;
        return false;
    }

    /** プレイヤーのいる部屋 index。プレイヤーがいない／部屋外なら「最新の部屋の1つ手前」（扉を開けた直後の位置）。 */
    public static int playerRoom(RoomsState rs, Player player) {
        if (player != null) {
            int i = RoomTracker.roomIndexAt(rs, player.blockPosition());
            if (i >= 0) return i;
        }
        return Math.max(0, rs.roomPaths.size() - 2);
    }

    static boolean spawn(Kind k, ServerLevel level, RoomsState rs, Player player) {
        if (rs.roomPaths.isEmpty()) { RoomsDebug.warn("{} spawn skipped: no rooms", k.display); return false; }
        int room = playerRoom(rs, player);
        VEntity e = k.type.get().create(level);
        if (e == null) { RoomsDebug.warn("{} spawn failed: create returned null", k.display); return false; }
        if (e.targetsOpenedRoom()) room = Math.min(room + 1, rs.roomPaths.size() - 1);
        BlockPos at = e.prepare(level, rs, room);
        if (at == null) { RoomsDebug.log("{} spawn skipped: no valid position near room {}", k.display, room); return false; }
        RusherChunks.prepareSpawn(level, e, new ChunkPos(at));
        if (!level.addFreshEntity(e)) { RoomsDebug.warn("{} spawn failed: addFreshEntity returned false", k.display); return false; }
        RoomsDebug.log(player, k.display + " が出現したわ（" + RoomTracker.format(rs, room) + "）",
                "{} spawn playerRoom={} ({}) pos=({}, {}, {}) {}", k.display, room, RoomTracker.format(rs, room),
                VEntity.f(e.getX()), VEntity.f(e.getY()), VEntity.f(e.getZ()), e.debugState());
        return true;
    }

    static List<BlockPos> path(RoomsState rs, int room) {
        return room >= 0 && room < rs.roomPaths.size() ? rs.roomPaths.get(room) : List.of();
    }
}
