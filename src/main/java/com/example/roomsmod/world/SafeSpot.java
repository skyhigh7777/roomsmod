package com.example.roomsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 部屋内の安全なワープ先：部屋の内側（箱の外周1ブロックを除く）で、足元が固い床、足と頭の2ブロックが通れる空間。
 * チャンクは同期で読み込む。経路（入口→折れ点→出口、床+1）の中央から探し、だめなら部屋の箱を走査、最後は入口。
 */
public final class SafeSpot {
    private SafeSpot() {}

    public static boolean inInterior(RoomsState rs, int room, BlockPos p) {
        for (BoundingBox b : rs.generatedRooms.get(room))
            if (p.getX() > b.minX() && p.getX() < b.maxX() && p.getZ() > b.minZ() && p.getZ() < b.maxZ()
                    && p.getY() > b.minY() && p.getY() + 1 < b.maxY()) return true;
        return false;
    }

    private static boolean passable(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.getCollisionShape(l, p).isEmpty() && s.getFluidState().isEmpty() && !(s.getBlock() instanceof DoorBlock);
    }

    /** 足の位置 p が安全か。 */
    public static boolean isSafe(ServerLevel l, RoomsState rs, int room, BlockPos p) {
        l.getChunk(p.getX() >> 4, p.getZ() >> 4); // 同期読み込み
        BlockPos below = p.below();
        return inInterior(rs, room, p) && passable(l, p) && passable(l, p.above())
                && l.getBlockState(below).isFaceSturdy(l, below, Direction.UP);
    }

    /** 足の位置（ブロック中央の下面）。 */
    /** 部屋の内側の安全な床からランダムに1か所（無ければ null）。 */
    public static Vec3 findRandom(ServerLevel l, RoomsState rs, int room, net.minecraft.util.RandomSource r) {
        if (room < 0 || room >= rs.generatedRooms.size()) return null;
        List<BlockPos> ok = new ArrayList<>();
        for (BoundingBox b : rs.generatedRooms.get(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX() + 1, b.minY() + 1, b.minZ() + 1, b.maxX() - 1, b.maxY() - 2, b.maxZ() - 1))
                if (isSafe(l, rs, room, q)) ok.add(q.immutable());
        return ok.isEmpty() ? null : Vec3.atBottomCenterOf(ok.get(r.nextInt(ok.size())));
    }

    public static Vec3 find(ServerLevel l, RoomsState rs, int room) {
        // A（縦穴）：床がないので決まった位置（AbyssRooms）
        Vec3 abyss = AbyssRooms.spotFor(rs, room);
        if (abyss != null) return abyss;
        List<BlockPos> path = room >= 0 && room < rs.roomPaths.size() ? rs.roomPaths.get(room) : List.of();
        List<BlockPos> cand = new ArrayList<>();
        // 経路の各区間の点（中央→外側の順）
        List<BlockPos> line = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            BlockPos a = path.get(i), b = path.get(i + 1);
            int n = Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getZ() - a.getZ()));
            for (int k = 0; k <= n; k++) {
                double t = n == 0 ? 0 : (double) k / n;
                line.add(BlockPos.containing(a.getX() + (b.getX() - a.getX()) * t, a.getY() + (b.getY() - a.getY()) * t, a.getZ() + (b.getZ() - a.getZ()) * t));
            }
        }
        int mid = line.size() / 2;
        for (int d = 0; d <= line.size(); d++) {
            if (mid + d < line.size()) cand.add(line.get(mid + d));
            if (d > 0 && mid - d >= 0) cand.add(line.get(mid - d));
        }
        for (BlockPos c : cand) for (int dy = 0; dy >= -1; dy--) {
            BlockPos q = c.above(dy);
            if (isSafe(l, rs, room, q)) return Vec3.atBottomCenterOf(q);
        }
        // 部屋の箱を走査
        if (room >= 0 && room < rs.generatedRooms.size())
            for (BoundingBox b : rs.generatedRooms.get(room))
                for (BlockPos q : BlockPos.betweenClosed(b.minX() + 1, b.minY() + 1, b.minZ() + 1, b.maxX() - 1, b.maxY() - 2, b.maxZ() - 1))
                    if (isSafe(l, rs, room, q)) return Vec3.atBottomCenterOf(q);
        com.example.roomsmod.util.RoomsDebug.warn("SafeSpot: no safe spot in room {}, falling back to entrance", room);
        return path.isEmpty() ? null : Vec3.atBottomCenterOf(path.get(0));
    }
}
