package com.example.roomsmod.entity;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * ラッシャー共通の経由点移動。位置を直接設定する運動学的移動で、deltaMovement は常に0
 * （バニラの travel と二重に動かない／クライアントで速度外挿しない）。
 * 1tickで最大 speed だけ進み、余った分で次の経由点へ続けて進む（閾値の取りこぼし・往復が起きない）。
 * 3秒進捗がなければ次の経由点へスナップする（見張り）。
 */
public final class WaypointMover {
    public static final int STUCK_TICKS = 60;

    private int lastProgressTick = 0;

    /** @return 新しい waypointIndex。target に到達していれば reached[0]=true。 */
    public int step(Entity e, String name, List<BlockPos> wps, int index, int target, double speed, boolean[] reached) {
        reached[0] = false;
        if (wps.isEmpty()) { reached[0] = true; return index; }
        index = Math.max(0, Math.min(index, wps.size() - 1));
        target = Math.max(0, Math.min(target, wps.size() - 1));
        int startIndex = index;
        Vec3 pos = e.position();
        double budget = speed;
        while (true) {
            Vec3 goal = Vec3.atBottomCenterOf(wps.get(index));
            double dist = goal.distanceTo(pos);
            if (dist <= budget + 1.0E-6) {
                pos = goal;
                budget -= dist;
                if (index == target) { reached[0] = true; break; }
                index += Integer.signum(target - index);
                if (budget <= 1.0E-6) break;
            } else {
                pos = pos.add(goal.subtract(pos).scale(budget / dist));
                break;
            }
        }
        if (index != startIndex || reached[0] || pos.distanceToSqr(e.position()) > 1.0E-4) lastProgressTick = e.tickCount;
        if (!reached[0] && index == startIndex && e.tickCount - lastProgressTick >= STUCK_TICKS) {
            BlockPos snap = wps.get(index);
            RoomsDebug.log("{} stuck watchdog: no progress for {} ticks at waypoint {}/{} pos=({}, {}, {}) -> snapping to {}",
                    name, e.tickCount - lastProgressTick, index, wps.size(),
                    String.format("%.1f", e.getX()), String.format("%.1f", e.getY()), String.format("%.1f", e.getZ()), snap);
            pos = Vec3.atBottomCenterOf(snap);
            if (index == target) reached[0] = true;
            else index += Integer.signum(target - index);
            lastProgressTick = e.tickCount;
        }
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(Vec3.ZERO);
        return index;
    }

    public void resetProgress(Entity e) { lastProgressTick = e.tickCount; }

    /** 経路を作り直したとき、元の経由点（同じ座標）の index を保つ。 */
    public static int preserveIndex(List<BlockPos> oldWps, int oldIndex, List<BlockPos> newWps) {
        if (newWps.isEmpty()) return 0;
        if (oldIndex >= 0 && oldIndex < oldWps.size()) {
            BlockPos cur = oldWps.get(oldIndex);
            if (oldIndex < newWps.size() && newWps.get(oldIndex).equals(cur)) return oldIndex;
            int found = newWps.indexOf(cur);
            if (found >= 0) return found;
        }
        return Math.max(0, Math.min(oldIndex, newWps.size() - 1));
    }
}
