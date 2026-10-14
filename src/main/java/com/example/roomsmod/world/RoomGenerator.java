package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
    /** 生成中の部屋のセクション（サーバースレッドのみで使用）。値は SectionTheme を参照。 */
    private static com.example.roomsmod.world.section.SectionTheme theme = com.example.roomsmod.world.section.Sections.A;

    private static net.minecraft.world.level.block.state.BlockState doorLower() { return theme.palette().door().get().defaultBlockState(); }
    /** 直近の choosePlan の結果（デバッグ用）。 */
    private static String lastPlanInfo = "";

    public static BlockPos generateLobby(ServerLevel level, BlockPos origin) {
        theme = com.example.roomsmod.world.section.Sections.forRoom(RoomsState.get(level), 0);
        int width = 20;
        int depth = 32;
        int height = 5;

        // 床・壁・天井
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlockAndUpdate(origin.offset(x, 0, z), theme.palette().floor().get());
                if (x == 0 || x == width - 1 || z == 0 || z == depth - 1) {
                    for (int y = 1; y < height; y++) {
                        level.setBlockAndUpdate(origin.offset(x, y, z), theme.palette().wall().get());
                    }
                }
                level.setBlockAndUpdate(origin.offset(x, height, z), theme.palette().ceiling().get());
            }
        }

        // 椅子のみ
        level.setBlockAndUpdate(origin.offset(5, 1, 10), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(10, 1, 15), Blocks.OAK_STAIRS.defaultBlockState());
        level.setBlockAndUpdate(origin.offset(15, 1, 20), Blocks.OAK_STAIRS.defaultBlockState());

        // 照明を規則正しく配置（天井に吊るす）
        for (int x = 5; x <= 15; x += 5) {
            for (int z = 8; z <= 24; z += 8) {
                level.setBlockAndUpdate(origin.offset(x, height - 1, z), theme.palette().light().get());
            }
        }

        // 出口扉
        BlockPos doorPos = origin.offset(10, 1, depth - 1);
        level.setBlockAndUpdate(doorPos, doorLower());
        level.setBlockAndUpdate(doorPos.above(), doorLower().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 出口と大きめ看板
        placeExitSign(level, origin.offset(10, 3, depth - 2), com.example.roomsmod.world.section.Sections.formatRoom(RoomsState.get(level), 1));

        // 重なり防止レジストリに登録
        // 状態をロビーから開始し、重なり防止レジストリと経路を登録
        RoomsState state = RoomsState.get(level);
        state.reset();
        List<BlockPos> path = List.of(origin.offset(10, 1, 2), origin.offset(10, 1, depth - 2), doorPos);
        state.addRoom(List.of(new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + width - 1, origin.getY() + height, origin.getZ() + depth - 1)), path);
        state.setExpectedExitDoor(doorPos);

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
        RoomShape shape = RoomShape.BOX;
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

        RoomsState state = RoomsState.get(level);
        theme = com.example.roomsmod.world.section.Sections.forRoom(state, roomNumber);
        var prevTheme = com.example.roomsmod.world.section.Sections.forRoom(state, roomNumber - 1);
        if (prevTheme != theme) RoomsDebug.log("section change at room {}: {} -> {}", roomNumber, prevTheme.id(), theme.id());
        RoomPlan plan = choosePlan(rand, origin, state.generatedRooms);
        String planInfo = lastPlanInfo;
        buildShell(level, origin, plan);
        placeFurniture(level, origin, plan, rand);

        // 出口扉
        BlockPos exitDoor = origin.offset(plan.exitX, 1, plan.exitZ);
        level.setBlockAndUpdate(exitDoor, doorLower());
        level.setBlockAndUpdate(exitDoor.above(), doorLower().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

        // 照明：天井に吊るす（既存ロジックのまま）
        int lightLevel = Math.max(0, 15 - Math.max(0, roomNumber - theme.lightFadeStart()) / theme.lightFadeStep());
        if (lightLevel > 0) {
            level.setBlockAndUpdate(origin.offset(ENTRANCE_X, plan.height - 1, plan.mainDepth / 2), theme.palette().light().get());
        }

        placeExitSign(level, origin.offset(plan.exitX, 3, plan.exitZ - 1), com.example.roomsmod.world.section.Sections.formatRoom(state, roomNumber + 1));

        // 経路（入口→折れ点→出口扉）を床+1の高さで記録
        List<BlockPos> path = new ArrayList<>();
        path.add(origin.offset(ENTRANCE_X, 1, 0));
        for (int[] w : plan.pathWaypoints) path.add(origin.offset(w[0], 1, w[1]));
        path.add(exitDoor);
        state.addRoom(toWorldBoxes(origin, plan), path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled()) {
            RoomsDebug.log("room generated #{} ({}) section={} origin={} {} boxes={} exitDoor={}", roomNumber, com.example.roomsmod.world.section.Sections.formatRoom(state, roomNumber), theme.prefix(), origin, planInfo, toWorldBoxes(origin, plan), exitDoor);
        }
        return exitDoor;
    }

    /** 候補を順に試し、既存部屋と重ならない最初のものを採用。最後は必ず通る直線の箱部屋。 */
    private static RoomPlan choosePlan(Random rand, BlockPos origin, List<List<BoundingBox>> rooms) {
        RoomShape[] shapes = RoomShape.values();
        RoomShape shape = shapes[com.example.roomsmod.world.section.SectionTheme.pickWeighted(rand, theme.shapeWeights())];
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
        int tried = 0;
        for (RoomPlan p : candidates) {
            tried++;
            if (isClear(origin, p, rooms)) {
                lastPlanInfo = "shape=" + shape + " exit=" + exit + " candidate=" + tried + "/" + candidates.size() + " fallbacksTried=" + (tried - 1);
                return p;
            }
        }
        lastPlanInfo = "shape=" + shape + " exit=" + exit + " -> all " + candidates.size() + " candidates overlapped, final fallback BOX/CENTER";
        // フォールバック：中央出口の直線箱部屋。出口は常に部屋の最大Z面にあるので、
        // 次の部屋は必ず既存部屋すべてより+Z側に置かれ、重ならない。
        return planStraight(RoomShape.BOX, ExitType.CENTER, 0, 0, 0);
    }

    /** 直線系（箱・廊下・ホール・高天井）。 */
    private static RoomPlan planStraight(RoomShape shape, ExitType exit, int a, int b, int c) {
        RoomPlan p = new RoomPlan();
        p.shape = shape;
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
        p.shape = RoomShape.L_SHAPE;
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
    private static boolean isClear(BlockPos origin, RoomPlan p, List<List<BoundingBox>> rooms) {
        if (p.exitZ != p.maxZ()) return false;
        List<BoundingBox> mine = toWorldBoxes(origin, p);
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
                    level.setBlockAndUpdate(origin.offset(x, 0, z), theme.palette().floor().get());
                    boolean entrance = z == 0 && x >= ENTRANCE_X - 1 && x <= ENTRANCE_X + 1;
                    if (p.isWall(x, z) && !entrance) {
                        for (int y = 1; y < p.height; y++) {
                            level.setBlockAndUpdate(origin.offset(x, y, z), theme.palette().wall().get());
                        }
                    } else {
                        for (int y = 1; y < p.height; y++) {
                            level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                    level.setBlockAndUpdate(origin.offset(x, p.height, z), theme.palette().ceiling().get());
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

        // 隠れ場所：壁際にロッカー。従来と同程度の3〜4個。
        int hides = theme.hidingMin() + rand.nextInt(theme.hidingExtra());
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
            var type = pickHiding(rand.nextFloat());
            placeHiding(level, origin.offset(c[0], 1, c[1]), facing, type);
            com.example.roomsmod.util.RoomsDebug.log("hiding spot type={} at {}", type.name().toLowerCase(), origin.offset(c[0], 1, c[1]));
            hides--;
        }

        // 遮蔽物：通路の脇にU字の遮蔽＋裏の隠れポケット
        placeCover(level, origin, p, rand, blocked, used);

        // 本棚：壁際に2段
        var fu = theme.furniture();
        int shelves = fu.shelfMin() + rand.nextInt(fu.shelfExtra());
        for (int[] c : wallSide) {
            if (shelves == 0) break;
            if (!reserve(used, c[0], c[1], 0)) continue;
            place(level, origin, c[0], c[1], 1, fu.shelf().get());
            place(level, origin, c[0], c[1], 2, fu.shelf().get());
            shelves--;
        }

        // 机：上付きトウヒハーフ＋感圧板
        int desks = rand.nextInt(fu.deskExtra());
        for (int[] c : open) {
            if (desks == 0) break;
            if (!reserve(used, c[0], c[1], 1)) continue;
            level.setBlockAndUpdate(origin.offset(c[0], 1, c[1]), fu.deskTop().get());
            place(level, origin, c[0], c[1], 2, fu.deskItem().get());
            desks--;
        }

        // 柱：広い部屋ほど多く
        int width = maxX - minX + 1;
        int pillars = width >= 20 ? rand.nextInt(width >= 28 ? 5 : 3) : 0;
        for (int[] c : open) {
            if (pillars == 0) break;
            if (!reserve(used, c[0], c[1], 2)) continue;
            var block = rand.nextBoolean() ? fu.pillarA().get() : fu.pillarB().get();
            for (int y = 1; y < p.height; y++) place(level, origin, c[0], c[1], y, block);
            pillars--;
        }
    }

    // ===================== 遮蔽物 =====================

    // 遮蔽物の出現率・個数・種類の重み・ブロックは SectionTheme.cover()
    /** 遮蔽の検証距離（ラッシャーの攻撃距離22.5＋余裕）。 */
    private static final double COVER_CHECK_RANGE = 24.0;
    private static final int COVER_ATTEMPTS = 60;

    enum CoverKind { BOOKSHELF_ROW, PILLAR_2X2, PARTITION, CRATES }

    /** 1つの遮蔽物。structure=置くブロック（高さ付き）、pocket=隠れる床、back=出入り用に空ける床。 */
    private record Cover(CoverKind kind, java.util.Map<Long, net.minecraft.world.level.block.state.BlockState[]> structure, Set<Long> pocket, Set<Long> back) {}

    /**
     * 通路の線分の脇（中心線から2マス以上離れた所）に、通路側を塞ぐU字の遮蔽物を置く。
     * 裏の1列が隠れポケット、その更に裏を出入り口として空ける。通路3マス幅・扉付近・ロッカー/机は blocked/used で避ける。
     * 置く前に、通路上のどの点からもポケットが見えないこと（2D視線）を検証する。部屋の床（壁以外）にしか置かない。
     */
    private static void placeCover(ServerLevel level, BlockPos origin, RoomPlan p, Random rand, Set<Long> blocked, Set<Long> used) {
        var cv = theme.cover();
        if (rand.nextFloat() >= cv.roomChance()) {
            RoomsDebug.log("cover shape={} none (roll)", p.shape);
            return;
        }
        int want = cv.min() + rand.nextInt(cv.max() - cv.min() + 1);
        int[] weights = cv.weightsFor(p.shape.name());
        List<int[]> rusherCells = new ArrayList<>();
        Set<Long> pathSet = pathCells(p);
        for (long k : pathSet) rusherCells.add(new int[]{(int) (k >> 32), (int) k});
        int placed = 0;
        List<String> report = new ArrayList<>();
        for (int attempt = 0; attempt < COVER_ATTEMPTS && placed < want; attempt++) {
            int seg = rand.nextInt(Math.max(1, p.pathWaypoints.size() - 1));
            if (seg + 1 >= p.pathWaypoints.size()) break;
            int[] a = p.pathWaypoints.get(seg), b = p.pathWaypoints.get(seg + 1);
            int len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            if (len < 4) continue;
            int dx = Integer.signum(b[0] - a[0]), dz = Integer.signum(b[1] - a[1]);
            int side = rand.nextBoolean() ? 1 : -1;
            int nx = -dz * side, nz = dx * side;
            CoverKind kind = pickKind(rand, weights);
            if (kind == null) break;
            int along = rand.nextInt(len);
            int ax = a[0] + dx * along, az = a[1] + dz * along;
            Cover c = buildCover(kind, rand, p, ax, az, dx, dz, nx, nz);
            if (!fits(p, c, blocked, used)) continue;
            if (!pocketHidden(p, c, rusherCells)) continue;
            for (var e : c.structure.entrySet()) {
                int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
                var col = e.getValue();
                for (int y = 0; y < col.length; y++) level.setBlockAndUpdate(origin.offset(x, 1 + y, z), col[y]);
            }
            used.addAll(c.structure.keySet());
            used.addAll(c.pocket);
            used.addAll(c.back);
            placed++;
            long pk = c.pocket.iterator().next();
            report.add(kind + "@" + origin.offset((int) (pk >> 32), 1, (int) pk).toShortString() + "(" + c.structure.size() + " cols)");
        }
        RoomsDebug.log("cover shape={} wanted={} placed={} {}", p.shape, want, placed, report);
    }

    private static CoverKind pickKind(Random rand, int[] w) {
        int total = 0;
        for (int v : w) total += v;
        if (total == 0) return null;
        int r = rand.nextInt(total);
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (r < 0) return CoverKind.values()[i]; }
        return null;
    }

    /** (s=通路方向, o=通路から離れる方向) のローカル座標でU字を組む。o=0 が通路中心線。 */
    private static Cover buildCover(CoverKind kind, Random rand, RoomPlan p, int ax, int az, int dx, int dz, int nx, int nz) {
        java.util.Map<Long, net.minecraft.world.level.block.state.BlockState[]> st = new java.util.LinkedHashMap<>();
        Set<Long> pocket = new HashSet<>(), back = new HashSet<>();
        int full = p.height - 1;              // 床+1〜天井直下
        int tall = Math.min(3, full);
        java.util.function.BiFunction<Integer, Integer, Long> at = (sv, ov) -> key(ax + dx * sv + nx * ov, az + dz * sv + nz * ov);
        switch (kind) {
            case BOOKSHELF_ROW, PARTITION, CRATES -> {
                int l = switch (kind) { case BOOKSHELF_ROW -> 4 + rand.nextInt(3); case PARTITION -> 5 + rand.nextInt(3); default -> 3 + rand.nextInt(2); };
                int s0 = -l / 2;
                for (int sv = s0; sv < s0 + l; sv++) {
                    st.put(at.apply(sv, 2), coverColumn(kind, rand, kind == CoverKind.PARTITION ? full : tall));
                }
                st.put(at.apply(s0, 3), coverColumn(kind, rand, kind == CoverKind.PARTITION ? full : tall));
                st.put(at.apply(s0 + l - 1, 3), coverColumn(kind, rand, kind == CoverKind.PARTITION ? full : tall));
                for (int sv = s0 + 1; sv < s0 + l - 1; sv++) { pocket.add(at.apply(sv, 3)); back.add(at.apply(sv, 4)); }
            }
            case PILLAR_2X2 -> {
                net.minecraft.world.level.block.state.BlockState[] pil = column(theme.cover().pillar().get(), full);
                for (int sv = 0; sv <= 1; sv++) for (int ov = 2; ov <= 3; ov++) st.put(at.apply(sv, ov), pil);
                // 2x2の両脇から裏へ回り込む袖（ポケットの横を塞ぐ）
                for (int ov = 3; ov <= 4; ov++) {
                    st.put(at.apply(-1, ov), column(theme.cover().pillarWing().get(), tall));
                    st.put(at.apply(2, ov), column(theme.cover().pillarWing().get(), tall));
                }
                for (int sv = 0; sv <= 1; sv++) { pocket.add(at.apply(sv, 4)); back.add(at.apply(sv, 5)); }
            }
        }
        return new Cover(kind, st, pocket, back);
    }

    private static net.minecraft.world.level.block.state.BlockState[] column(net.minecraft.world.level.block.state.BlockState b, int h) {
        var col = new net.minecraft.world.level.block.state.BlockState[Math.max(2, h)];
        java.util.Arrays.fill(col, b);
        return col;
    }

    private static net.minecraft.world.level.block.state.BlockState[] coverColumn(CoverKind kind, Random rand, int h) {
        return switch (kind) {
            case BOOKSHELF_ROW -> column(theme.cover().shelf().get(), h);
            case PARTITION -> column(theme.cover().partition().get(), h);
            default -> {
                int hh = Math.max(2, Math.min(h, 2 + rand.nextInt(2)));
                var col = new net.minecraft.world.level.block.state.BlockState[hh];
                for (int y = 0; y < hh; y++) col[y] = rand.nextBoolean() ? theme.cover().crateA().get() : theme.cover().crateB().get();
                yield col;
            }
        };
    }

    /** 全セルが部屋の床（壁でない）で、通路・扉付近・他の家具と重ならないか。構造物は他の家具から1マス空ける。 */
    private static boolean fits(RoomPlan p, Cover c, Set<Long> blocked, Set<Long> used) {
        for (long k : c.structure.keySet()) {
            int x = (int) (k >> 32), z = (int) k;
            if (!p.inside(x, z) || p.isWall(x, z) || blocked.contains(k)) return false;
            for (int ddx = -1; ddx <= 1; ddx++)
                for (int ddz = -1; ddz <= 1; ddz++)
                    if (used.contains(key(x + ddx, z + ddz))) return false;
        }
        for (Set<Long> set : List.of(c.pocket, c.back)) {
            for (long k : set) {
                int x = (int) (k >> 32), z = (int) k;
                if (!p.inside(x, z) || p.isWall(x, z) || blocked.contains(k) || used.contains(k) || c.structure.containsKey(k)) return false;
            }
        }
        return !c.pocket.isEmpty();
    }

    /** 通路セル（ラッシャーが通りうる3マス幅）のどこから見ても、ポケットの全セル中心が遮られているか（2D）。 */
    private static boolean pocketHidden(RoomPlan p, Cover c, List<int[]> rusherCells) {
        for (long pk : c.pocket) {
            double qx = (int) (pk >> 32) + 0.5, qz = (int) pk + 0.5;
            for (int[] r : rusherCells) {
                double rx = r[0] + 0.5, rz = r[1] + 0.5;
                double d = Math.hypot(qx - rx, qz - rz);
                if (d > COVER_CHECK_RANGE) continue;
                if (lineClear(p, c, rx, rz, qx, qz, d)) return false;
            }
        }
        return true;
    }

    private static boolean lineClear(RoomPlan p, Cover c, double x0, double z0, double x1, double z1, double d) {
        int steps = (int) Math.ceil(d / 0.1);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int cx = (int) Math.floor(x0 + (x1 - x0) * t), cz = (int) Math.floor(z0 + (z1 - z0) * t);
            long k = key(cx, cz);
            if (c.structure.containsKey(k) || !p.inside(cx, cz) || p.isWall(cx, cz)) return false;
        }
        return true;
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

    /** 隠れ場所の種類を theme.hidingMix() から累積判定で選ぶ（f は nextFloat の値）。 */
    private static com.example.roomsmod.world.section.SectionTheme.HidingType pickHiding(float f) {
        float total = 0f;
        for (var w : theme.hidingMix()) total += w.weight();
        float r = f * total, cum = 0f;
        for (var w : theme.hidingMix()) { cum += w.weight(); if (r < cum) return w.value(); }
        return theme.hidingMix().get(theme.hidingMix().size() - 1).value();
    }

    /** 隠れ場所を種類ごとに置く。新しい種類はここに分岐を足す。 */
    private static void placeHiding(ServerLevel level, BlockPos pos, Direction facing, com.example.roomsmod.world.section.SectionTheme.HidingType type) {
        switch (type) {
            case TABLE -> level.setBlockAndUpdate(pos, com.example.roomsmod.RoomsMod.HIDING_TABLE.get().defaultBlockState());
            case LOCKER -> placeHidingSpot(level, pos, facing);
        }
    }

    /**
     * 隠れ場所（2マスのロッカー、正面=facing）を1つ設置する。
     * 将来ロッカーブロックに差し替える際はここだけ変更する。facingは開口側（部屋の内側）。
     */
    public static void placeHidingSpot(ServerLevel level, BlockPos pos, Direction facing) {
        var lower = com.example.roomsmod.RoomsMod.LOCKER.get().defaultBlockState()
                .setValue(com.example.roomsmod.block.LockerBlock.FACING, facing);
        level.setBlock(pos, lower, 2);
        level.setBlock(pos.above(), lower.setValue(com.example.roomsmod.block.LockerBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        level.blockUpdated(pos, lower.getBlock());
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

    /**
     * 扉の上の部屋番号プレート。pos は扉の1マス手前（来る側＝部屋の内側）・床+3。
     * 北向き（-Z側から読める）で、出口扉のある+Z側の壁に掛ける。テキストは発光させ、編集不可にする。
     */
    private static void placeExitSign(ServerLevel level, BlockPos pos, String text) {
        BlockPos wall = pos.relative(Direction.SOUTH);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(wall).isSolid()) {
            RoomsDebug.log("room number sign {} skipped at {} (space occupied or no wall)", text, pos);
            return;
        }
        level.setBlockAndUpdate(pos, theme.sign().wallSign().get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallSignBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
            var st = new net.minecraft.world.level.block.entity.SignText()
                    .setMessage(1, net.minecraft.network.chat.Component.literal(text))
                    .setColor(theme.sign().color())
                    .setHasGlowingText(theme.sign().glowing());
            sign.setText(st, true);
            sign.setWaxed(true);
            sign.setChanged();
            level.sendBlockUpdated(pos, sign.getBlockState(), sign.getBlockState(), 3);
        }
        RoomsDebug.log("room number sign {} at {}", text, pos);
    }
}
