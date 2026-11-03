package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * H（Hospital、病棟）区画の部屋：まっすぐな病室。白いタイルの床・壁、両側の壁際にベッドの列、ベッドの間はカーテンの仕切り。
 * <ul>
 *   <li>内寸 幅13（x=4..16、壁 x=3 / x=17）× 高さ4（床〜天井 5）。入口は x=10 の z=0、出口は x=10 の最大Z面（真ん中、曲がらない）。</li>
 *   <li>ベッドの区画（BAY=4 ブロック）：仕切りのカーテン（z=zb、壁から3マス）、枕元の棚（zb+1）、ベッド（zb+2、頭が壁側）、点滴台（zb+3）。
 *       通路側にもカーテンが引いてある区画がある。カーテンは所々破れて（穴・蜘蛛の巣）、ベッドが外された区画もある。</li>
 *   <li>区画数：SHORT 4 / STD 6 / LONG 8 / VLONG 10（VLONG は H-250 以降）。見た目：WARD / CURTAINED（通路側のカーテン多め）/ DIM（照明半分・荒れ多め）。</li>
 *   <li>通路（x=8..12、真ん中 x=9..11 は常に空く）の天井に照明（エンドロッド、RoomLighting の減光どおり）。</li>
 *   <li>隠れ場所：ベッドの横の壁のロッカー（壁を窪ませる）か、ベッドの代わりのテーブル。どこからも MAX_HIDE_GAP 以内、最低 MIN_HIDES 個、ロッカー1つ以上。</li>
 *   <li>前の部屋と重なる時は、重なる側のベッドの列をなくした細い部屋（壁 x=7 / x=13）にする（それでも重なれば両側とも細く）。</li>
 * </ul>
 */
final class HospitalRooms {
    private HospitalRooms() {}

    static final int H = 5;            // 床〜天井
    static final int EX = 10;          // 入口・出口の x
    static final int WIDE = 7;         // ベッドの列がある側：壁までの距離（壁 x=3 / x=17）
    static final int NARROW = 3;       // ベッドの列がない側（壁 x=7 / x=13）
    static final int BAY = 4;
    static final int FIRST_BAY_Z = 2;
    static final int MAX_HIDE_GAP = 16;
    static final int MIN_HIDES = 2;
    static final int VLONG_FROM = 250;

    enum Length { SHORT(4), STD(6), LONG(8), VLONG(10); final int bays; Length(int b) { bays = b; } }
    enum Variant { WARD, CURTAINED, DIM }

    private static final int[] LENGTH_WEIGHTS = {25, 45, 25, 5};
    private static final int[] VARIANT_WEIGHTS = {50, 25, 25};

    /** 片側（left = -X 側）。 */
    private record Side(boolean left, boolean beds) {
        int wall() { return left ? EX - (beds ? WIDE : NARROW) : EX + (beds ? WIDE : NARROW); }
        /** 壁から k マス内側の x。 */
        int in(int k) { return left ? wall() + k : wall() - k; }
        /** 部屋の内側を向く方向。 */
        Direction inward() { return left ? Direction.EAST : Direction.WEST; }
    }

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        int local = Sections.localRoom(state, roomNumber);
        Length len = Length.values()[SectionTheme.pickWeighted(rand, LENGTH_WEIGHTS)];
        if (len == Length.VLONG && local < VLONG_FROM) len = Length.LONG;
        Variant variant = Variant.values()[SectionTheme.pickWeighted(rand, VARIANT_WEIGHTS)];
        int n = len.bays;
        int d = FIRST_BAY_Z + BAY * n + 2; // 内寸の奥行き（z=1..d）、出口の壁 z=d+1

        // 前の部屋と重ならない幅を選ぶ（両側ベッド → 片側 → 両側なし）
        boolean[][] tries = {{true, true}, {true, false}, {false, true}, {false, false}};
        Side left = null, right = null;
        String fit = "";
        for (boolean[] t : tries) {
            Side l = new Side(true, t[0]), r = new Side(false, t[1]);
            if (isClear(origin, l.wall(), r.wall(), d, state.generatedRooms)) { left = l; right = r; fit = (t[0] ? "L" : "-") + (t[1] ? "R" : "-"); break; }
        }
        if (left == null) { left = new Side(true, false); right = new Side(false, false); fit = "forced"; RoomsDebug.warn("H room {} overlaps an older room at every width, using the narrow shape", roomNumber); }

        buildShell(level, origin, left.wall(), right.wall(), d, theme, rand);
        BlockPos exitDoor = origin.offset(EX, 1, d + 1);
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(exitDoor, lower);
        level.setBlockAndUpdate(exitDoor.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        RoomGenerator.sealExit(level, exitDoor);

        // 隠れ場所を先に決める（その区画にはベッドの代わりにテーブル、またはベッド横の壁にロッカー）
        List<int[]> hides = planHiding(rand, theme, left, right, n, d); // {side(0=L,1=R), z, type(0=locker,1=table), bay(-1=区画外)}
        int beds = 0, curtains = 0;
        for (Side s : new Side[]{left, right}) {
            if (!s.beds()) continue;
            for (int i = 0; i < n; i++) {
                int zb = FIRST_BAY_Z + BAY * i;
                boolean locker = false, table = false;
                for (int[] h : hides) if (h[0] == (s.left() ? 0 : 1) && h[3] == i) { if (h[2] == 0) locker = true; else table = true; }
                beds += buildBay(level, origin, s, zb, variant, rand, locker, table);
            }
            curtains += partitions(level, origin, s, n, variant, rand);
        }
        String hiding = placeHiding(level, origin, left, right, hides);
        lights(level, origin, d, theme, local, variant);
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = List.of(origin.offset(EX, 1, 0), origin.offset(EX, 1, 1), origin.offset(EX, 1, d), exitDoor);
        List<BoundingBox> boxes = List.of(box(origin, left.wall(), right.wall(), d));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=H origin={} ward length={} bays={} depth={} variant={} width={} boxes={} exitDoor={} beds={} curtains={} {}",
                    roomNumber, Sections.formatRoom(state, roomNumber), origin, len, n, d, variant, fit, boxes, exitDoor, beds, curtains, hiding);
        return exitDoor;
    }

    private static BoundingBox box(BlockPos o, int x0, int x1, int d) {
        return new BoundingBox(o.getX() + x0, o.getY(), o.getZ(), o.getX() + x1, o.getY() + H, o.getZ() + d + 1);
    }

    /** RoomGenerator.isClear と同じ判定（直前の部屋とは接してよい、それ以外とは1マス空ける）。 */
    private static boolean isClear(BlockPos o, int x0, int x1, int d, List<List<BoundingBox>> rooms) {
        BoundingBox mine = box(o, x0, x1, d);
        for (int i = 0; i < rooms.size(); i++) {
            BoundingBox test = i == rooms.size() - 1 ? mine : mine.inflatedBy(1);
            for (BoundingBox other : rooms.get(i)) if (test.intersects(other)) return false;
        }
        return true;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }

    private static boolean air(ServerLevel l, BlockPos o, int x, int y, int z) { return l.getBlockState(o.offset(x, y, z)).isAir(); }

    /** 床：白いタイル（白コンクリと滑らかなクォーツの市松、所々くすんだタイル）。壁：腰までタイル（クォーツレンガ）、青灰の線、上は白。 */
    private static void buildShell(ServerLevel l, BlockPos o, int x0, int x1, int d, SectionTheme theme, Random rand) {
        BlockState tileA = theme.palette().floor().get(), tileB = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState dull = Blocks.CALCITE.defaultBlockState(), grime = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
        BlockState wallTile = theme.palette().wall().get(), stripe = Blocks.LIGHT_BLUE_TERRACOTTA.defaultBlockState();
        BlockState upper = Blocks.WHITE_CONCRETE.defaultBlockState(), ceil = theme.palette().ceiling().get();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++)
            for (int z = 0; z <= d + 1; z++) {
                boolean w = x == x0 || x == x1 || z == 0 || z == d + 1;
                float f = rand.nextFloat();
                set(l, o, x, 0, z, f < 0.05f ? dull : f < 0.07f ? grime : ((x + z) & 1) == 0 ? tileA : tileB);
                for (int y = 1; y < H; y++) {
                    if (!w) { set(l, o, x, y, z, air); continue; }
                    BlockState s = y <= 2 ? wallTile : y == 3 ? stripe : upper;
                    if (y != 3 && rand.nextFloat() < 0.05f) s = dull; // 剥がれ・汚れ
                    set(l, o, x, y, z, s);
                }
                set(l, o, x, H, z, ceil);
            }
        set(l, o, EX, 1, 0, air);
        set(l, o, EX, 2, 0, air);
    }

    /** カーテンの1枚（汚れた白・灰色、破れて穴が空いていれば空気、上の段の穴は時々蜘蛛の巣）。置いたら 1。 */
    private static int curtain(ServerLevel l, BlockPos o, int x, int y, int z, float torn, Random rand) {
        if (rand.nextFloat() < torn) {
            if (y == 3 && rand.nextFloat() < 0.35f) set(l, o, x, y, z, Blocks.COBWEB.defaultBlockState());
            return 0;
        }
        set(l, o, x, y, z, (rand.nextFloat() < 0.25f ? Blocks.LIGHT_GRAY_WOOL : Blocks.WHITE_WOOL).defaultBlockState());
        return 1;
    }

    private static float torn(Variant v) { return v == Variant.DIM ? 0.30f : 0.15f; }

    /** 区画の間の仕切り（z = 各区画の頭と最後の区画の後ろ、壁から3マス、高さ3）と天井のカーテンレール（鎖）。 */
    private static int partitions(ServerLevel l, BlockPos o, Side s, int n, Variant v, Random rand) {
        int placed = 0;
        BlockState rail = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.X);
        for (int i = 0; i <= n; i++) {
            int z = FIRST_BAY_Z + BAY * i;
            for (int k = 1; k <= 3; k++) {
                for (int y = 1; y <= 3; y++) placed += curtain(l, o, s.in(k), y, z, torn(v), rand);
                set(l, o, s.in(k), 4, z, rail);
            }
        }
        return placed;
    }

    /**
     * ベッドの区画1つ（z = zb+1..zb+3、壁から1〜4マス）。table ならベッドの代わりにテーブル（placeHiding で置く）、
     * locker なら点滴台を置かない（壁のロッカーの前を空ける）。通路側のカーテンは隠れ場所の区画には引かない。置いたベッドの数を返す。
     */
    private static int buildBay(ServerLevel l, BlockPos o, Side s, int zb, Variant v, Random rand, boolean locker, boolean table) {
        boolean hide = locker || table;
        int bed = 0;
        // 枕元の棚（時々火の消えたろうそく）
        set(l, o, s.in(1), 1, zb + 1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        if (rand.nextFloat() < 0.3f)
            set(l, o, s.in(1), 2, zb + 1, Blocks.WHITE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + rand.nextInt(2)).setValue(CandleBlock.LIT, false));
        // ベッド（頭が壁側）。外された区画は床に蜘蛛の巣
        boolean removed = rand.nextFloat() < (v == Variant.DIM ? 0.25f : 0.12f);
        if (!table) {
            if (removed) {
                set(l, o, s.in(1), 1, zb + 2, Blocks.COBWEB.defaultBlockState());
            } else {
                Block b = rand.nextFloat() < 0.25f ? Blocks.LIGHT_GRAY_BED : Blocks.WHITE_BED;
                Direction head = s.left() ? Direction.WEST : Direction.EAST; // 足→頭の向き
                BlockState foot = b.defaultBlockState().setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.FOOT);
                l.setBlock(o.offset(s.in(2), 1, zb + 2), foot, 2);
                l.setBlock(o.offset(s.in(1), 1, zb + 2), foot.setValue(BedBlock.PART, BedPart.HEAD), 2);
                bed = 1;
            }
        }
        // 点滴台（鉄格子2段＋鎖）
        if (!locker && !table && rand.nextFloat() < 0.4f) {
            set(l, o, s.in(2), 1, zb + 3, Blocks.IRON_BARS.defaultBlockState());
            set(l, o, s.in(2), 2, zb + 3, Blocks.IRON_BARS.defaultBlockState());
            set(l, o, s.in(2), 3, zb + 3, Blocks.CHAIN.defaultBlockState());
        }
        // 通路側のカーテン（引いてある区画は手前2マス、奥の1マスは開いている）
        float drawn = v == Variant.CURTAINED ? 0.7f : 0.3f;
        if (!hide && rand.nextFloat() < drawn) {
            BlockState rail = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Z);
            for (int z = zb + 1; z <= zb + 2; z++)
                for (int y = 1; y <= 3; y++) curtain(l, o, s.in(4), y, z, torn(v), rand);
            for (int z = zb; z <= zb + BAY; z++) set(l, o, s.in(4), 4, z, rail);
        }
        // 天井の隅の蜘蛛の巣
        if (rand.nextFloat() < (v == Variant.DIM ? 0.4f : 0.15f) && air(l, o, s.in(1), 4, zb + 3)) set(l, o, s.in(1), 4, zb + 3, Blocks.COBWEB.defaultBlockState());
        return bed;
    }

    private static SectionTheme.HidingType pick(SectionTheme theme, float f) {
        float total = 0f;
        for (var w : theme.hidingMix()) total += w.weight();
        float r = f * total, cum = 0f;
        for (var w : theme.hidingMix()) { cum += w.weight(); if (r < cum) return w.value(); }
        return theme.hidingMix().get(theme.hidingMix().size() - 1).value();
    }

    /**
     * 隠れ場所の位置：区画 2〜3 つ（8〜12 ブロック）ごとに左右交互。最後の隠れ場所から出口まで MAX_HIDE_GAP を超えるなら1つ足す。
     * ベッドの列がある側は区画の中（ロッカー＝ベッドの足側の壁 zb+3、テーブル＝ベッドの位置 zb+2）、ない側は壁の窪みのロッカー／壁際のテーブル。
     */
    private static List<int[]> planHiding(Random rand, SectionTheme theme, Side left, Side right, int n, int d) {
        List<int[]> out = new ArrayList<>();
        boolean onLeft = rand.nextBoolean();
        int i = rand.nextInt(2);
        while (i < n) {
            out.add(slot(onLeft ? left : right, i, rand, theme));
            onLeft = !onLeft;
            i += 2 + rand.nextInt(2);
        }
        if (out.isEmpty() || d - out.get(out.size() - 1)[1] > MAX_HIDE_GAP) {
            Side s = onLeft ? left : right;
            for (int[] h : out) if (h[0] == (s.left() ? 0 : 1) && h[1] / BAY == (FIRST_BAY_Z + BAY * (n - 1) + 2) / BAY) s = s.left() ? right : left; // 同じ区画は反対側
            out.add(slot(s, n - 1, rand, theme));
        }
        while (out.size() < MIN_HIDES) {
            int[] last = out.get(out.size() - 1);
            int bay = last[3] >= 0 ? last[3] : n - 1;
            out.add(slot(last[0] == 0 ? right : left, bay, rand, theme)); // 反対側の同じ区画
        }
        boolean anyLocker = false;
        for (int[] h : out) if (h[2] == 0) anyLocker = true;
        if (!anyLocker) { // 1部屋に最低1つはロッカー（テーブルの位置 zb+2 からベッドの足側 zb+3 へ）
            int[] h = out.get(out.size() - 1);
            h[2] = 0;
            h[1] = (h[1] - FIRST_BAY_Z) % BAY == 2 ? h[1] + 1 : h[1];
        }
        return out;
    }

    private static int[] slot(Side s, int bay, Random rand, SectionTheme theme) {
        int type = pick(theme, rand.nextFloat()) == SectionTheme.HidingType.LOCKER ? 0 : 1;
        int zb = FIRST_BAY_Z + BAY * bay;
        int z = type == 0 ? zb + 3 : zb + 2;
        return new int[]{s.left() ? 0 : 1, z, type, s.beds() ? bay : -1};
    }

    private static String placeHiding(ServerLevel l, BlockPos o, Side left, Side right, List<int[]> hides) {
        int lockers = 0, tables = 0;
        List<Integer> zs = new ArrayList<>();
        for (int[] h : hides) {
            Side s = h[0] == 0 ? left : right;
            int z = h[1];
            if (h[2] == 0) {
                // 壁を窪ませたロッカー（正面は部屋の内側）
                RoomGenerator.placeHidingSpot(l, o.offset(s.wall(), 1, z), s.inward());
                lockers++;
                RoomsDebug.log("hiding spot type=locker at {} (wall niche)", o.offset(s.wall(), 1, z));
            } else {
                // ベッドの列がある側はベッドの位置（壁から2マス目）、ない側は壁際
                int x = s.beds() ? s.in(2) : s.in(1);
                RoomGenerator.placeHiding(l, o.offset(x, 1, z), s.inward(), SectionTheme.HidingType.TABLE);
                tables++;
                RoomsDebug.log("hiding spot type=table at {}", o.offset(x, 1, z));
            }
            zs.add(z);
        }
        return "lockers=" + lockers + " tables=" + tables + " z=" + zs;
    }

    /** 通路（x=8..12）の天井に照明（RoomLighting の段階どおり、DIM は半分）。 */
    private static void lights(ServerLevel l, BlockPos o, int d, SectionTheme theme, int localRoom, Variant v) {
        RoomLighting.Fixture f = RoomLighting.fixture(RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        int max = v == Variant.DIM ? Math.max(1, f.maxCount() / 2) : f.maxCount();
        List<int[]> interiors = List.of(new int[]{EX - 2, 1, EX + 2, d});
        List<int[]> path = List.of(new int[]{EX, 0}, new int[]{EX, 1}, new int[]{EX, d}, new int[]{EX, d + 1});
        int placed = 0;
        for (int[] c : RoomLighting.candidates(interiors, f)) {
            if (placed >= max) break;
            BlockPos pos = o.offset(c[0], f.hanging() ? H - 1 : 1, c[1]);
            if (!l.getBlockState(pos).isAir()) continue;
            if (f.hanging() ? l.getBlockState(pos.above()).isAir() : l.getBlockState(pos.below()).isAir()) continue;
            if (!f.hanging() && RoomLighting.nearPath(path, c[0], c[1])) continue;
            if (c[0] == EX && c[1] == d) continue; // 番号プレート
            l.setBlockAndUpdate(pos, f.state().get());
            placed++;
        }
    }
}
