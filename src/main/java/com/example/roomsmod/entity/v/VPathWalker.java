package com.example.roomsmod.entity.v;

import com.example.roomsmod.entity.WaypointMover;
import com.example.roomsmod.hiding.RusherHits;
import com.example.roomsmod.util.ApproachSoundHook;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 部屋の経路（階段の経由点を含む）を startRoom→endRoom の順に進み、終点で消える。
 * endRoom < startRoom なら逆向き（V-50：前方の部屋からプレイヤーへ向かって来る）。
 * 当たりは RusherHits（隠れ場所・遮蔽物のルールはB-20と同じ）。
 */
public abstract class VPathWalker extends VEntity {
    protected int endRoom = 0;
    protected int waypointIndex = 0;
    protected int grace = 0;
    private List<BlockPos> wps = null;
    private final WaypointMover mover = new WaypointMover();
    private final boolean[] reached = new boolean[1];

    protected VPathWalker(EntityType<? extends Mob> type, Level level) { super(type, level); }

    protected abstract double speed();
    protected abstract int graceTicks();
    protected abstract double hitRange();
    protected abstract float damage();
    protected abstract SoundEvent approachSound();
    /** 接近ループの再生間隔（tick、音声ファイルの長さに合わせる）。 */
    protected int loopTicks() { return 40; }

    /** true を返す間は移動・当たり判定をしない。既定は graceTicks() 待ち。 */
    protected boolean holdTick(ServerLevel level) {
        if (grace < graceTicks()) { grace++; return true; }
        return false;
    }

    private List<BlockPos> waypoints() {
        if (wps == null && level() instanceof ServerLevel sl) {
            RoomsState rs = RoomsState.get(sl);
            boolean rev = endRoom < startRoom;
            wps = pathOf(rs, Math.min(startRoom, endRoom), Math.max(startRoom, endRoom));
            if (rev) java.util.Collections.reverse(wps);
            waypointIndex = Math.min(waypointIndex, wps.size());
        }
        return wps == null ? List.of() : wps;
    }

    /**
     * 前進型（endRoom >= startRoom）のみ：走行中にプレイヤーが扉を開けて endRoom より先の部屋が生成されたら、
     * その部屋の経路（階段込み）を末尾に足し、最新の部屋の出口まで走り続ける。
     * 逆向き（V-50）は対象外。チャンクは VEntity.tick の RusherChunks.move が現在位置に追従して保持する。
     */
    private List<BlockPos> extendIfNewRooms(ServerLevel level, List<BlockPos> w) {
        if (wps == null || endRoom < startRoom) return w;
        RoomsState rs = RoomsState.get(level);
        int last = rs.roomPaths.size() - 1;
        if (last <= endRoom) return w;
        int before = wps.size();
        for (BlockPos p : pathOf(rs, endRoom + 1, last))
            if (wps.isEmpty() || !wps.get(wps.size() - 1).equals(p)) wps.add(p);
        endRoom = last;
        RoomsDebug.log("{} path extended to room {} (+{} waypoints)", vName(), last, wps.size() - before);
        return wps;
    }

    /** 経路の終点に着いたとき。true を返すと消えずに続ける（例：reversePath で折り返す）。 */
    protected boolean onPathEnd(ServerLevel level) { return false; }

    /** 今の経路を逆向きにして先頭から走り直す（startRoom/endRoom も入れ替えるので、以後は新しい部屋の延長をしない）。 */
    protected void reversePath() {
        if (wps == null) return;
        java.util.Collections.reverse(wps);
        int t = startRoom; startRoom = endRoom; endRoom = t;
        waypointIndex = 0;
    }

    /** 今の経路の先頭に戻って走り直す（2回目の通過など）。 */
    protected void restartPath() {
        if (wps == null || wps.isEmpty()) return;
        waypointIndex = 0;
        BlockPos s = wps.get(0);
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
    }

    /** 経路の長さ（まだ読み込んでいなければ 0）。 */
    protected int pathSize() { return wps == null ? 0 : wps.size(); }

    /** startRoom→endRoom の経路の先頭に置く。 */
    protected BlockPos placeAtStart(RoomsState rs, int from, int to) {
        startRoom = from; endRoom = to; wps = null;
        List<BlockPos> w = pathOf(rs, Math.min(from, to), Math.max(from, to));
        if (w.isEmpty()) return null;
        BlockPos s = to < from ? w.get(w.size() - 1) : w.get(0);
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        return s;
    }

    @Override
    protected void clientTick() {
        if (tickCount % loopTicks() == 1) ApproachSoundHook.start(this, approachSound());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        List<BlockPos> w = waypoints();
        if (holdTick(level)) return;
        w = extendIfNewRooms(level, w);
        if (w.isEmpty() || waypointIndex >= w.size()) { if (!w.isEmpty() && onPathEnd(level)) return; leave("path end (" + w.size() + " waypoints)"); return; }
        int idx = mover.step(this, vName(), w, waypointIndex, w.size() - 1, speed(), reached);
        if (idx < w.size()) faceTowards(net.minecraft.world.phys.Vec3.atBottomCenterOf(w.get(idx)));
        waypointIndex = reached[0] ? w.size() : idx;
        for (Player p : playersNear(hitRange())) RusherHits.tryHit(this, vName(), p, canSee(p), damage());
    }

    @Override
    protected String debugState() {
        return "rooms " + startRoom + "->" + endRoom + " waypoint=" + waypointIndex + "/" + (wps == null ? "?" : wps.size()) + " grace=" + grace;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EndRoom", endRoom); tag.putInt("WaypointIndex", waypointIndex); tag.putInt("Grace", grace);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        endRoom = tag.getInt("EndRoom"); waypointIndex = tag.getInt("WaypointIndex"); grace = tag.getInt("Grace");
        wps = null;
        RoomsDebug.log("{} loaded {}", vName(), debugState());
    }
}
