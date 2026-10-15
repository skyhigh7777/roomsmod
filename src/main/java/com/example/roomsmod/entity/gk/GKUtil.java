package com.example.roomsmod.entity.gk;

import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** GK エンティティの共通処理。 */
public final class GKUtil {
    private GKUtil() {}

    /** 経路（幅3）の上か。 */
    public static boolean nearPath(RoomsState rs, int room, BlockPos p) {
        List<BlockPos> path = rs.roomPaths.get(room);
        for (int i = 0; i + 1 < path.size(); i++) {
            BlockPos a = path.get(i), b = path.get(i + 1);
            if (p.getX() >= Math.min(a.getX(), b.getX()) - 1 && p.getX() <= Math.max(a.getX(), b.getX()) + 1
                    && p.getZ() >= Math.min(a.getZ(), b.getZ()) - 1 && p.getZ() <= Math.max(a.getZ(), b.getZ()) + 1) return true;
        }
        return false;
    }

    /** 部屋の中の、経路を塞がない安全な床（足の位置）を最大 n 個（互いに2マス以上離す）。 */
    public static List<BlockPos> freeSpots(ServerLevel l, RoomsState rs, int room, RandomSource r, int n) {
        List<BlockPos> ok = new ArrayList<>(), out = new ArrayList<>();
        if (room < 0 || room >= rs.generatedRooms.size()) return out;
        for (BoundingBox b : rs.generatedRooms.get(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX() + 1, b.minY() + 1, b.minZ() + 1, b.maxX() - 1, b.maxY() - 2, b.maxZ() - 1))
                if (SafeSpot.isSafe(l, rs, room, q) && !nearPath(rs, room, q) && l.getBlockState(q.below()).isSolidRender(l, q.below())) ok.add(q.immutable());
        Collections.shuffle(ok, new java.util.Random(r.nextLong()));
        for (BlockPos q : ok) {
            if (out.size() >= n) break;
            boolean far = true;
            for (BlockPos o : out) far &= o.distManhattan(q) >= 3;
            if (far) out.add(q);
        }
        return out;
    }

    public static boolean inRoom(ServerLevel l, int room, Player p) {
        return RoomTracker.roomIndexAt(RoomsState.get(l), p.blockPosition()) == room;
    }

    /** 部屋 room にいる、または e から r 以内のプレイヤー（観戦者除く）。 */
    public static List<Player> players(ServerLevel l, Entity e, int room, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : l.players()) if (!p.isSpectator() && p.isAlive() && (inRoom(l, room, p) || p.distanceTo(e) <= r)) out.add(p);
        return out;
    }

    public static void actionbar(List<Player> ps, Component c) {
        for (Player p : ps) if (p instanceof ServerPlayer sp) sp.displayClientMessage(c, true);
    }
}
