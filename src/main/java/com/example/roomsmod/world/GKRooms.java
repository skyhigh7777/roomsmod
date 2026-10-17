package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomGenerator.Rect;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.*;

/**
 * GK（学校 / Gakkou）区画の部屋。A（内寸約20x28〜）と V（内寸5〜9）の中間で A 寄りの、内寸 9〜15 の教室。
 * <ul>
 *   <li>CLASSROOM: 1部屋の教室（内寸 11〜15 x 11〜17）。机の列・黒板・教卓</li>
 *   <li>HALLWAY: 細長い廊下（内寸 5〜7 x 15〜21）。壁沿いにロッカーが多い</li>
 *   <li>PREP_ROOM: 教室（内寸 9〜12）＋横の準備室（内寸 5〜7）を内扉でつなぎ、準備室の奥壁に出口</li>
 * </ul>
 * どの部屋にも必ずロッカー1つと隠れテーブル1つを置く（置けなければ部屋の中に強制配置し WARN）。
 * 通路（幅3）は家具で塞がない。出口は常に部屋の最大Z面（次の部屋は +Z 側）。
 */
final class GKRooms {
    private GKRooms() {}

    static final int H = 5;          // 床〜天井
    private static final int EX = 10;

    enum Layout { CLASSROOM, HALLWAY, PREP_ROOM }

    static final class Plan {
        Layout layout;
        final List<Rect> rects = new ArrayList<>();
        int exitX, exitZ;
        final List<int[]> innerDoors = new ArrayList<>(); // {x, z, axisX}
        final List<int[]> path = new ArrayList<>();       // {x, z}
        boolean inside(int x, int z) { for (Rect r : rects) if (r.contains(x, z)) return true; return false; }
        boolean isWall(int x, int z) {
            for (Rect r : rects) if (r.contains(x, z) && (x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1())) return true;
            return false;
        }
        boolean floorCell(int x, int z) { return inside(x, z) && !isWall(x, z); }
        int maxZ() { return rects.stream().mapToInt(Rect::z1).max().orElse(0); }
    }

    private static int r(Random rand, int lo, int hi) { return lo + rand.nextInt(hi - lo + 1); }
    private static long key(int x, int z) { return RoomGenerator.key(x, z); }

    private static int interiorX0(Random rand, int w, BlockPos origin) {
        int lo = EX - w + 2, hi = EX - 1;
        if (origin.getX() > 40) return lo + rand.nextInt(Math.max(1, (hi - lo + 1) / 2));
        if (origin.getX() < -40) return hi - rand.nextInt(Math.max(1, (hi - lo + 1) / 2));
        return r(rand, lo, hi);
    }

    static Plan planSingle(Random rand, BlockPos origin, Layout l, int w, int d) {
        Plan p = new Plan();
        p.layout = l;
        int ix0 = interiorX0(rand, w, origin), ix1 = ix0 + w - 1;
        p.rects.add(new Rect(ix0 - 1, 0, ix1 + 1, d + 1));
        p.exitX = r(rand, ix0 + 1, ix1 - 1);
        p.exitZ = d + 1;
        int[][] pts = {{EX, 0}, {EX, 1}, {EX, d}, {p.exitX, d}, {p.exitX, d + 1}};
        for (int[] q : pts) p.path.add(q);
        return p;
    }

    static Plan planPrep(Random rand, BlockPos origin) {
        Plan p = new Plan();
        p.layout = Layout.PREP_ROOM;
        int w1 = r(rand, 9, 12), d1 = r(rand, 9, 12), w2 = r(rand, 5, 7), d2 = r(rand, 6, 9);
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
        for (int[] q : pts) p.path.add(q);
        return p;
    }

    static Plan choose(Random rand, BlockPos origin, List<List<BoundingBox>> rooms) {
        float f = rand.nextFloat();
        Plan p;
        if (f < 0.5f) p = planSingle(rand, origin, Layout.CLASSROOM, r(rand, 11, 15), r(rand, 11, 17));
        else if (f < 0.75f) p = planSingle(rand, origin, Layout.HALLWAY, r(rand, 5, 7), r(rand, 15, 21));
        else p = planPrep(rand, origin);
        if (isClear(origin, p, rooms)) return p;
        RoomsDebug.log("gk plan {} overlapped, fallback CLASSROOM 9x9", p.layout);
        return planSingle(new Random(0), BlockPos.ZERO, Layout.CLASSROOM, 9, 9);
    }

    private static List<BoundingBox> boxes(BlockPos origin, Plan p) {
        List<BoundingBox> l = new ArrayList<>();
        for (Rect r : p.rects)
            l.add(new BoundingBox(origin.getX() + r.x0(), origin.getY(), origin.getZ() + r.z0(), origin.getX() + r.x1(), origin.getY() + H, origin.getZ() + r.z1()));
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
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        Plan p = choose(rand, origin, state.generatedRooms);
        Map<String, Integer> decor = new TreeMap<>();
        buildShell(level, origin, p, theme);
        BlockPos exitDoor = origin.offset(p.exitX, 1, p.exitZ);
        door(level, exitDoor, Direction.NORTH, theme);
        RoomGenerator.sealExit(level, exitDoor);
        for (int[] d : p.innerDoors) door(level, origin.offset(d[0], 1, d[1]), d[2] == 1 ? Direction.EAST : Direction.WEST, theme);
        int local = Sections.localRoom(state, roomNumber);
        String hides = furnish(level, origin, p, rand, theme, decor);
        lights(level, origin, p, theme, local);
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = new ArrayList<>();
        for (int[] w : p.path) path.add(origin.offset(w[0], 1, w[1]));
        state.addRoom(boxes(origin, p), path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=GK origin={} gk layout={} boxes={} exitDoor={} hiding={} decor={}", roomNumber,
                    Sections.formatRoom(state, roomNumber), origin, p.layout, boxes(origin, p), exitDoor, hides, decor);
        return exitDoor;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }

    private static void door(ServerLevel level, BlockPos pos, Direction facing, SectionTheme theme) {
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, facing);
        level.setBlockAndUpdate(pos, lower);
        level.setBlockAndUpdate(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void buildShell(ServerLevel l, BlockPos o, Plan p, SectionTheme theme) {
        Set<Long> done = new HashSet<>();
        BlockState floor = theme.palette().floor().get(), wall = theme.palette().wall().get(), ceil = theme.palette().ceiling().get();
        BlockState skirting = Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState();
        for (Rect r : p.rects)
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++) {
                    if (!done.add(key(x, z))) continue;
                    set(l, o, x, 0, z, floor);
                    boolean w = p.isWall(x, z);
                    for (int y = 1; y < H; y++) set(l, o, x, y, z, w ? (y == 1 ? skirting : wall) : Blocks.AIR.defaultBlockState());
                    set(l, o, x, H, z, ceil);
                }
        set(l, o, EX, 1, 0, Blocks.AIR.defaultBlockState());
        set(l, o, EX, 2, 0, Blocks.AIR.defaultBlockState());
    }

    private static List<int[]> floorCells(Plan p) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Rect r : p.rects)
            for (int x = r.x0(); x <= r.x1(); x++)
                for (int z = r.z0(); z <= r.z1(); z++)
                    if (p.floorCell(x, z) && seen.add(key(x, z))) out.add(new int[]{x, z});
        return out;
    }

    private static Direction facingAway(Plan p, int x, int z) {
        for (Direction d : Direction.Plane.HORIZONTAL)
            if (p.isWall(x + d.getStepX(), z + d.getStepZ())) {
                Direction op = d.getOpposite();
                if (p.floorCell(x + op.getStepX(), z + op.getStepZ())) return op;
            }
        return null;
    }

    /** 家具。返り値は隠れ場所の内訳（デバッグ用）。 */
    private static String furnish(ServerLevel l, BlockPos o, Plan p, Random rand, SectionTheme theme, Map<String, Integer> decor) {
        Set<Long> blocked = new HashSet<>(), used = new HashSet<>();
        for (int i = 0; i + 1 < p.path.size(); i++) {
            int[] s = p.path.get(i), e = p.path.get(i + 1);
            int x = s[0], z = s[1];
            while (true) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) blocked.add(key(x + dx, z + dz));
                if (x == e[0] && z == e[1]) break;
                if (x != e[0]) x += Integer.signum(e[0] - x); else z += Integer.signum(e[1] - z);
            }
        }
        for (int[] d : p.innerDoors) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) blocked.add(key(d[0] + dx, d[1] + dz));
        // 出口の看板の真下は空ける（番号プレートが置けるように）
        blocked.add(key(p.exitX, p.exitZ - 1));

        List<Object[]> wallSide = new ArrayList<>();
        for (int[] c : floorCells(p)) {
            if (blocked.contains(key(c[0], c[1]))) continue;
            Direction f = facingAway(p, c[0], c[1]);
            if (f != null) wallSide.add(new Object[]{c, f});
        }
        Collections.shuffle(wallSide, rand);

        // 隠れ場所：必ずロッカー1・テーブル1、その後テーマの比率で追加（廊下はロッカー多め）
        List<SectionTheme.HidingType> want = new ArrayList<>(List.of(SectionTheme.HidingType.LOCKER, SectionTheme.HidingType.TABLE));
        int extra = theme.hidingMin() - 2 + rand.nextInt(theme.hidingExtra()) + (p.layout == Layout.HALLWAY ? 1 : 0);
        for (int i = 0; i < extra; i++)
            want.add(p.layout == Layout.HALLWAY ? SectionTheme.HidingType.LOCKER : RoomGenerator.pickHiding(rand.nextFloat()));
        int lockers = 0, tables = 0;
        for (SectionTheme.HidingType t : want) {
            boolean ok = false;
            for (Object[] w : wallSide) {
                int[] c = (int[]) w[0]; Direction face = (Direction) w[1];
                int fx = c[0] + face.getStepX(), fz = c[1] + face.getStepZ();
                if (used.contains(key(c[0], c[1])) || !p.floorCell(fx, fz) || used.contains(key(fx, fz))) continue;
                if (!RoomGenerator.reserve(used, c[0], c[1], 1)) continue;
                used.add(key(fx, fz));
                RoomGenerator.placeHiding(l, o.offset(c[0], 1, c[1]), face, t);
                RoomsDebug.log("hiding spot type={} at {}", t.name().toLowerCase(), o.offset(c[0], 1, c[1]));
                ok = true;
                break;
            }
            if (!ok && (t == SectionTheme.HidingType.LOCKER ? lockers : tables) == 0) ok = forcePlace(l, o, p, blocked, used, t);
            if (ok) { if (t == SectionTheme.HidingType.LOCKER) lockers++; else tables++; }
        }
        if (lockers == 0 || tables == 0) RoomsDebug.warn("GK room missing hiding spot lockers={} tables={} layout={} origin={}", lockers, tables, p.layout, o);

        // 黒板（入口から見て左右どちらかの側壁、y=2..3、壁の中なので通路に影響なし）
        Rect main = p.rects.get(0);
        if (p.layout != Layout.HALLWAY) {
            int bx = rand.nextBoolean() ? main.x0() : main.x1();
            int zc = (main.z0() + main.z1()) / 2;
            for (int z = zc - 2; z <= zc + 2; z++)
                if (z > main.z0() && z < main.z1() && p.isWall(bx, z) && !p.floorCell(bx, z) && !isDoorCell(p, bx, z))
                    for (int y = 2; y <= 3; y++) set(l, o, bx, y, z, Blocks.GREEN_TERRACOTTA.defaultBlockState());
            decor.merge("blackboard", 1, Integer::sum);
        }
        // 机の列（2マス間隔の格子、通路外）
        BlockState desk = Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        int desks = 0;
        for (int[] c : floorCells(p)) {
            if (p.layout == Layout.HALLWAY) break;
            int x = c[0], z = c[1];
            if ((x + z * 3) % 2 != 0 || z % 2 != 0 || blocked.contains(key(x, z)) || used.contains(key(x, z))) continue;
            if (facingAway(p, x, z) != null) continue; // 壁際は隠れ場所・小物用
            set(l, o, x, 1, z, desk);
            used.add(key(x, z));
            desks++;
            if (rand.nextInt(5) == 0) { set(l, o, x, 2, z, Blocks.FLOWER_POT.defaultBlockState()); }
        }
        decor.merge("desk", desks, Integer::sum);
        // 壁際の小物
        for (Object[] w : wallSide) {
            int[] c = (int[]) w[0]; Direction face = (Direction) w[1];
            if (used.contains(key(c[0], c[1])) || rand.nextFloat() >= 0.35f) continue;
            used.add(key(c[0], c[1]));
            switch (rand.nextInt(5)) {
                case 0 -> { set(l, o, c[0], 1, c[1], Blocks.BOOKSHELF.defaultBlockState()); decor.merge("bookshelf", 1, Integer::sum); }
                case 1 -> { set(l, o, c[0], 1, c[1], Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, face)); decor.merge("lectern", 1, Integer::sum); }
                case 2 -> { set(l, o, c[0], 1, c[1], Blocks.NOTE_BLOCK.defaultBlockState()); decor.merge("note_block", 1, Integer::sum); }
                case 3 -> { set(l, o, c[0], 1, c[1], Blocks.CAULDRON.defaultBlockState()); decor.merge("bucket", 1, Integer::sum); }
                default -> { set(l, o, c[0], 1, c[1], Blocks.BARREL.defaultBlockState()); decor.merge("barrel", 1, Integer::sum); }
            }
        }
        return "lockers=" + lockers + " tables=" + tables;
    }

    private static boolean isDoorCell(Plan p, int x, int z) {
        if (x == p.exitX && z == p.exitZ) return true;
        for (int[] d : p.innerDoors) if (d[0] == x && d[1] == z) return true;
        return false;
    }

    /** 壁際に置けなかったときの保険：通路外の床ならどこでも（ロッカーは向きを内側へ）。 */
    private static boolean forcePlace(ServerLevel l, BlockPos o, Plan p, Set<Long> blocked, Set<Long> used, SectionTheme.HidingType t) {
        for (int[] c : floorCells(p)) {
            long k = key(c[0], c[1]);
            if (blocked.contains(k) || used.contains(k)) continue;
            used.add(k);
            Direction f = facingAway(p, c[0], c[1]);
            RoomGenerator.placeHiding(l, o.offset(c[0], 1, c[1]), f == null ? Direction.NORTH : f, t);
            RoomsDebug.log("hiding spot type={} at {} (forced)", t.name().toLowerCase(), o.offset(c[0], 1, c[1]));
            return true;
        }
        return false;
    }

    private static void lights(ServerLevel l, BlockPos o, Plan p, SectionTheme theme, int localRoom) {
        RoomLighting.Fixture f = RoomLighting.fixture(RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        List<int[]> interiors = new ArrayList<>();
        for (Rect r : p.rects) interiors.add(new int[]{r.x0() + 1, r.z0() + 1, r.x1() - 1, r.z1() - 1});
        int placed = 0;
        for (int[] c : RoomLighting.candidates(interiors, f)) {
            if (placed >= f.maxCount()) break;
            if (!p.floorCell(c[0], c[1])) continue;
            BlockPos pos = o.offset(c[0], f.hanging() ? H - 1 : 1, c[1]);
            if (!l.getBlockState(pos).isAir()) continue;
            if (f.hanging() ? l.getBlockState(pos.above()).isAir() : l.getBlockState(pos.below()).isAir()) continue;
            if (!f.hanging() && RoomLighting.nearPath(p.path, c[0], c[1])) continue;
            if (c[0] == p.exitX && c[1] == p.exitZ - 1) continue;
            l.setBlockAndUpdate(pos, f.state().get());
            placed++;
        }
    }
}
