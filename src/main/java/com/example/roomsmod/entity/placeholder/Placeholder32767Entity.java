package com.example.roomsmod.entity.placeholder;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * PLACEHOLDER-32767。
 * <ol>
 *   <li>出現：ビープ音1回、最奥の部屋の ROOMS_BEHIND 部屋手前（1 以上）の真ん中に現れる。</li>
 *   <li>WAIT_MIN〜WAIT_MAX tick（両端含む）待つ。</li>
 *   <li>ランダムなピッチのビープ音を3つ同時に鳴らし、BEEP_TICKS 待つ（鳴り終わるまで）。</li>
 *   <li>最奥から WARP_ROOMS 部屋（1 以上）のうち、直前と違うランダムな部屋の真ん中へ WARP_INTERVAL tick ごとにワープ（WARPS 回）。
 *       ワープするたび、その部屋の範囲内にいて隠れていないプレイヤーは即死。</li>
 *   <li>WARPS 回で消える。</li>
 * </ol>
 * 自然出現しない（/rooms spawn placeholder32767 のみ）。状態は NBT に保存し、途中でロードしても続きから。
 */
public class Placeholder32767Entity extends PlaceholderEntity {
    public static final int ROOMS_BEHIND = 30, WAIT_MIN = 88, WAIT_MAX = 188, BEEP_TICKS = 20;
    public static final int WARP_ROOMS = 15, WARP_INTERVAL = 2, WARPS = 70;
    public static final float KILL_DAMAGE = 1000f;

    /** 0=出現直後, 1=待ち, 2=3つのビープの後, 3=ワープ中 */
    private int phase = 0, phaseTicks = 0, waitTicks = 0, warps = 0, lastRoom = -1;

    public Placeholder32767Entity(EntityType<? extends Mob> type, Level level) { super(type, level, "PLACEHOLDER-32767"); }

    private static int deepest(RoomsState rs) { return Math.max(1, rs.roomPaths.size() - 1); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int d = deepest(rs);
        int room = Math.max(1, d - ROOMS_BEHIND);
        BlockPos at = roomCenter(rs, room);
        if (at == null) return null;
        snapTo(at);
        startRoom = room; lastRoom = room;
        RoomsDebug.log("{} spawn room={} ({}) deepest={} at {}", name, room, RoomTracker.format(rs, room), d, at);
        return at;
    }

    /** 部屋の真ん中（部屋の範囲を全部合わせた中心がその部屋の中ならそこ、外なら経路の真ん中の点）。床の上。 */
    static BlockPos roomCenter(RoomsState rs, int room) {
        if (room < 0 || room >= rs.roomPaths.size()) return null;
        List<BlockPos> path = rs.roomPaths.get(room);
        List<BoundingBox> boxes = room < rs.generatedRooms.size() ? rs.generatedRooms.get(room) : List.of();
        if (!boxes.isEmpty()) {
            int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            for (BoundingBox b : boxes) { x0 = Math.min(x0, b.minX()); z0 = Math.min(z0, b.minZ()); x1 = Math.max(x1, b.maxX()); z1 = Math.max(z1, b.maxZ()); }
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            for (BoundingBox b : boxes)
                if (cx >= b.minX() && cx <= b.maxX() && cz >= b.minZ() && cz <= b.maxZ()) return new BlockPos(cx, b.minY() + 1, cz);
        }
        return path.isEmpty() ? null : path.get(path.size() / 2);
    }

    private void snapTo(BlockPos at) {
        moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, getYRot(), getXRot()); // 旧位置も同じにする（補間しない）
    }

    /** クライアント：位置の補間をせず、そのまま置き直す（ワープが線のように見えないように）。 */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        moveTo(x, y, z, yRot, xRot);
    }

    /** 部屋ディメンションの各プレイヤーの位置で鳴らす（どこにいても聞こえる）。 */
    private void beep(ServerLevel level, float pitch) {
        SoundEvent s = RoomsMod.PLACEHOLDER_BEEP.get();
        for (ServerPlayer p : level.players()) level.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.HOSTILE, 1.0f, pitch);
    }

    private void enter(int ph) { phase = ph; phaseTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        phaseTicks++;
        switch (phase) {
            case 0 -> {
                beep(level, 1.0f);
                waitTicks = WAIT_MIN + random.nextInt(WAIT_MAX - WAIT_MIN + 1);
                RoomsDebug.log("{} beep, waiting {} ticks in room {}", name, waitTicks, startRoom);
                enter(1);
            }
            case 1 -> {
                if (phaseTicks >= waitTicks) {
                    float[] pitches = new float[3];
                    for (int i = 0; i < 3; i++) { pitches[i] = 0.5f + random.nextFloat() * 1.5f; beep(level, pitches[i]); }
                    RoomsDebug.log("{} triple beep pitches={}/{}/{}", name, f(pitches[0]), f(pitches[1]), f(pitches[2]));
                    enter(2);
                }
            }
            case 2 -> { if (phaseTicks >= BEEP_TICKS) { enter(3); warp(level, rs); } }
            case 3 -> {
                if (phaseTicks % WARP_INTERVAL == 0) {
                    if (warps >= WARPS) { leave("finished " + WARPS + " warps"); return; }
                    warp(level, rs);
                }
            }
            default -> enter(0);
        }
    }

    /** 最奥の WARP_ROOMS 部屋から、直前と違う部屋へワープして、その部屋の隠れていないプレイヤーを即死させる。 */
    private void warp(ServerLevel level, RoomsState rs) {
        int d = deepest(rs);
        int lo = Math.max(1, d - WARP_ROOMS + 1);
        int n = d - lo + 1;
        int room;
        if (n <= 1) room = lo; // 部屋が1つしかない：同じ部屋でも仕方ない
        else if (lastRoom >= lo && lastRoom <= d) { room = lo + random.nextInt(n - 1); if (room >= lastRoom) room++; } // 直前の部屋を除いて一様に
        else room = lo + random.nextInt(n);
        BlockPos at = roomCenter(rs, room);
        warps++;
        if (at == null) { RoomsDebug.log("{} warp {}/{} skipped: no center for room {}", name, warps, WARPS, room); return; }
        snapTo(at);
        lastRoom = room;
        int kills = 0;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (RoomTracker.roomIndexAt(rs, p.blockPosition()) != room) continue;
            if (HidingRules.isProtectedFrom(this, p)) { RoomsDebug.log(p, name + ": spared by hiding", "{} warp room={} player={} spared (hiding)", name, room, p.getGameProfile().getName()); continue; }
            RoomsDamage.hurt(this, p, KILL_DAMAGE);
            kills++;
            RoomsDebug.log("{} warp room={} killed player={}", name, room, p.getGameProfile().getName());
        }
        RoomsDebug.log("{} warp {}/{} -> room {} ({}) range {}..{} kills={}", name, warps, WARPS, room, RoomTracker.format(rs, room), lo, d, kills);
    }

    @Override protected String debugState() { return "phase=" + phase + " phaseTicks=" + phaseTicks + " wait=" + waitTicks + " warps=" + warps + "/" + WARPS + " lastRoom=" + lastRoom; }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase); t.putInt("PhaseTicks", phaseTicks); t.putInt("WaitTicks", waitTicks); t.putInt("Warps", warps); t.putInt("LastRoom", lastRoom);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase"); phaseTicks = t.getInt("PhaseTicks"); waitTicks = t.getInt("WaitTicks"); warps = t.getInt("Warps");
        lastRoom = t.contains("LastRoom") ? t.getInt("LastRoom") : -1;
        RoomsDebug.log("{} loaded {}", name, debugState());
    }
}
