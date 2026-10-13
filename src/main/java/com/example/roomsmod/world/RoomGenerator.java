package com.example.roomsmod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import com.example.roomsmod.event.DoorEventHandler;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 部屋生成ロジック。
 * 照明はA-025からA-100で徐々に暗くする。
 * 看板はText Displayで大きめに表示。
 */
public class RoomGenerator {

    public static BlockPos generateLobby(ServerLevel level, BlockPos origin) {
        int width = 20;
        int depth = 32;
        int height = 5;

        // 床・壁・天井
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                    for (int y = 1; y < height; y++) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(origin.offset(x, height, z), Blocks.STONE.defaultBlockState());
            }
        }

        // 椅子のみ
        level.setBlockAndUpdate(origin.offset(5, 1, 10), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(10, 1, 15), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(15, 1, 20), Blocks.OAK_STAIRS.defaultBlockState());

        // 照明を規則正しく配置（天井に吊るす）
        for (int x = 5; x <= 15; x += 5) {
            for (int z = 8; z <= 24; z += 8) {
                level.setBlockAndUpdate(origin.offset(x, height - 1, z), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
            }
        }

        // 出口扉
        BlockPos doorPos = origin.offset(10, 1, depth - 1);
        level.setBlockAndUpdate(doorPos, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(doorPos.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 出口と大きめ看板
        placeExitSign(level, origin.offset(10, 3, depth - 2), "A-001");

        // 重なり防止レジストリに登録
        DoorEventHandler.generatedRooms.add(List.of(new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + width - 1, origin.getY() + height, origin.getZ() + depth - 1)));

        return doorPos;
    }

    // ===================== 通常部屋生成 =====================

    /** 部屋の形状。 */
    enum RoomShape { BOX, CORRIDOR, HALL, TALL, L_SHAPE }

    /** 出口の位置。 */
    enum ExitType { CENTER, OFFSET_LEFT, OFFSET_RIGHT, SIDE_LEFT, SIDE_RIGHT }

    /** ローカル座標（originからの相対x,z）の矩形。両端を含み、壁も含む。 */
    record Rect(int x0, int z0, int x1, int z1) {
        boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    /** 部屋の設計図。 */
    static final class RoomPlan {
        final List<Rect> rects = new ArrayList<>();
        int height = 5;
        int mainDepth;       // 照明位置の基準（入口側の区画の奥行き）
        int exitX, exitZ;    // 出口扉（ローカル）
        final List<int[]> pathWaypoints = new ArrayList<>(); // 入口→出口の通路の折れ点

        boolean inside(int x, int z) {
            for (Rect r : rects) if (r.contains(x, z)) return true;
            return false;
        }

        /** 8近傍に部屋外があれば壁。 */
        boolean isWall(int x, int z) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!inside(x + dx, z + dz)) return true;
            return false;
        }

        int minX() { return rects.stream().mapToInt(Rect::x0).min().orElse(0); }
        int maxX() { return rects.stream().mapToInt(Rect::x1).max().orElse(0); }
        int maxZ() { return rects.stream().mapToInt(Rect::z1).max().orElse(0); }
    }

    private static final int ENTRANCE_X = 10;

    public static BlockPos generateNextRoom(ServerLevel level, BlockPos origin, int roomNumber) {
        // シード付き乱数で再現性を確保
        Random rand = new Random(level.getSeed() * 31L + roomNumber * 0x9E3779B97F4A7C15L);

        RoomPlan plan = choosePlan(rand, origin, roomNumber);
        buildShell(level, origin, plan);
        placeFurniture(level, origin, plan, rand);

        // 出口扉
        BlockPos exitDoor = origin.offset(plan.exitX, 1, plan.exitZ);
        level.setBlockAndUpdate(exitDoor, Blocks.OAK_DOOR.defaultBlockState());
        level.setBlockAndUpdate(exitDoor.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 照明：天井に吊るす（既存ロジックのまま）
        int lightLevel = Math.max(0, 15 - Math.max(0, roomNumber - 25) / 5);
        if (lightLevel > 0) {
            level.setBlockAndUpdate(origin.offset(ENTRANCE_X, plan.height - 1, plan.mainDepth / 2), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }

        placeExitSign(level, origin.offset(plan.exitX, 3, plan.exitZ - 1), "A-" + String.format("%03d", roomNumber + 1));

        DoorEventHandler.generatedRooms.add(toWorldBoxes(origin, plan));
        return exitDoor;
    }

    /** 候補を順に試し、既存部屋と重ならない最初のものを採用。最後は必ず通る直線の箱部屋。 */
    private static RoomPlan choosePlan(Random rand, BlockPos origin, int roomNumber) {
        RoomShape[] shapes = RoomShape.values();
        RoomShape shape = shapes[rand.nextInt(shapes.length)];
        // x方向に流れすぎないよう、原点から離れたら中央へ戻る向きを優先
        boolean preferRight = origin.getX() > 40 ? false : origin.getX() < -40 ? true : rand.nextBoolean();
        ExitType exit = switch (rand.nextInt(5)) {
            case 0 -> ExitType.CENTER;
            case 1 -> preferRight ? ExitType.OFFSET_RIGHT : ExitType.OFFSET_LEFT;
            case 2 -> preferRight ? ExitType.OFFSET_LEFT : ExitType.OFFSET_RIGHT; // オフセットは小さいので逆も可
            default -> preferRight ? ExitType.SIDE_RIGHT : ExitType.SIDE_LEFT;
        };
        int a = rand.nextInt(1000), b = rand.nextInt(1000), c = rand.nextInt(1000);

        List<RoomPlan> candidates = new ArrayList<>();
        if (shape == RoomShape.L_SHAPE) {
            candidates.add(planL(preferRight, false, a, b));
            candidates.add(planL(!preferRight, false, a, b)); // 逆方向に曲がる
            candidates.add(planL(preferRight, true, a, b));   // 短い脚
            candidates.add(planL(!preferRight, true, a, b));
            candidates.add(planStraight(RoomShape.BOX, exit, a, b, c));
        } else {
            candidates.add(planStraight(shape, exit, a, b, c));
            if (exit == ExitType.SIDE_LEFT || exit == ExitType.SIDE_RIGHT) {
                candidates.add(planStraight(shape, exit == ExitType.SIDE_LEFT ? ExitType.SIDE_RIGHT : ExitType.SIDE_LEFT, a, b, c));
            }
            candidates.add(planStraight(shape, ExitType.CENTER, a, b, c));
        }
        for (RoomPlan p : candidates) {
            if (isClear(origin, p)) return p;
        }
        // フォールバック：中央出口の直線箱部屋。出口は常に部屋の最大Z面にあるので、
        // 次の部屋は必ず既存部屋すべてより+Z側に置かれ、重ならない。
        return planStraight(RoomShape.BOX, ExitType.CENTER, 0, 0, 0);
    }

    /** 直線系（箱・廊下・ホール・高天井）。 */
    private static RoomPlan planStraight(RoomShape shape, ExitType exit, int a, int b, int c) {
        RoomPlan p = new RoomPlan();
        int width, depth;
        switch (shape) {
            case CORRIDOR -> { width = 7 + a % 3; depth = 40 + b % 11; }
            case HALL -> { width = 28 + a % 5; depth = 28 + b % 7; }
            case TALL -> { width = 20; depth = 28 + b % 9; p.height = 9 + a % 2; }
            default -> { width = 20; depth = 28 + b % 9; }
        }
        int x0 = ENTRANCE_X - width / 2;
        int x1 = x0 + width - 1;
        p.rects.add(new Rect(x0, 0, x1, depth - 1));
        p.mainDepth = depth;
        p.pathWaypoints.add(new int[]{ENTRANCE_X, 1});

        int maxOffset = Math.max(0, (x1 - x0) / 2 - 3);
        switch (exit) {
            case OFFSET_LEFT, OFFSET_RIGHT -> {
                int off = maxOffset == 0 ? 0 : 2 + c % Math.max(1, maxOffset - 1);
                off = Math.min(off, maxOffset);
                p.exitX = ENTRANCE_X + (exit == ExitType.OFFSET_RIGHT ? off : -off);
                p.exitZ = depth - 1;
                p.pathWaypoints.add(new int[]{ENTRANCE_X, depth - 4});
                p.pathWaypoints.add(new int[]{p.exitX, depth - 4});
            }
            case SIDE_LEFT, SIDE_RIGHT -> {
                // 側壁に開口 → 横へ短い通路 → +Z へ曲がって扉。扉は部屋全体の最大Z面になる。
                boolean right = exit == ExitType.SIDE_RIGHT;
                int zs = depth - 7;
                int wallX = right ? x1 : x0;
                int dir = right ? 1 : -1;
                int outer = wallX + dir * 6;
                p.rects.add(new Rect(Math.min(wallX, outer), zs, Math.max(wallX, outer), zs + 4));
                int legA = wallX + dir * 2;
                p.rects.add(new Rect(Math.min(legA, outer), zs, Math.max(legA, outer), zs + 9));
                p.exitX = wallX + dir * 4;
                p.exitZ = zs + 9;
                p.pathWaypoints.add(new int[]{ENTRANCE_X, zs + 2});
                p.pathWaypoints.add(new int[]{p.exitX, zs + 2});
            }
            default -> {
                p.exitX = ENTRANCE_X;
                p.exitZ = depth - 1;
            }
        }
        p.pathWaypoints.add(new int[]{p.exitX, p.exitZ - 1});
        return p;
    }

    /** L字：前進してから左右に曲がる。出口は曲がった脚の先端の+Z壁。 */
    private static RoomPlan planL(boolean right, boolean shortLeg, int a, int b) {
        RoomPlan p = new RoomPlan();
        int fwdWidth = 9;
        int fwdDepth = 18 + a % 7;
        int legDepth = 11; // 曲がった脚の奥行き（z方向）
        int legLen = shortLeg ? 12 : 16 + b % 7;
        int fx0 = ENTRANCE_X - fwdWidth / 2;
        int fx1 = fx0 + fwdWidth - 1;
        int totalDepth = fwdDepth + legDepth - 1;
        int lz0 = totalDepth - legDepth;
        p.rects.add(new Rect(fx0, 0, fx1, totalDepth - 1));
        int lx0 = right ? fx0 : fx0 - legLen;
        int lx1 = right ? fx1 + legLen : fx1;
        p.rects.add(new Rect(lx0, lz0, lx1, totalDepth - 1));
        p.mainDepth = fwdDepth;
        p.exitX = right ? lx1 - 4 : lx0 + 4;
        p.exitZ = totalDepth - 1;
        int midZ = lz0 + legDepth / 2;
        p.pathWaypoints.add(new int[]{ENTRANCE_X, 1});
        p.pathWaypoints.add(new int[]{ENTRANCE_X, midZ});
        p.pathWaypoints.add(new int[]{p.exitX, midZ});
        p.pathWaypoints.add(new int[]{p.exitX, p.exitZ - 1});
        return p;
    }

    /**
     * 重なり判定。全既存部屋と壁込みで交差しないこと、さらに直前の部屋以外とは1ブロックの余白も確保。
     * 加えて出口が部屋全体の最大Z面にあること（+Z進行の不変条件）を要求する。
     */
    private static boolean isClear(BlockPos origin, RoomPlan p) {
        if (p.exitZ != p.maxZ()) return false;
        List<BoundingBox> mine = toWorldBoxes(origin, p);
        List<List<BoundingBox>> rooms = DoorEventHandler.generatedRooms;
        for (int i = 0; i < rooms.size(); i++) {
            boolean previous = i == rooms.size() - 1;
            for (BoundingBox other : rooms.get(i)) {
                for (BoundingBox box : mine) {
                    BoundingBox test = previous ? box : box.inflatedBy(1);
                    if (test.intersects(other)) return false;
                }
            }
        }
        return true;
    }

    private static List<BoundingBox> toWorldBoxes(BlockPos origin, RoomPlan p) {
        List<BoundingBox> list = new ArrayList<>();
        for (Rect r : p.rects) {
            list.add(new BoundingBox(origin.getX() + r.x0(), origin.getY(), origin.getZ() + r.z0(),
                    origin.getX() + r.x1(), origin.getY() + p.height, origin.getZ() + r.z1()));
        }
        return list;
    }

    /** 床・壁・天井を設置。入口（z==0, x 9..11）は開ける。 */
    private static void buildShell(ServerLevel level, BlockPos origin, RoomPlan p) {
        Set<Long> done = new HashSet<>();
        for (Rect r : p.rects) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                for (int z = r.z0(); z <= r.z1(); z++) {
                    if (!done.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                    level.setBlockAndUpdate(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                    boolean entrance = z == 0 && x >= ENTRANCE_X - 1 && x <= ENTRANCE_X + 1;
                    if (p.isWall(x, z) && !entrance) {
                        for (int y = 1; y < p.height; y++) {
                            level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                        }
                    } else {
                        for (int y = 1; y < p.height; y++) {
                            level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                    level.setBlockAndUpdate(origin.offset(x, p.height, z), Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    // ===================== 家具 =====================

    /** 通路（入口→出口の折れ線、幅3）のセル集合。 */
    private static Set<Long> pathCells(RoomPlan p) {
        Set<Long> cells = new HashSet<>();
        for (int i = 0; i + 1 < p.pathWaypoints.size(); i++) {
            int[] s = p.pathWaypoints.get(i), e = p.pathWaypoints.get(i + 1);
            int x = s[0], z = s[1];
            while (true) {
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        cells.add(key(x + dx, z + dz));
                if (x == e[0] && z == e[1]) break;
                if (x != e[0]) x += Integer.signum(e[0] - x); else z += Integer.signum(e[1] - z);
            }
        }
        return cells;
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private static void placeFurniture(ServerLevel level, BlockPos origin, RoomPlan p, Random rand) {
        Set<Long> blocked = pathCells(p);
        // 入口と出口周辺は空ける
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 3; dz++) {
                blocked.add(key(ENTRANCE_X + dx, dz));
                blocked.add(key(p.exitX + dx, p.exitZ - dz));
            }

        List<int[]> wallSide = new ArrayList<>();  // 壁に接する床セル
        List<int[]> open = new ArrayList<>();      // 壁から2以上離れた床セル
        int minX = p.minX(), maxX = p.maxX(), maxZ = p.maxZ();
        for (int x = minX; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!p.inside(x, z) || p.isWall(x, z) || blocked.contains(key(x, z))) continue;
                boolean touchesWall = p.isWall(x + 1, z) || p.isWall(x - 1, z) || p.isWall(x, z + 1) || p.isWall(x, z - 1);
                boolean nearWall = touchesWall || p.isWall(x + 2, z) || p.isWall(x - 2, z) || p.isWall(x, z + 2) || p.isWall(x, z - 2);
                if (touchesWall) wallSide.add(new int[]{x, z});
                else if (!nearWall) open.add(new int[]{x, z});
            }
        }
        Collections.shuffle(wallSide, rand);
        Collections.shuffle(open, rand);
        Set<Long> used = new HashSet<>();

        // 隠れ場所：壁際に単体のオーク板（将来ロッカーに差し替え予定）。従来と同程度の3〜4個。
        int hides = 3 + rand.nextInt(2);
        for (int[] c : wallSide) {
            if (hides == 0) break;
            Direction facing = facingAwayFromWall(p, c[0], c[1]);
            if (facing == null) continue;
            int fx = c[0] + facing.getStepX(), fz = c[1] + facing.getStepZ();
            int fx2 = fx + facing.getStepX(), fz2 = fz + facing.getStepZ();
            // 前方1〜2マスの床を空けておく
            if (!p.inside(fx, fz) || p.isWall(fx, fz) || used.contains(key(fx, fz))) continue;
            if (!reserve(used, c[0], c[1], 1)) continue;
            used.add(key(fx, fz));
            if (p.inside(fx2, fz2) && !p.isWall(fx2, fz2)) used.add(key(fx2, fz2));
            placeHidingSpot(level, origin.offset(c[0], 1, c[1]), facing);
            hides--;
        }

        // 本棚：壁際に2段
        int shelves = 2 + rand.nextInt(4);
        for (int[] c : wallSide) {
            if (shelves == 0) break;
            if (!reserve(used, c[0], c[1], 0)) continue;
            place(level, origin, c[0], c[1], 1, Blocks.BOOKSHELF);
            place(level, origin, c[0], c[1], 2, Blocks.BOOKSHELF);
            shelves--;
        }

        // 机：上付きトウヒハーフ＋感圧板
        int desks = rand.nextInt(3);
        for (int[] c : open) {
            if (desks == 0) break;
            if (!reserve(used, c[0], c[1], 1)) continue;
            level.setBlockAndUpdate(origin.offset(c[0], 1, c[1]), Blocks.SPRUCE_SLAB.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            place(level, origin, c[0], c[1], 2, Blocks.OAK_PRESSURE_PLATE);
            desks--;
        }

        // 柱：広い部屋ほど多く
        int width = maxX - minX + 1;
        int pillars = width >= 20 ? rand.nextInt(width >= 28 ? 5 : 3) : 0;
        for (int[] c : open) {
            if (pillars == 0) break;
            if (!reserve(used, c[0], c[1], 2)) continue;
            var block = rand.nextBoolean() ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS;
            for (int y = 1; y < p.height; y++) place(level, origin, c[0], c[1], y, block);
            pillars--;
        }
    }

    /** 壁から部屋内側を向く方向（隣接する壁の反対側）。 */
    private static Direction facingAwayFromWall(RoomPlan p, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (p.isWall(x + d.getStepX(), z + d.getStepZ())) {
                Direction o = d.getOpposite();
                if (!p.isWall(x + o.getStepX(), z + o.getStepZ())) return o;
            }
        }
        return null;
    }

    /**
     * 隠れ場所を1つ設置する。現在はOAK_PLANKS単体（A-60の視線遮蔽判定に使用）。
     * 将来ロッカーブロックに差し替える際はここだけ変更する。facingは開口側（部屋の内側）。
     */
    public static void placeHidingSpot(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
    }

    /** 周囲spacingマス以内が未使用なら確保する。 */
    private static boolean reserve(Set<Long> used, int x, int z, int spacing) {
        for (int dx = -spacing; dx <= spacing; dx++)
            for (int dz = -spacing; dz <= spacing; dz++)
                if (used.contains(key(x + dx, z + dz))) return false;
        used.add(key(x, z));
        return true;
    }

    private static void place(ServerLevel level, BlockPos origin, int x, int z, int y, net.minecraft.world.level.block.Block block) {
        level.setBlockAndUpdate(origin.offset(x, y, z), block.defaultBlockState());
    }

    private static void placeExitSign(ServerLevel level, BlockPos pos, String text) {
        // TextDisplayのsetTextなどがprivateのため、一旦通常の看板で代用
        // 大きめ表示は後でText DisplayのEntityData経由で実装予定
        level.setBlockAndUpdate(pos, Blocks.OAK_WALL_SIGN.defaultBlockState());
        // TODO: SignBlockEntityでテキスト設定
    }
}
