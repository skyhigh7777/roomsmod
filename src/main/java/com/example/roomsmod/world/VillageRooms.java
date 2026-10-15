package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomGenerator.Rect;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.*;

/**
 * V（Village）区画の部屋。A の形は使わず、小さな民家（内寸5〜9）を作る。
 * <ul>
 *   <li>SINGLE: 1部屋の家</li>
 *   <li>TWO_ROOM: 手前と奥の2部屋を内扉でつなぐ</li>
 *   <li>SIDE_ROOM: 手前の部屋の横の部屋へ内扉で入り、その奥壁に出口</li>
 *   <li>TWO_STORY（約22%）: 階段で2階へ。1階が基準高さなら上り（出口は2階）、上がった後なら下り（入口が2階、出口は1階）。
 *       高さは基準/基準+4 の2段だけを行き来する。</li>
 * </ul>
 * ローカル座標: x,z は origin 基準（入口 x=10, z=0 の壁）、y は「家の1階床=0」。origin は入口の床なので、下りの家は base=origin-4。
 * 経路（RoomsState の waypoint）は足の高さで、階段は1段ごとに「上がる→進む」の点を持つ（検査とラッシャー移動の両方が通る）。
 */
final class VillageRooms {
    private VillageRooms() {}

    static final int STORY = 4;           // 1階の床〜2階の床
    static final int BASE_Y = 1;          // 区画の基準床
    static final float TWO_STORY_CHANCE = 0.22f;
    private static final int EX = 10;     // 入口 x

    enum Layout { SINGLE, TWO_ROOM, SIDE_ROOM, TWO_STORY }

    static final class Plan {
        Layout layout;
        final List<Rect> rects = new ArrayList<>();
        int stories = 1;
        int entranceLevel = 0;            // 0 or STORY
        int exitX, exitZ, exitLevel;
        final List<int[]> innerDoors = new ArrayList<>(); // {x, z, axisX(1=壁がx方向に面する)}
        boolean hasStairs; int sx, zb, sdz;               // 階段: 列 sx、1階の手前 zb、上り方向 sdz
        final List<int[]> path = new ArrayList<>();       // {x, y(足), z}

        boolean inside(int x, int z) { for (Rect r : rects) if (r.contains(x, z)) return true; return false; }
        /** 各矩形の外周が壁（矩形どうしは辺だけ共有するので共有辺は内壁になる）。 */
        boolean isWall(int x, int z) {
            for (Rect r : rects) if (r.contains(x, z) && (x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1())) return true;
            return false;
        }
        boolean isCorner(int x, int z) {
            for (Rect r : rects) if ((x == r.x0() || x == r.x1()) && (z == r.z0() || z == r.z1())) return true;
            return false;
        }
        boolean floorCell(int x, int z) { return inside(x, z) && !isWall(x, z); }
        int height() { return stories * STORY; }
        int maxZ() { return rects.stream().mapToInt(Rect::z1).max().orElse(0); }
        boolean stairCell(int x, int z) {
            if (!hasStairs || x != sx) return false;
            for (int k = 1; k <= 3; k++) if (z == zb + k * sdz) return true;
            return false;
        }
    }

    private static long key(int x, int z) { return RoomGenerator.key(x, z); }

    // ===================== 設計 =====================

    private static int r(Random rand, int lo, int hi) { return lo + rand.nextInt(hi - lo + 1); }

    /** 入口 x=10 が端でない内寸 w の左端 x を返す。 */
    private static int interiorX0(Random rand, int w, BlockPos origin) {
        int lo = EX - w + 2, hi = EX - 1;
        if (origin.getX() > 40) return lo + rand.nextInt(Math.max(1, (hi - lo + 1) / 2));            // 右に流れたら左へ
        if (origin.getX() < -40) return hi - rand.nextInt(Math.max(1, (hi - lo + 1) / 2));
        return r(rand, lo, hi);
    }

    private static Plan planSingle(Random rand, BlockPos origin, int w, int d) {
        Plan p = new Plan();
        p.layout = Layout.SINGLE;
        int ix0 = interiorX0(rand, w, origin), ix1 = ix0 + w - 1;
        p.rects.add(new Rect(ix0 - 1, 0, ix1 + 1, d + 1));
        p.exitX = r(rand, ix0 + 1, ix1 - 1);
        p.exitZ = d + 1;
        p.path.add(new int[]{EX, 1, 0});
        p.path.add(new int[]{EX, 1, 1});
        p.path.add(new int[]{EX, 1, d});
        p.path.add(new int[]{p.exitX, 1, d});
        p.path.add(new int[]{p.exitX, 1, d + 1});
        return p;
    }

    private static Plan planTwoRoom(Random rand, BlockPos origin) {
        Plan p = new Plan();
        p.layout = Layout.TWO_ROOM;
        int w1 = r(rand, 5, 8), d1 = r(rand, 4, 6), w2 = r(rand, 5, 8), d2 = r(rand, 4, 6);
        int fx0 = interiorX0(rand, w1, origin), fx1 = fx0 + w1 - 1;
        int bx0 = fx0 + r(rand, -(w2 - 3), w1 - 3), bx1 = bx0 + w2 - 1;
        int lo = Math.max(fx0, bx0), hi = Math.min(fx1, bx1);      // 共有する内側の列（3以上）
        int di = r(rand, lo + (hi - lo >= 2 ? 1 : 0), hi - (hi - lo >= 2 ? 1 : 0));
        int wallZ = d1 + 1, maxZ = d1 + 2 + d2;
        p.rects.add(new Rect(fx0 - 1, 0, fx1 + 1, wallZ));
        p.rects.add(new Rect(bx0 - 1, wallZ, bx1 + 1, maxZ));
        p.innerDoors.add(new int[]{di, wallZ, 0});
        p.exitX = r(rand, bx0 + 1, bx1 - 1);
        p.exitZ = maxZ;
        int[][] pts = {{EX, 0}, {EX, 1}, {EX, d1}, {di, d1}, {di, maxZ - 1}, {p.exitX, maxZ - 1}, {p.exitX, maxZ}};
        for (int[] q : pts) p.path.add(new int[]{q[0], 1, q[1]});
        return p;
    }

    private static Plan planSide(Random rand, BlockPos origin) {
        Plan p = new Plan();
        p.layout = Layout.SIDE_ROOM;
        int w1 = r(rand, 5, 7), d1 = r(rand, 5, 7), w2 = r(rand, 4, 6), d2 = r(rand, 5, 7);
        boolean right = origin.getX() > 40 ? false : origin.getX() < -40 || rand.nextBoolean();
        int fx0 = interiorX0(rand, w1, origin), fx1 = fx0 + w1 - 1;
        int sz0 = r(rand, Math.max(2, d1 + 2 - d2), d1 - 1), sz1 = Math.max(sz0 + d2 - 1, d1 + 1);
        int wallX = right ? fx1 + 1 : fx0 - 1;
        int far = right ? wallX + w2 + 1 : wallX - w2 - 1;
        p.rects.add(new Rect(fx0 - 1, 0, fx1 + 1, d1 + 1));
        p.rects.add(new Rect(Math.min(wallX, far), sz0 - 1, Math.max(wallX, far), sz1 + 1));
        int dz = r(rand, sz0, d1);
        p.innerDoors.add(new int[]{wallX, dz, 1});
        int sIn0 = Math.min(wallX, far) + 1, sIn1 = Math.max(wallX, far) - 1;
        p.exitX = r(rand, sIn0, sIn1);
        p.exitZ = sz1 + 1;
        int[][] pts = {{EX, 0}, {EX, 1}, {EX, dz}, {p.exitX, dz}, {p.exitX, sz1}, {p.exitX, sz1 + 1}};
        for (int[] q : pts) p.path.add(new int[]{q[0], 1, q[1]});
        return p;
    }

    /** 階段の経由点（上り順）。1段ごとに「上がる→進む」。 */
    private static List<int[]> stairPoints(int x, int zb, int dz) {
        List<int[]> l = new ArrayList<>();
        l.add(new int[]{x, 1, zb});
        for (int k = 1; k <= 4; k++) {
            l.add(new int[]{x, k + 1, zb + (k - 1) * dz});
            l.add(new int[]{x, k + 1, zb + k * dz});
        }
        return l;
    }

    private static Plan planTwoStory(Random rand, BlockPos origin, boolean descending) {
        Plan p = new Plan();
        p.layout = Layout.TWO_STORY;
        p.stories = 2;
        int w = r(rand, 7, 9), d = r(rand, 7, 9);
        int ix0 = interiorX0(rand, w, origin), ix1 = ix0 + w - 1;
        p.rects.add(new Rect(ix0 - 1, 0, ix1 + 1, d + 1));
        p.hasStairs = true;
        p.sx = rand.nextBoolean() ? ix0 : ix1;
        int u = STORY + 1;
        // 出口は階段の列と離す
        List<Integer> xs = new ArrayList<>();
        for (int x = ix0 + 1; x <= ix1 - 1; x++) if (Math.abs(x - p.sx) >= 2) xs.add(x);
        p.exitX = xs.get(rand.nextInt(xs.size()));
        p.exitZ = d + 1;
        if (!descending) {
            p.sdz = 1; p.zb = d - 4;                   // 上り: 段 d-3..d-1、2階の着地 z=d
            p.entranceLevel = 0; p.exitLevel = STORY;
            p.path.add(new int[]{EX, 1, 0});
            p.path.add(new int[]{EX, 1, 1});
            p.path.add(new int[]{EX, 1, p.zb});
            p.path.addAll(stairPoints(p.sx, p.zb, 1));
            p.path.add(new int[]{p.exitX, u, d});
            p.path.add(new int[]{p.exitX, u, d + 1});
        } else {
            p.sdz = -1; p.zb = 5;                      // 下り: 2階の着地 z=1、段 4..2、1階 z=5
            p.entranceLevel = STORY; p.exitLevel = 0;
            p.path.add(new int[]{EX, u, 0});
            p.path.add(new int[]{EX, u, 1});
            List<int[]> st = stairPoints(p.sx, p.zb, -1);
            Collections.reverse(st);
            p.path.addAll(st);
            p.path.add(new int[]{p.exitX, 1, p.zb});
            p.path.add(new int[]{p.exitX, 1, d});
            p.path.add(new int[]{p.exitX, 1, d + 1});
        }
        return p;
    }

    private static Plan choose(Random rand, BlockPos origin, List<List<BoundingBox>> rooms) {
        Plan p;
        float f = rand.nextFloat();
        if (f < TWO_STORY_CHANCE) p = planTwoStory(rand, origin, origin.getY() > BASE_Y);
        else if (f < TWO_STORY_CHANCE + 0.28f) p = planSingle(rand, origin, r(rand, 5, 9), r(rand, 5, 9));
        else if (f < TWO_STORY_CHANCE + 0.56f) p = planTwoRoom(rand, origin);
        else p = planSide(rand, origin);
        if (isClear(origin, p, rooms)) return p;
        RoomsDebug.log("village plan {} overlapped, fallback SINGLE 5x5", p.layout);
        Plan fb = planSingle(new Random(0), BlockPos.ZERO, 5, 5);
        return fb;
    }

    private static int baseY(BlockPos origin, Plan p) { return origin.getY() - p.entranceLevel; }

    private static List<BoundingBox> boxes(BlockPos origin, Plan p) {
        int by = baseY(origin, p);
        List<BoundingBox> l = new ArrayList<>();
        for (Rect r : p.rects)
            l.add(new BoundingBox(origin.getX() + r.x0(), by, origin.getZ() + r.z0(), origin.getX() + r.x1(), by + p.height(), origin.getZ() + r.z1()));
        return l;
    }

    private static boolean isClear(BlockPos origin, Plan p, List<List<BoundingBox>> rooms) {
        if (p.exitZ != p.maxZ()) return false;
        List<BoundingBox> mine = boxes(origin, p);
        for (int i = 0; i < rooms.size(); i++) {
            boolean previous = i == rooms.size() - 1;
            for (BoundingBox o : rooms.get(i)) for (BoundingBox b : mine) if ((previous ? b : b.inflatedBy(1)).intersects(o)) return false;
        }
        return true;
    }

    // ===================== 生成 =====================

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        var theme = Sections.forRoom(state, roomNumber);
        Plan p = choose(rand, origin, state.generatedRooms);
        BlockPos base = new BlockPos(origin.getX(), baseY(origin, p), origin.getZ());
        Ctx c = new Ctx(level, base, p, rand);
        buildShell(c, theme);
        BlockPos exitDoor = base.offset(p.exitX, p.exitLevel + 1, p.exitZ);
        door(level, exitDoor, Direction.NORTH);
        RoomGenerator.sealExit(level, exitDoor);
        for (int[] d : p.innerDoors) door(level, base.offset(d[0], 1, d[1]), d[2] == 1 ? Direction.EAST : Direction.WEST);
        if (p.hasStairs) buildStairs(c);
        furnish(c, theme, Sections.localRoom(state, roomNumber));
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = new ArrayList<>();
        for (int[] w : p.path) path.add(base.offset(w[0], w[1], w[2]));
        state.addRoom(boxes(origin, p), path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=V origin={} village layout={} stories={} {} boxes={} exitDoor={} decor={}", roomNumber,
                    Sections.formatRoom(state, roomNumber), origin, p.layout, p.stories,
                    p.stories == 2 ? (p.entranceLevel > 0 ? "descending" : "ascending") : "", boxes(origin, p), exitDoor, c.decor);
        return exitDoor;
    }

    private static final class Ctx {
        final ServerLevel level; final BlockPos base; final Plan p; final Random rand;
        final Map<String, Integer> decor = new TreeMap<>();
        Ctx(ServerLevel level, BlockPos base, Plan p, Random rand) { this.level = level; this.base = base; this.p = p; this.rand = rand; }
        void set(int x, int y, int z, BlockState s) { level.setBlockAndUpdate(base.offset(x, y, z), s); }
        BlockState get(int x, int y, int z) { return level.getBlockState(base.offset(x, y, z)); }
        void count(String k) { decor.merge(k, 1, Integer::sum); }
    }

    private static void door(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState lower = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing);
        level.setBlockAndUpdate(pos, lower);
        level.setBlockAndUpdate(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void buildShell(Ctx c, com.example.roomsmod.world.section.SectionTheme theme) {
        Plan p = c.p;
        int h = p.height();
        Set<Long> done = new HashSet<>();
        BlockState floor = theme.palette().floor().get(), wall = theme.palette().wall().get(), ceil = theme.palette().ceiling().get();
        BlockState post = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        for (Rect r : p.rects)
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++) {
                    if (!done.add(key(x, z))) continue;
                    c.set(x, 0, z, floor);
                    boolean w = p.isWall(x, z);
                    for (int y = 1; y < h; y++) {
                        BlockState s = w ? (p.isCorner(x, z) ? post : wall) : Blocks.AIR.defaultBlockState();
                        if (w && (y == STORY) && p.stories == 2) s = post; // 2階の床の高さに梁
                        if (!w && y == STORY && p.stories == 2 && !p.stairCell(x, z)) s = floor;
                        c.set(x, y, z, s);
                    }
                    c.set(x, h, z, ceil);
                }
        // 入口（1マス幅）
        int ey = p.entranceLevel;
        c.set(EX, ey + 1, 0, Blocks.AIR.defaultBlockState());
        c.set(EX, ey + 2, 0, Blocks.AIR.defaultBlockState());
    }

    private static void buildStairs(Ctx c) {
        Plan p = c.p;
        Direction up = p.sdz > 0 ? Direction.SOUTH : Direction.NORTH;
        // 4段すべて階段ブロック（最上段 k=STORY は2階の床の高さに置くので上面が2階と揃う）。しゃがみ歩きで上れる（段差は全て0.5）。
        for (int k = 1; k <= STORY; k++) {
            int z = p.zb + k * p.sdz;
            for (int y = 1; y < k; y++) c.set(p.sx, y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            c.set(p.sx, k, z, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
            if (k < STORY) c.set(p.sx, STORY, z, Blocks.AIR.defaultBlockState());
        }
        // 2階の手すり（吹き抜けの内側と、着地の反対側）
        int in = p.sx == p.rects.get(0).x0() + 1 ? 1 : -1;
        List<BlockPos> rails = new ArrayList<>();
        for (int k = 1; k <= 3; k++) rails.add(new BlockPos(p.sx + in, STORY + 1, p.zb + k * p.sdz));
        rails.add(new BlockPos(p.sx, STORY + 1, p.zb));
        for (BlockPos r : rails) c.level.setBlockAndUpdate(c.base.offset(r), Blocks.SPRUCE_FENCE.defaultBlockState());
        for (BlockPos r : rails) {
            BlockPos w = c.base.offset(r);
            c.level.setBlockAndUpdate(w, Block.updateFromNeighbourShapes(c.level.getBlockState(w), c.level, w));
        }
    }

    // ===================== 家具 =====================

    private static final Block[] POTS = {Blocks.POTTED_POPPY, Blocks.POTTED_DANDELION, Blocks.POTTED_FERN, Blocks.POTTED_AZURE_BLUET,
            Blocks.POTTED_RED_TULIP, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_OAK_SAPLING, Blocks.POTTED_LILY_OF_THE_VALLEY};
    private static final Block[] CARPETS = {Blocks.RED_CARPET, Blocks.BROWN_CARPET, Blocks.GREEN_CARPET, Blocks.BLUE_CARPET,
            Blocks.YELLOW_CARPET, Blocks.WHITE_CARPET, Blocks.ORANGE_CARPET, Blocks.CYAN_CARPET};
    private static final Block[] BEDS = {Blocks.RED_BED, Blocks.WHITE_BED, Blocks.BLUE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED, Blocks.BROWN_BED};

    /** 階ごとの家具配置用の集合。 */
    private static final class Floor {
        final int y; // 足の高さ（1 or 5）
        final Set<Long> blocked = new HashSet<>(), used = new HashSet<>();
        Floor(int y) { this.y = y; }
    }

    private static void furnish(Ctx c, com.example.roomsmod.world.section.SectionTheme theme, int localRoom) {
        Plan p = c.p;
        Random rand = c.rand;
        List<Floor> floors = new ArrayList<>();
        floors.add(new Floor(1));
        if (p.stories == 2) floors.add(new Floor(STORY + 1));
        // 通路（幅3）を階ごとに塞ぐ
        for (int i = 0; i + 1 < p.path.size(); i++) {
            int[] s = p.path.get(i), e = p.path.get(i + 1);
            Floor f = s[1] >= STORY + 1 && e[1] >= STORY + 1 ? floors.get(floors.size() - 1) : s[1] <= 2 && e[1] <= 2 ? floors.get(0) : null;
            if (f == null) continue; // 階段の途中
            int x = s[0], z = s[2];
            while (true) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) f.blocked.add(key(x + dx, z + dz));
                if (x == e[0] && z == e[2]) break;
                if (x != e[0]) x += Integer.signum(e[0] - x); else z += Integer.signum(e[2] - z);
            }
        }
        if (p.hasStairs) for (Floor f : floors)
            for (int k = 0; k <= 4; k++) for (int dx = -1; dx <= 1; dx++) f.blocked.add(key(p.sx + dx, p.zb + k * p.sdz));
        for (int[] d : p.innerDoors)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) floors.get(0).blocked.add(key(d[0] + dx, d[1] + dz));

        // 壁際セル
        List<Object[]> wallSide = new ArrayList<>(); // {Floor, int[]{x,z}, Direction}
        for (Floor f : floors) for (int[] cell : floorCells(p)) {
            if (f.blocked.contains(key(cell[0], cell[1]))) continue;
            Direction face = facingAway(p, cell[0], cell[1]);
            if (face != null) wallSide.add(new Object[]{f, cell, face});
        }
        Collections.shuffle(wallSide, rand);

        // 隠れ場所（机60%/ロッカー40%）
        int hides = theme.hidingMin() + rand.nextInt(theme.hidingExtra());
        for (Object[] w : wallSide) {
            if (hides == 0) break;
            Floor f = (Floor) w[0]; int[] cell = (int[]) w[1]; Direction face = (Direction) w[2];
            int fx = cell[0] + face.getStepX(), fz = cell[1] + face.getStepZ();
            if (!p.floorCell(fx, fz) || f.used.contains(key(fx, fz))) continue;
            if (!RoomGenerator.reserve(f.used, cell[0], cell[1], 1)) continue;
            f.used.add(key(fx, fz));
            var type = RoomGenerator.pickHiding(rand.nextFloat());
            RoomGenerator.placeHiding(c.level, c.base.offset(cell[0], f.y, cell[1]), face, type);
            RoomsDebug.log("hiding spot type={} at {}", type.name().toLowerCase(), c.base.offset(cell[0], f.y, cell[1]));
            hides--;
        }

        // 遮蔽物（1部屋の家の1階だけ。狭い家では置けないことが多い）
        if (p.rects.size() == 1) {
            RoomGenerator.RoomPlan rp = new RoomGenerator.RoomPlan();
            rp.rects.add(p.rects.get(0));
            rp.height = STORY;
            for (int[] w : p.path) if (w[1] == 1) rp.pathWaypoints.add(new int[]{w[0], w[2]});
            // 階段区間をまたぐ線分ができないよう、1階の連続部分だけ使う
            if (p.hasStairs && p.entranceLevel == 0) { /* 入口→階段手前は連続 */ }
            RoomGenerator.placeCover(c.level, c.base, rp, rand, floors.get(0).blocked, floors.get(0).used);
        }

        // 大物: ベッド（2階建ては2階に必ず、他は50%）・暖炉（35%）
        Floor top = floors.get(floors.size() - 1);
        int beds = p.stories == 2 ? 1 + rand.nextInt(2) : rand.nextFloat() < 0.5f ? 1 : 0;
        for (Object[] w : wallSide) {
            if (beds == 0) break;
            Floor f = (Floor) w[0]; if (f != top) continue;
            int[] cell = (int[]) w[1]; Direction face = (Direction) w[2];
            int fx = cell[0] + face.getStepX(), fz = cell[1] + face.getStepZ();
            if (!free(p, f, cell[0], cell[1]) || !free(p, f, fx, fz)) continue;
            Block bed = BEDS[rand.nextInt(BEDS.length)];
            BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, face.getOpposite()).setValue(BedBlock.PART, BedPart.FOOT);
            c.set(fx, f.y, fz, foot);
            c.set(cell[0], f.y, cell[1], foot.setValue(BedBlock.PART, BedPart.HEAD));
            f.used.add(key(cell[0], cell[1])); f.used.add(key(fx, fz));
            c.count("bed");
            // 枕元に小机＋ろうそく
            Direction t = face.getClockWise();
            int nx = cell[0] + t.getStepX(), nz = cell[1] + t.getStepZ();
            if (free(p, f, nx, nz) && facingAway(p, nx, nz) != null) {
                c.set(nx, f.y, nz, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
                c.set(nx, f.y + 1, nz, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + rand.nextInt(3)));
                f.used.add(key(nx, nz)); c.count("nightstand");
            }
            beds--;
        }
        if (rand.nextFloat() < 0.35f) {
            for (Object[] w : wallSide) {
                Floor f = (Floor) w[0]; int[] cell = (int[]) w[1]; Direction face = (Direction) w[2];
                Direction t = face.getClockWise();
                int ax = cell[0] - t.getStepX(), az = cell[1] - t.getStepZ(), bx = cell[0] + t.getStepX(), bz = cell[1] + t.getStepZ();
                if (!free(p, f, cell[0], cell[1]) || !free(p, f, ax, az) || !free(p, f, bx, bz)) continue;
                if (facingAway(p, ax, az) != face || facingAway(p, bx, bz) != face) continue;
                int fx = cell[0] + face.getStepX(), fz = cell[1] + face.getStepZ();
                if (!p.floorCell(fx, fz)) continue;
                for (int y = f.y; y < f.y + 3; y++) {
                    c.set(ax, y, az, Blocks.BRICKS.defaultBlockState());
                    c.set(bx, y, bz, Blocks.BRICKS.defaultBlockState());
                    if (y > f.y) c.set(cell[0], y, cell[1], Blocks.BRICKS.defaultBlockState());
                }
                c.set(cell[0], f.y, cell[1], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.FACING, face).setValue(CampfireBlock.SIGNAL_FIRE, false));
                f.used.add(key(cell[0], cell[1])); f.used.add(key(ax, az)); f.used.add(key(bx, bz)); f.used.add(key(fx, fz));
                c.count("fireplace");
                break;
            }
        }

        // 小物を壁際に（空いている壁際の約7割）
        for (Object[] w : wallSide) {
            Floor f = (Floor) w[0]; int[] cell = (int[]) w[1]; Direction face = (Direction) w[2];
            if (!free(p, f, cell[0], cell[1]) || rand.nextFloat() >= 0.7f) continue;
            f.used.add(key(cell[0], cell[1]));
            wallItem(c, cell[0], f.y, cell[1], face);
        }

        // テーブルと椅子（部屋の中ほど可）
        for (Floor f : floors) {
            List<int[]> cells = floorCells(p);
            Collections.shuffle(cells, rand);
            for (int[] cell : cells) {
                int x = cell[0], z = cell[1];
                boolean ok = true;
                for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1 && ok; dz++) ok = free(p, f, x + dx, z + dz);
                if (!ok) continue;
                c.set(x, f.y, z, Blocks.SPRUCE_FENCE.defaultBlockState());
                c.set(x, f.y + 1, z, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
                f.used.add(key(x, z));
                int chairs = 1 + rand.nextInt(2);
                List<Direction> dirs = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
                Collections.shuffle(dirs, rand);
                for (Direction d : dirs) {
                    if (chairs == 0) break;
                    c.set(x + d.getStepX(), f.y, z + d.getStepZ(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, d));
                    f.used.add(key(x + d.getStepX(), z + d.getStepZ()));
                    chairs--;
                }
                c.count("table+chairs");
                break;
            }
        }

        // じゅうたん（通路の外の空き 2x2〜3x2）
        for (Floor f : floors) {
            List<int[]> cells = floorCells(p);
            Collections.shuffle(cells, rand);
            int rugs = 1 + rand.nextInt(2);
            Block carpet = CARPETS[rand.nextInt(CARPETS.length)];
            for (int[] cell : cells) {
                if (rugs == 0) break;
                int w = 2 + rand.nextInt(2), d = 2;
                boolean ok = true;
                for (int dx = 0; dx < w && ok; dx++) for (int dz = 0; dz < d && ok; dz++) ok = free(p, f, cell[0] + dx, cell[1] + dz);
                if (!ok) continue;
                for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < d; dz++) {
                    c.set(cell[0] + dx, f.y, cell[1] + dz, carpet.defaultBlockState());
                    f.used.add(key(cell[0] + dx, cell[1] + dz));
                }
                c.count("rug");
                rugs--;
            }
        }

        // 照明: セクション内の部屋番号が進むほど少なく・暗く（RoomLighting）。各階ごとに配置
        RoomLighting.Fixture lf = RoomLighting.fixture(
                RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        List<int[]> interiors = new ArrayList<>();
        for (Rect r : p.rects) interiors.add(new int[]{r.x0() + 1, r.z0() + 1, r.x1() - 1, r.z1() - 1});
        List<int[]> path2d = new ArrayList<>();
        for (int[] q : p.path) path2d.add(new int[]{q[0], q[2]});
        for (Floor f : floors) {
            int placed = 0;
            for (int[] cand : RoomLighting.candidates(interiors, lf)) {
                if (placed >= lf.maxCount()) break;
                int x = cand[0], z = cand[1];
                if (!p.floorCell(x, z)) continue;
                if (p.hasStairs && Math.abs(x - p.sx) <= 1 && between(z, p.zb, p.zb + 4 * p.sdz)) continue;
                if (x == p.exitX && z == p.exitZ - 1) continue; // 番号プレートの場所
                if (!lf.hanging() && (RoomLighting.nearPath(path2d, x, z) || f.used.contains(key(x, z)))) continue;
                int y = lf.hanging() ? f.y + 2 : f.y;
                if (!c.get(x, y, z).isAir()) continue;
                if (lf.hanging() ? c.get(x, y + 1, z).isAir() : c.get(x, y - 1, z).isAir()) continue;
                c.set(x, y, z, lf.state().get());
                c.count("light");
                placed++;
            }
        }

        // 窓（外側の側壁に板ガラス、外は黒コンクリートで夜の暗さ）
        for (Floor f : floors) {
            int y = f.y + 1;
            Set<Long> win = new HashSet<>();
            for (Rect r : p.rects)
                for (int x = r.x0(); x <= r.x1(); x++)
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        if (!p.isWall(x, z) || p.isCorner(x, z) || z == 0 || z == p.maxZ()) continue;
                        Direction out = null;
                        for (Direction d : Direction.Plane.HORIZONTAL)
                            if (!p.inside(x + d.getStepX(), z + d.getStepZ()) && p.floorCell(x - d.getStepX(), z - d.getStepZ())) out = d;
                        if (out == null || win.contains(key(x, z))) continue;
                        Direction t = out.getClockWise();
                        if (win.contains(key(x + t.getStepX(), z + t.getStepZ())) || win.contains(key(x - t.getStepX(), z - t.getStepZ()))) continue;
                        if (!c.get(x, y, z).is(theme.palette().wall().get().getBlock())) continue;
                        if (rand.nextFloat() >= 0.4f) continue;
                        BlockPos back = c.base.offset(x + out.getStepX(), y, z + out.getStepZ());
                        // 板ガラスの外に1ブロックの空気ポケット（V-100 が入る）を作り、その周り（奥・左右・上下）を黒コンクリートで塞ぐ
                        if (!c.level.getBlockState(back).isAir()) continue;
                        BlockPos[] shell = {back.relative(out), back.relative(t), back.relative(t.getOpposite()), back.above(), back.below()};
                        boolean ok = true;
                        for (BlockPos s2 : shell) { var bs = c.level.getBlockState(s2); if (!bs.isAir() && !bs.isSolidRender(c.level, s2)) ok = false; }
                        if (!ok) continue;
                        for (BlockPos s2 : shell) if (c.level.getBlockState(s2).isAir()) c.level.setBlockAndUpdate(s2, Blocks.BLACK_CONCRETE.defaultBlockState());
                        BlockPos wp = c.base.offset(x, y, z);
                        c.level.setBlockAndUpdate(wp, Block.updateFromNeighbourShapes(Blocks.GLASS_PANE.defaultBlockState(), c.level, wp));
                        win.add(key(x, z));
                        c.count("window");
                    }
        }
    }

    private static boolean between(int v, int a, int b) { return v >= Math.min(a, b) && v <= Math.max(a, b); }

    private static boolean free(Plan p, Floor f, int x, int z) {
        long k = key(x, z);
        return p.floorCell(x, z) && !f.blocked.contains(k) && !f.used.contains(k);
    }

    private static List<int[]> floorCells(Plan p) {
        List<int[]> l = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Rect r : p.rects)
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++)
                    if (p.floorCell(x, z) && seen.add(key(x, z))) l.add(new int[]{x, z});
        return l;
    }

    /** 壁（扉も含む）に接していれば、その反対（部屋の内側）向き。 */
    private static Direction facingAway(Plan p, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (p.isWall(x + d.getStepX(), z + d.getStepZ())) {
                Direction o = d.getOpposite();
                if (p.floorCell(x + o.getStepX(), z + o.getStepZ())) return o;
            }
        }
        return null;
    }

    /** 壁際の1マス家具。face は部屋の内側向き。 */
    private static void wallItem(Ctx c, int x, int y, int z, Direction face) {
        Random rand = c.rand;
        BlockState pot = POTS[rand.nextInt(POTS.length)].defaultBlockState();
        switch (rand.nextInt(13)) {
            case 0, 1 -> { c.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState()); c.set(x, y + 1, z, Blocks.BOOKSHELF.defaultBlockState()); c.count("bookshelf"); }
            case 2 -> { c.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState()); c.set(x, y + 1, z, pot); c.count("bookshelf+pot"); }
            case 3 -> { c.set(x, y, z, Blocks.CRAFTING_TABLE.defaultBlockState()); c.count("crafting_table"); }
            case 4 -> { c.set(x, y, z, (rand.nextBoolean() ? Blocks.FURNACE : Blocks.SMOKER).defaultBlockState().setValue(AbstractFurnaceBlock.FACING, face)); c.count("furnace/smoker"); }
            case 5 -> { c.set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, face)); c.count("chest"); }
            case 6 -> {
                c.set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
                c.set(x, y + 1, z, rand.nextBoolean() ? Blocks.LANTERN.defaultBlockState() : pot);
                c.count("barrel");
            }
            case 7 -> { c.set(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3)); c.count("cauldron"); }
            case 8 -> { c.set(x, y, z, Blocks.COMPOSTER.defaultBlockState()); c.count("composter"); }
            case 9 -> { c.set(x, y, z, (rand.nextBoolean() ? Blocks.LOOM : Blocks.CARTOGRAPHY_TABLE).defaultBlockState()); c.count("workstation"); }
            case 10 -> { c.set(x, y, z, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, face)); c.count("lectern"); }
            case 11 -> { c.set(x, y, z, pot); c.count("flower_pot"); }
            default -> {
                c.set(x, y, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
                c.set(x, y + 1, z, rand.nextBoolean() ? pot : Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 1 + rand.nextInt(4)));
                c.count("side_table");
            }
        }
    }
}
