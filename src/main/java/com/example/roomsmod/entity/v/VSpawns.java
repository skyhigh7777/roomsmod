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
        V10("V-10", "v10", "err_v10", RoomsMod.V10),
        V25T("V-25-T", "v25t", "searcher", RoomsMod.V25T), V25L("V-25-L", "v25l", "searcher", RoomsMod.V25L),
        V25_BUFFED("V25-BUFFED", "v25buffed", "searcher,buffed_v100", RoomsMod.V25_BUFFED),
        V100("V-100", "v100", "buffed_v100", RoomsMod.V100), V50("V-50", "v50", "err_v50", RoomsMod.V50),
        // S（School、旧 GK）区画
        GK_CHIME("S-25", "schime", null, RoomsMod.GK_CHIME),
        GK_MIMIC("S-165", "smimic", "searcher", RoomsMod.GK_MIMIC),
        GK_MOTH("S-10", "smoth", null, RoomsMod.GK_MOTH),
        GK_STARE("S-205", "sstare", "gk_sense", RoomsMod.GK_STARE),
        GK_WHISPER("S-45", "swhisper", "gk_place", RoomsMod.GK_WHISPER),
        GK_DETENTION("S-65", "sdetention", "gk_door", RoomsMod.GK_DETENTION),
        GK_SUBSTITUTE("S-105", "ssubstitute", "gk_door", RoomsMod.GK_SUBSTITUTE),
        GK_ERASER("S-245", "seraser", "gk_rush", RoomsMod.GK_ERASER),
        GK_QUIZ("S-85", "squiz", "gk_place", RoomsMod.GK_QUIZ),
        GK_PA("S-185", "spa", "gk_place,gk_door", RoomsMod.GK_PA),
        GK_JANITOR("S-225", "sjanitor", "gk_rush", RoomsMod.GK_JANITOR),
        GK_MIRROR("S-270", "smirror", "gk_place,gk_rush", RoomsMod.GK_MIRROR),
        GK_07("S-125", "s07", "gk_back", RoomsMod.GK_07),
        GK_BELL2("S-300", "sbell2", "gk_rush", RoomsMod.GK_BELL2),
        GK_REWIND("S-145", "srewind", "gk_place,gk_back", RoomsMod.GK_REWIND),
        // B 区画
        B_GRIN("B-130", "bgrin", "b_rush", RoomsMod.B_GRIN),
        B_MASK("B-30", "bmask", null, RoomsMod.B_MASK),
        B_EYE("B-70", "beye", "b_still", RoomsMod.B_EYE),
        B_CHORUS("B-230", "bchorus", "b_rush", RoomsMod.B_CHORUS),
        B_STATIC("B-15", "bstatic", "b_rush", RoomsMod.B_STATIC),
        B_ERROR("B-110", "berror", "b_rush,b_still,gk_rush,err_v10,err_v50,err_b20,err_b40", RoomsMod.B_ERROR),
        B_ARGUS("B-180", "bargus", "b_rush", RoomsMod.B_ARGUS),
        B_MAW("B-155", "bmaw", "gk_door", RoomsMod.B_MAW),
        B_SCATTER("B-205", "bscatter", null, RoomsMod.B_SCATTER),
        B_REACH("B-50", "breach", "b_rush", RoomsMod.B_REACH),
        B_SMILE("B-90", "bsmile", "b_rush", RoomsMod.B_SMILE),
        B_VOID("B-260", "bvoid", "b_rush", RoomsMod.B_VOID),
        B_17("B-290", "b17", null, RoomsMod.B_17),
        // U（廊下）区画
        U20("U-20", "u20", null, RoomsMod.U20),
        U60("U-60", "u60", null, RoomsMod.U60),
        U120("U-120", "u120", null, RoomsMod.U120),
        U200("U-200", "u200", null, RoomsMod.U200),
        U300("U-300", "u300", null, RoomsMod.U300),
        // ハードモード専用（どのセクションにも属さない。Sections.hardTable にだけ入る）
        E332("3!3!2", "mimic", null, RoomsMod.E332),
        // PLACEHOLDER（テスト用）区画：区画の表は空。32767 / 65535 はハードの抽選表（Sections.HARD_ONLY）にだけ入る。2147483648 は /rooms spawn と 3!3!2 のみ
        PLACEHOLDER_32767("PLACEHOLDER-32767", "placeholder32767", null, RoomsMod.PLACEHOLDER_32767),
        PLACEHOLDER_65535("PLACEHOLDER-65535", "placeholder65535", null, RoomsMod.PLACEHOLDER_65535),
        PLACEHOLDER_2147483648("PLACEHOLDER-2147483648", "placeholder2147483648", null, RoomsMod.PLACEHOLDER_2147483648),
        // コマンド専用（どの抽選表にも 3!3!2 の候補にも入らない）。表示名は ExtraboxEntity.NAME、別名 EXTRABOX
        EXTRABOX(com.example.roomsmod.entity.hard.ExtraboxEntity.NAME, "extrabox", null, RoomsMod.EXTRABOX);
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
            // 3!3!2 は同時に何体いてもよい（抽選・/rooms spawn とも生存チェックなし。ボスバーは1体ごと）
            if (this == E332 || this == EXTRABOX) return false; // EXTRABOX も同時に何体でも
            // B-110 は B-20 / B-40（Kind ではない）とも同時に出さない
            if (this == B_ERROR && (com.example.roomsmod.event.DoorEventHandler.isA20Alive(level) || com.example.roomsmod.event.DoorEventHandler.isA40Alive(level))) return true;
            for (Kind k : values())
                if ((k == this || sharesGroup(exclusionGroup, k.exclusionGroup)) && isAlive(level, k.type.get())) return true;
            return false;
        }
        public static Kind byDisplay(String name) { for (Kind k : values()) if (k.display.equals(name)) return k; return null; }
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
        for (Entity e : level.getAllEntities()) if (e.getType() == type && VEntity.isActive(e)) return true; // 止まった古い個体は数えない
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
        if (com.example.roomsmod.entity.placeholder.Placeholder2147483648Entity.blocksSpawns(level)) { RoomsDebug.log("{} spawn blocked: PLACEHOLDER-2147483648 alive", k.display); return false; }
        int room = playerRoom(rs, player);
        // A の縦穴：通常は A 専用の敵だけ、ハードは縦に追ってこられる種類だけ（コマンドは制限なし）
        if (com.example.roomsmod.world.AbyssSpace.blockSpawn(level, rs, player, room, k.display)) return false;
        VEntity e = k.type.get().create(level);
        if (e == null) { RoomsDebug.warn("{} spawn failed: create returned null", k.display); return false; }
        if (e.targetsOpenedRoom()) room = Math.min(room + 1, rs.roomPaths.size() - 1);
        String home = e.homeSection();
        boolean off = home != null && !home.equals(com.example.roomsmod.world.section.Sections.forRoom(rs, room).id());
        e.setSpawnContext(rs.hardMode, off);
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
