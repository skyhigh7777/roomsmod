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
    /** 加減速つきの今の速度（ブロック/tick）。accel() <= 0 の種類は使わない。 */
    protected double curSpeed = 0;
    private List<BlockPos> wps = null;
    /** 経路の末尾に、A の縦穴で数部屋奥まで伸ばした点を足しているか（AbyssSpace.AHEAD_ROOMS）。 */
    private boolean abyssTail = false;
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

    // ===== 加減速（既定はどちらも 0 = 従来どおりの等速） =====
    /** 加速度（ブロック/tick²）。0 以下なら常に最高速。 */
    protected double accel() { return 0; }
    /** 減速度（ブロック/tick²）。0 より大きく、brakeAtPathEnd() が true なら経路の終点（折り返し点）の手前で止まれるように減速する。 */
    protected double decel() { return 0; }
    /** 経路の終点で止まる（折り返す・止まる）予定か。true の間だけ終点手前で減速する。曲がり角では減速しない。 */
    protected boolean brakeAtPathEnd() { return false; }
    /** 加減速中の最低速度（曲がり角・経路の継ぎ目で止まりかけないように）。 */
    protected double minMoveSpeed() { return 0.12; }

    /** 速度の倍率（ハードモードなど）。既定は出現時のハード強化だけ。 */
    protected double speedMul() { return hardSpawn ? com.example.roomsmod.world.HardMode.SPEED_MUL : 1.0; }
    /** 猶予の倍率。 */
    protected double graceMul() { return hardSpawn ? com.example.roomsmod.world.HardMode.GRACE_MUL : 1.0; }
    /** 倍率をかけた実際の猶予（tick）。 */
    protected int effectiveGraceTicks() { return Math.max(10, (int) Math.round(graceTicks() * graceMul())); }
    /** 倍率をかけた最高速。 */
    protected double maxSpeed() { return speed() * speedMul(); }
    /** 走り終えて消えるときにハードの連鎖抽選をするか（突進型だけ true）。 */
    protected boolean chainsOnFinish() { return true; }

    /** true を返す間は移動・当たり判定をしない。既定は effectiveGraceTicks() 待ち。 */
    protected boolean holdTick(ServerLevel level) {
        if (grace < effectiveGraceTicks()) { grace++; return true; }
        return false;
    }

    /** 止まった（折り返し・待ち）あと、加速をやり直す。 */
    protected void resetSpeed() { curSpeed = 0; }

    /** この tick の移動量。加減速なしなら maxSpeed()。 */
    private double stepSpeed(List<BlockPos> w) {
        double vmax = maxSpeed();
        double a = accel();
        if (a <= 0) return vmax;
        double d = decel();
        if (curSpeed > vmax) curSpeed = Math.max(vmax, curSpeed - (d > 0 ? d : a)); // 最高速が下がった：なめらかに落とす
        else curSpeed = Math.min(vmax, Math.max(curSpeed, Math.min(minMoveSpeed(), vmax)) + a);
        if (d > 0 && brakeAtPathEnd()) {
            double rem = remainingDistance(w);
            double allowed = Math.sqrt(2 * d * rem);
            if (allowed < curSpeed) curSpeed = Math.max(Math.min(minMoveSpeed(), vmax), allowed);
        }
        return curSpeed;
    }

    /** 今の位置から経路の終点までの距離（経由点に沿って）。 */
    private double remainingDistance(List<BlockPos> w) {
        if (w.isEmpty() || waypointIndex >= w.size()) return 0;
        net.minecraft.world.phys.Vec3 at = virtualPos();
        double sum = 0;
        for (int i = waypointIndex; i < w.size(); i++) {
            net.minecraft.world.phys.Vec3 g = net.minecraft.world.phys.Vec3.atBottomCenterOf(w.get(i));
            sum += at.distanceTo(g);
            at = g;
            if (sum > 64) break; // 遠ければ減速しないので打ち切り
        }
        return sum;
    }

    /**
     * 部屋 lo..hi の経路（A の縦穴は仮想座標）。forward で終点が A の部屋なら、さらに AHEAD_ROOMS 部屋奥（の縦穴の底）まで伸ばす
     * （プレイヤーを追い越したら、すぐ前で消えたり折り返したりせず、深いところへ消えていくように）。
     */
    private List<BlockPos> buildPath(RoomsState rs, int lo, int hi, boolean forward) {
        abyssTail = false;
        if (rs.abyss == null) return pathOf(rs, lo, hi);
        List<BlockPos> w = com.example.roomsmod.world.AbyssSpace.pathOf(rs, lo, hi);
        if (forward && com.example.roomsmod.world.AbyssSpace.isAbyssRoom(rs, hi)) {
            w.add(com.example.roomsmod.world.AbyssSpace.roomBottom(rs.abyss, hi + com.example.roomsmod.world.AbyssSpace.AHEAD_ROOMS));
            abyssTail = true;
        }
        return w;
    }

    /**
     * 経路を部屋 from → to に張り替える（from > to なら逆向き）。今の位置から走り出す（ワープしない）。
     * 前向き（to >= from）なら、以後も新しい部屋ができると延長する。
     */
    protected void setPath(ServerLevel level, int from, int to) {
        RoomsState rs = RoomsState.get(level);
        int last = Math.max(0, rs.roomPaths.size() - 1);
        from = Math.max(0, Math.min(from, last)); to = Math.max(0, Math.min(to, last));
        startRoom = from; endRoom = to;
        List<BlockPos> w = buildPath(rs, Math.min(from, to), Math.max(from, to), to >= from);
        if (to < from) java.util.Collections.reverse(w);
        wps = w;
        waypointIndex = 0;
        mover.resetProgress(this);
    }

    private List<BlockPos> waypoints() {
        if (wps == null && level() instanceof ServerLevel sl) {
            RoomsState rs = RoomsState.get(sl);
            boolean rev = endRoom < startRoom;
            wps = new ArrayList<>(buildPath(rs, Math.min(startRoom, endRoom), Math.max(startRoom, endRoom), !rev));
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
        if (abyssTail && !wps.isEmpty()) {
            boolean allAbyss = true;
            for (int i = endRoom + 1; i <= last; i++) if (!com.example.roomsmod.world.AbyssSpace.isAbyssRoom(rs, i)) { allAbyss = false; break; }
            if (allAbyss) { // 縦穴は 1 本の線：末尾の点を深くするだけ（もう通り過ぎた点へ戻らない）
                wps.set(wps.size() - 1, com.example.roomsmod.world.AbyssSpace.roomBottom(rs.abyss, last + com.example.roomsmod.world.AbyssSpace.AHEAD_ROOMS));
                endRoom = last;
                RoomsDebug.log("{} abyss path deepened to room {}", vName(), last + com.example.roomsmod.world.AbyssSpace.AHEAD_ROOMS);
                return wps;
            }
            wps.remove(wps.size() - 1);
            waypointIndex = Math.min(waypointIndex, wps.size());
        }
        List<BlockPos> more = buildPath(rs, endRoom + 1, last, true);
        for (BlockPos p : more)
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
        setVirtualPos(new net.minecraft.world.phys.Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5));
    }

    /** 経路の長さ（まだ読み込んでいなければ 0）。 */
    protected int pathSize() { return wps == null ? 0 : wps.size(); }

    /** startRoom→endRoom の経路の先頭に置く。 */
    protected BlockPos placeAtStart(RoomsState rs, int from, int to) {
        startRoom = from; endRoom = to; wps = null;
        List<BlockPos> w = buildPath(rs, Math.min(from, to), Math.max(from, to), to >= from);
        if (w.isEmpty()) return null;
        BlockPos s = to < from ? w.get(w.size() - 1) : w.get(0);
        setVirtualPos(new net.minecraft.world.phys.Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5));
        return blockPosition(); // 実際の位置（チャンクの読み込み用）
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
        if (w.isEmpty() || waypointIndex >= w.size()) {
            if (!w.isEmpty() && onPathEnd(level)) return;
            leave("path end (" + w.size() + " waypoints)");
            if (chainsOnFinish()) com.example.roomsmod.world.HardMode.chainReroll(level, vName());
            return;
        }
        boolean abyss = RoomsState.get(level).abyss != null;
        int idx = abyss
                ? mover.step(this, vName(), virtualPos(), this::setVirtualPos, true, w, waypointIndex, w.size() - 1, stepSpeed(w), reached)
                : mover.step(this, vName(), w, waypointIndex, w.size() - 1, stepSpeed(w), reached);
        if (idx < w.size()) faceTowards(position().add(net.minecraft.world.phys.Vec3.atBottomCenterOf(w.get(idx)).subtract(virtualPos())));
        waypointIndex = reached[0] ? w.size() : idx;
        // 縦穴の中では仮想座標どうしの距離・視線（ループしても位置関係はそのまま）
        for (Player p : playersNearVirtual(hitRange())) RusherHits.tryHit(this, vName(), p, canSeeVirtual(p), damage());
    }

    @Override
    protected String debugState() {
        return "rooms " + startRoom + "->" + endRoom + " waypoint=" + waypointIndex + "/" + (wps == null ? "?" : wps.size()) + " grace=" + grace + "/" + effectiveGraceTicks()
                + (accel() > 0 ? " speed=" + f(curSpeed) + "/" + f(maxSpeed()) : "") + (hardSpawn ? " hard" : "") + (offSection ? " offSection" : "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("EndRoom", endRoom); tag.putInt("WaypointIndex", waypointIndex); tag.putInt("Grace", grace);
        tag.putDouble("CurSpeed", curSpeed);
        tag.putBoolean("AbyssTail", abyssTail);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        endRoom = tag.getInt("EndRoom"); waypointIndex = tag.getInt("WaypointIndex"); grace = tag.getInt("Grace");
        curSpeed = tag.getDouble("CurSpeed");
        wps = null;
        RoomsDebug.log("{} loaded {}", vName(), debugState());
    }
}
