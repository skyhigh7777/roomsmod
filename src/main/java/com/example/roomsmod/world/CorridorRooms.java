package com.example.roomsmod.world;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomGenerator.Rect;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * U 区画の部屋：まっすぐな廊下だけ。内寸 幅5 × 高さ4（床〜天井 5）、外壁込みの幅 7。
 * 入口は x=10 の z=0、出口は x=10 の最大Z面（常に真ん中、曲がらない）。
 * <ul>
 *   <li>奥行き（内寸）：SHORT 24 / STD 36 / LONG 52 / VLONG 72（VLONG は U-250 以降）</li>
 *   <li>見た目：PLAIN / PILLARED（両側に柱、陰に入れる）/ ALCOVE（壁の窪みのロッカーが多い）/ DIM（照明が少ない）</li>
 * </ul>
 * 隠れ場所は壁を窪ませたロッカー（壁の位置に置く）と、壁際のテーブル。通路の真ん中 3 マス（x=9..11）は常に空ける。
 * どの地点からも隠れ場所まで MAX_HIDE_GAP ブロック以内、1部屋に最低 MIN_HIDES 個。
 * 部屋はすべて +Z に一直線に並ぶので、既存の部屋と重なることはない。
 */
final class CorridorRooms {
    private CorridorRooms() {}

    static final int H = 5;            // 床〜天井
    static final int EX = 10;          // 入口・出口の x
    static final int HALF = 2;         // 内寸の半幅（幅 5）
    /** 隠れ場所どうし（と部屋の両端）の間隔の上限。 */
    static final int MAX_HIDE_GAP = 16;
    static final int MIN_HIDES = 2;
    /** VLONG が出始めるセクション内番号。 */
    static final int VLONG_FROM = 250;

    enum Length { SHORT(24), STD(36), LONG(52), VLONG(72); final int depth; Length(int d) { depth = d; } }
    enum Variant { PLAIN, PILLARED, ALCOVE, DIM }

    private static final int[] LENGTH_WEIGHTS = {25, 45, 25, 5};
    private static final int[] VARIANT_WEIGHTS = {35, 25, 25, 15};

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        int local = Sections.localRoom(state, roomNumber);
        Length len = Length.values()[SectionTheme.pickWeighted(rand, LENGTH_WEIGHTS)];
        if (len == Length.VLONG && local < VLONG_FROM) len = Length.LONG;
        Variant variant = Variant.values()[SectionTheme.pickWeighted(rand, VARIANT_WEIGHTS)];
        int d = len.depth;
        Rect rect = new Rect(EX - HALF - 1, 0, EX + HALF + 1, d + 1);

        buildShell(level, origin, rect, theme);
        BlockPos exitDoor = origin.offset(EX, 1, d + 1);
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(exitDoor, lower);
        level.setBlockAndUpdate(exitDoor.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        RoomGenerator.sealExit(level, exitDoor);

        List<Integer> hideZ = new ArrayList<>();
        String hides = placeHiding(level, origin, d, variant, rand, theme, hideZ);
        int pillars = variant == Variant.PILLARED ? placePillars(level, origin, d, hideZ) : 0;
        lights(level, origin, d, theme, local, variant);
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = List.of(origin.offset(EX, 1, 0), origin.offset(EX, 1, 1), origin.offset(EX, 1, d), exitDoor);
        List<BoundingBox> boxes = List.of(new BoundingBox(origin.getX() + rect.x0(), origin.getY(), origin.getZ() + rect.z0(),
                origin.getX() + rect.x1(), origin.getY() + H, origin.getZ() + rect.z1()));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=U origin={} corridor length={} depth={} variant={} boxes={} exitDoor={} hiding={} pillars={}",
                    roomNumber, Sections.formatRoom(state, roomNumber), origin, len, d, variant, boxes, exitDoor, hides, pillars);
        return exitDoor;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }

    private static void buildShell(ServerLevel l, BlockPos o, Rect r, SectionTheme theme) {
        BlockState floor = theme.palette().floor().get(), wall = theme.palette().wall().get(), ceil = theme.palette().ceiling().get();
        BlockState base = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState stripe = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = r.x0(); x <= r.x1(); x++)
            for (int z = r.z0(); z <= r.z1(); z++) {
                boolean w = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
                // 床：真ん中の通路（x=9..11）に細い線
                set(l, o, x, 0, z, !w && Math.abs(x - EX) <= 1 && z % 4 == 0 ? stripe : floor);
                for (int y = 1; y < H; y++) set(l, o, x, y, z, w ? (y == 1 ? base : wall) : air);
                set(l, o, x, H, z, ceil);
            }
        set(l, o, EX, 1, 0, air);
        set(l, o, EX, 2, 0, air);
    }

    /**
     * 隠れ場所。z は 3 から始め、ALCOVE は 8、他は 12〜14 ごとに左右交互。最後の隠れ場所から出口までが
     * MAX_HIDE_GAP を超えるなら1つ足す。ロッカーは壁の位置（窪み）、テーブルは壁際の床（通路 3 マスは空く）。
     */
    private static String placeHiding(ServerLevel l, BlockPos o, int d, Variant v, Random rand, SectionTheme theme, List<Integer> hideZ) {
        List<Integer> zs = new ArrayList<>();
        int z = 3 + rand.nextInt(4);
        int step = v == Variant.ALCOVE ? 8 : 12 + rand.nextInt(3);
        while (z <= d - 2) { zs.add(z); z += step; }
        if (zs.isEmpty() || d - zs.get(zs.size() - 1) > MAX_HIDE_GAP) zs.add(Math.max(3, d - 3));
        while (zs.size() < MIN_HIDES) zs.add(Math.max(3, Math.min(d - 2, zs.get(zs.size() - 1) + 6)));
        boolean left = rand.nextBoolean();
        int lockers = 0, tables = 0;
        for (int hz : zs) {
            if (hideZ.contains(hz)) hz++;
            SectionTheme.HidingType t = pick(theme, rand.nextFloat());
            // 1部屋に最低1つはロッカー
            if (t == SectionTheme.HidingType.TABLE && lockers == 0 && hz == zs.get(zs.size() - 1)) t = SectionTheme.HidingType.LOCKER;
            if (t == SectionTheme.HidingType.LOCKER) {
                int wx = left ? EX - HALF - 1 : EX + HALF + 1;
                RoomGenerator.placeHidingSpot(l, o.offset(wx, 1, hz), left ? Direction.EAST : Direction.WEST);
                lockers++;
                RoomsDebug.log("hiding spot type=locker at {} (wall niche)", o.offset(wx, 1, hz));
            } else {
                int tx = left ? EX - HALF : EX + HALF;
                RoomGenerator.placeHiding(l, o.offset(tx, 1, hz), left ? Direction.EAST : Direction.WEST, SectionTheme.HidingType.TABLE);
                tables++;
                RoomsDebug.log("hiding spot type=table at {}", o.offset(tx, 1, hz));
            }
            hideZ.add(hz);
            left = !left;
        }
        return "lockers=" + lockers + " tables=" + tables + " z=" + hideZ;
    }

    private static SectionTheme.HidingType pick(SectionTheme theme, float f) {
        float total = 0f;
        for (var w : theme.hidingMix()) total += w.weight();
        float r = f * total, cum = 0f;
        for (var w : theme.hidingMix()) { cum += w.weight(); if (r < cum) return w.value(); }
        return theme.hidingMix().get(theme.hidingMix().size() - 1).value();
    }

    /** 両側の壁際（x=8, x=12）に 8 ブロックごとの柱。隠れ場所の前後 1 マスは避ける。 */
    private static int placePillars(ServerLevel l, BlockPos o, int d, List<Integer> hideZ) {
        BlockState p = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        int n = 0;
        for (int z = 6; z <= d - 3; z += 8) {
            boolean near = false;
            for (int hz : hideZ) if (Math.abs(hz - z) <= 1) near = true;
            if (near) continue;
            for (int x : new int[]{EX - HALF, EX + HALF})
                for (int y = 1; y < H; y++) set(l, o, x, y, z, p);
            n++;
        }
        return n;
    }

    private static void lights(ServerLevel l, BlockPos o, int d, SectionTheme theme, int localRoom, Variant v) {
        RoomLighting.Fixture f = RoomLighting.fixture(RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        int max = v == Variant.DIM ? Math.max(1, f.maxCount() / 2) : f.maxCount();
        List<int[]> interiors = List.of(new int[]{EX - HALF, 1, EX + HALF, d});
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
