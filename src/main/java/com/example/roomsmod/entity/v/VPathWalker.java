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
 * 当たりは RusherHits（隠れ場所・遮蔽物のルールはA-20と同じ）。
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
        if (grace < graceTicks()) { grace++; return; }
        if (w.isEmpty() || waypointIndex >= w.size()) { leave("path end (" + w.size() + " waypoints)"); return; }
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
