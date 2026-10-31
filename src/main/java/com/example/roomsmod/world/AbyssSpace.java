package com.example.roomsmod.world;

import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.Sections;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A（Abyss）の縦穴の「伸ばした（仮想の）座標」。縦穴は同じ見た目の層をループしているだけなので、
 * 部屋 r の層1の y を (r - 最初の部屋) * L ブロック下へずらした座標で敵の位置を持つ（深いほど y が小さい、途切れない 1 本の縦穴）。
 * <ul>
 *   <li>仮想 → 実際：y + (先頭の部屋 - 最初の部屋) * L。縦穴の見えている範囲（層0〜底）の外なら、上は小部屋の中・下は底の床の下へ隠す。</li>
 *   <li>プレイヤーが 1 周（L ブロック上へテレポート）すると先頭の部屋が 1 つ進むので、敵の実際の位置も同時に L 上がる。
 *       {@link #afterLoop} がその場で敵を動かし、クライアントにも同じだけずらすよう知らせる（パーティクルも一緒に）。</li>
 *   <li>縦穴の外（ほかの区画の部屋）では仮想 = 実際。</li>
 * </ul>
 */
public final class AbyssSpace {
    private AbyssSpace() {}

    /** 前方（下）に出る・折り返すとき、プレイヤーの部屋から最低何部屋奥にするか。 */
    public static final int AHEAD_ROOMS = 3;

    public static AbyssRooms.Shaft shaft(RoomsState rs) { return rs == null ? null : rs.abyss; }

    /** 今ブロックとして見えている層1の部屋（先頭）。 */
    public static int lead(RoomsState rs) {
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null) return 0;
        if (s.lead == Integer.MIN_VALUE)
            s.lead = Sections.forRoom(rs, rs.currentRoom) == Sections.A && rs.currentRoom >= s.entryRoom ? rs.currentRoom : s.entryRoom;
        return s.lead;
    }

    /** 縦穴の水平範囲（壁を含む）。 */
    public static boolean inColumn(AbyssRooms.Shaft s, double x, double z) {
        return x >= s.ox + AbyssRooms.X0 && x < s.ox + AbyssRooms.X1 + 1 && z >= s.oz + AbyssRooms.Z0 && z < s.oz + AbyssRooms.Z1 + 1;
    }

    /** 仮想座標が縦穴の部分（小部屋の床より下）か。 */
    public static boolean isShaftVirtual(RoomsState rs, Vec3 v) {
        AbyssRooms.Shaft s = rs.abyss;
        return s != null && inColumn(s, v.x, v.z) && v.y < s.oy;
    }

    /** 部屋 room の層1の y を仮想座標にするためのずれ（引く量）。 */
    public static int roomOffset(AbyssRooms.Shaft s, int room) { return Math.max(0, room - s.entryRoom) * AbyssRooms.L; }

    /** 仮想 → 実際の位置。見えている範囲の外なら隠れる位置へ（hidden[0] = true）。 */
    public static Vec3 toPhysical(RoomsState rs, Vec3 v, boolean[] hidden) {
        if (hidden != null) hidden[0] = false;
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null || !isShaftVirtual(rs, v)) return v;
        double y = v.y + roomOffset(s, lead(rs));
        if (y > s.oy - 1) { if (hidden != null) hidden[0] = true; y = s.oy + 1; }          // 上：ふさいだ小部屋の中
        else if (y < s.bottomY + 1) { if (hidden != null) hidden[0] = true; y = s.bottomY - 4; } // 下：底の床の下
        return new Vec3(v.x, y, v.z);
    }

    public static Vec3 toPhysical(RoomsState rs, Vec3 v) { return toPhysical(rs, v, null); }

    /** 実際の位置（部屋 room の層1にいるもの）→ 仮想座標。 */
    public static Vec3 toVirtual(RoomsState rs, Vec3 p, int room) {
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null || !inColumn(s, p.x, p.z) || p.y >= s.oy) return p;
        return new Vec3(p.x, p.y - roomOffset(s, room), p.z);
    }

    /** プレイヤーの仮想座標（縦穴の中ならそのプレイヤーの部屋で、それ以外は実際の位置）。 */
    public static Vec3 playerVirtual(RoomsState rs, Player p) { return playerVirtual(rs, p, p.position()); }

    public static Vec3 playerVirtual(RoomsState rs, Player p, Vec3 at) {
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null || !inColumn(s, at.x, at.z) || at.y >= s.oy) return at;
        return toVirtual(rs, at, AbyssRooms.roomOf(p, lead(rs)));
    }

    /** 部屋 room（A）の層1の上端・下端の仮想座標（縦穴の中心）。部屋がまだできていなくてもよい。 */
    public static BlockPos roomTop(AbyssRooms.Shaft s, int room) { return s.at(AbyssRooms.CX, s.segTop(1) - roomOffset(s, room), AbyssRooms.CZ); }
    public static BlockPos roomBottom(AbyssRooms.Shaft s, int room) { return s.at(AbyssRooms.CX, s.segBottom(1) - roomOffset(s, room), AbyssRooms.CZ); }

    /** 仮想の y がどの部屋（A）の層1にあたるか。 */
    public static int roomAtVirtualY(AbyssRooms.Shaft s, double vy) {
        // 部屋 r の層1は y ∈ [segBottom(1) - off(r), segTop(1) - off(r)]
        int k = (int) Math.floor((s.segTop(1) + 1 - vy) / AbyssRooms.L);
        return s.entryRoom + Math.max(0, k);
    }

    /**
     * 部屋 from..to の経由点を仮想座標で（A の部屋は y をずらす。roomPaths にまだない A の部屋は縦穴の中心線で補う）。
     * A 以外の部屋は VEntity.pathOf と同じ。
     */
    public static List<BlockPos> pathOf(RoomsState rs, int from, int to) {
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null) return VEntity.pathOf(rs, from, to);
        List<BlockPos> w = new ArrayList<>();
        for (int i = Math.max(0, from); i <= to; i++) {
            boolean a = Sections.forRoom(rs, i) == Sections.A && i >= s.entryRoom;
            List<BlockPos> src;
            if (i < rs.roomPaths.size()) src = rs.roomPaths.get(i);
            else if (a) src = List.of(roomTop(s, i), roomBottom(s, i));
            else break;
            int off = a ? roomOffset(s, i) : 0;
            for (BlockPos p : src) {
                BlockPos q = a && inColumn(s, p.getX() + 0.5, p.getZ() + 0.5) && p.getY() < s.oy ? p.below(off) : p;
                if (w.isEmpty() || !w.get(w.size() - 1).equals(q)) w.add(q);
            }
        }
        return w;
    }

    /** 部屋 room が A の縦穴の部屋か。 */
    public static boolean isAbyssRoom(RoomsState rs, int room) {
        return rs.abyss != null && room >= rs.abyss.entryRoom && Sections.forRoom(rs, room) == Sections.A;
    }

    // ===================== A に出てよい敵 =====================

    /** コマンド（/rooms spawn など明示のデバッグ操作）から出すときは制限しない。 */
    private static int commandDepth = 0;

    public static <T> T asCommand(java.util.function.Supplier<T> f) {
        commandDepth++;
        try { return f.get(); } finally { commandDepth--; }
    }

    public static boolean commandSpawn() { return commandDepth > 0; }

    private static final java.util.Map<String, boolean[]> CAPS = new java.util.HashMap<>();

    /**
     * name の敵が A の縦穴に出てよいか：A 専用の種類（isAbyssNative）は常に、ほかのセクションの種類はハードモードで、
     * 上から縦に追ってくる動きにできるもの（canChaseInAbyss：B/U の突進型。U-200 は除く）だけ。
     * VEntity でないもの（B-20/B-40）・場所や扉・テーブルに頼るもの（V-25-T/L・V25-BUFFED などの searcher 群を含む）は出さない。
     */
    public static boolean allowedInAbyss(ServerLevel level, String name, boolean hard) {
        boolean[] c = CAPS.computeIfAbsent(name, n -> {
            var k = com.example.roomsmod.entity.v.VSpawns.Kind.byDisplay(n);
            if (k == null) return new boolean[]{false, false};
            VEntity e = k.type.get().create(level);
            if (e == null) return new boolean[]{false, false};
            boolean[] r = {e.isAbyssNative(), e.canChaseInAbyss() && !sharesSearcher(k)};
            e.discard();
            return r;
        });
        return c[0] || (hard && c[1]);
    }

    private static boolean sharesSearcher(com.example.roomsmod.entity.v.VSpawns.Kind k) {
        return com.example.roomsmod.entity.v.VSpawns.sharesGroup(k.exclusionGroup, "searcher");
    }

    /** 出現の基準（プレイヤー or 部屋）が A の縦穴か。 */
    public static boolean inAbyssContext(RoomsState rs, Player player, int room) {
        if (rs.abyss == null) return false;
        if (player != null && rs.abyss.contains(player.position())) return true;
        return isAbyssRoom(rs, room);
    }

    /** 出現処理の入口で使う：A の縦穴で出してはいけない（コマンド以外）なら true。 */
    public static boolean blockSpawn(ServerLevel level, RoomsState rs, Player player, int room, String name) {
        if (commandSpawn() || !inAbyssContext(rs, player, room)) return false;
        if (allowedInAbyss(level, name, rs.hardMode)) return false;
        RoomsDebug.log("{} spawn blocked in abyss (room={}, hard={})", name, room, rs.hardMode);
        return true;
    }

    // ===================== 1 周したとき =====================

    /** 仮想座標を持つエンティティ（VEntity が実装）。 */
    public interface Tracked {
        /** 仮想座標から実際の位置を置き直す。動いたら（y の差）、動かなければ 0。 */
        double abyssResync(ServerLevel level, RoomsState rs);
    }

    /** サーバー → クライアント：縦穴の中のこれらのエンティティ（と縦穴の中のパーティクル）を dy だけずらす（補間なしで）。 */
    public record ShiftPayload(int[] ids, float[] dys, float particleDy, int minX, int minZ, int maxX, int maxZ, int minY, int maxY) implements CustomPacketPayload {
        public static final Type<ShiftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(com.example.roomsmod.RoomsMod.MOD_ID, "abyss_shift"));
        public static final StreamCodec<ByteBuf, ShiftPayload> STREAM_CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.ids.length);
            for (int i = 0; i < m.ids.length; i++) { ByteBufCodecs.VAR_INT.encode(buf, m.ids[i]); buf.writeFloat(m.dys[i]); }
            buf.writeFloat(m.particleDy);
            buf.writeInt(m.minX); buf.writeInt(m.minZ); buf.writeInt(m.maxX); buf.writeInt(m.maxZ); buf.writeInt(m.minY); buf.writeInt(m.maxY);
        }, buf -> {
            int n = Math.min(1024, ByteBufCodecs.VAR_INT.decode(buf));
            int[] ids = new int[n]; float[] dys = new float[n];
            for (int i = 0; i < n; i++) { ids[i] = ByteBufCodecs.VAR_INT.decode(buf); dys[i] = buf.readFloat(); }
            float pdy = buf.readFloat();
            return new ShiftPayload(ids, dys, pdy, buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側の処理（ClientSetup が入れる。専用サーバーでは何もしない）。 */
    public static volatile Consumer<ShiftPayload> clientShift = m -> {};

    /**
     * AbyssRooms.loop から：プレイヤーを L 上へテレポートし、先頭の部屋が進んだ直後。仮想座標を持つ敵をすぐ置き直し、
     * 近くのプレイヤーへ（テレポートの直後に届くように）同じずれを送る。
     */
    static void afterLoop(ServerLevel level, RoomsState rs, AbyssRooms.Shaft s) {
        List<Integer> ids = new ArrayList<>();
        List<Float> dys = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof Tracked t) || !e.isAlive()) continue;
            double dy = t.abyssResync(level, rs);
            if (dy != 0) { ids.add(e.getId()); dys.add((float) dy); }
        }
        int[] ia = new int[ids.size()]; float[] da = new float[ids.size()];
        for (int i = 0; i < ia.length; i++) { ia[i] = ids.get(i); da[i] = dys.get(i); }
        ShiftPayload m = new ShiftPayload(ia, da, AbyssRooms.L, s.ox + AbyssRooms.X0, s.oz + AbyssRooms.Z0, s.ox + AbyssRooms.X1 + 1, s.oz + AbyssRooms.Z1 + 1, s.bottomY - 6, s.oy);
        Vec3 c = Vec3.atCenterOf(s.at(AbyssRooms.CX, s.segTop(1), AbyssRooms.CZ));
        for (ServerPlayer p : level.players())
            if (p.position().distanceToSqr(c) <= 96 * 96) PacketDistributor.sendToPlayer(p, m);
        if (RoomsDebug.isEnabled() && ia.length > 0) RoomsDebug.log("abyss loop shift entities={} dys={}", ids, dys);
    }
}
