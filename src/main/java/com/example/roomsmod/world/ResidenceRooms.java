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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * R（Residence、住宅）区画の部屋：家の1区画ずつ（玄関・廊下 / リビング / キッチン / 寝室 / 和室）。まっすぐな部屋（H と同じ骨組み）。
 * <ul>
 *   <li>内寸 幅13（x=4..16、壁 x=3 / x=17）× 高さ4（床〜天井 5）。入口は x=10 の z=0、出口は x=10 の最大Z面。真ん中の通路 x=8..12 には家具を置かない。</li>
 *   <li>家具は両側の壁際（壁から1〜3マス）。廊下は細い（壁 x=7 / x=13）。前の部屋と重なる時は重なる側を細くする（H と同じ）。</li>
 *   <li>隠れ場所（H と同じ規則）：壁の窪みのロッカー（クローゼット・冷蔵庫・押し入れ）か、壁際のテーブル（ベッドの下・ちゃぶ台）。
 *       どこからも MAX_HIDE_GAP 以内、最低 MIN_HIDES 個、ロッカー1つ以上。</li>
 *   <li>壁際に開かない扉（クローゼット・勝手口。後ろは壁）を 1〜2 枚。R-5（インターホン）はこの扉を少し開ける。</li>
 * </ul>
 */
final class ResidenceRooms {
    private ResidenceRooms() {}

    static final int H = 5;
    static final int EX = 10;
    static final int WIDE = 7, NARROW = 3;
    static final int MAX_HIDE_GAP = 14;
    static final int MIN_HIDES = 2;

    enum Kind { HALLWAY(14, 8), LIVING(14, 5), KITCHEN(12, 5), BEDROOM(12, 5), WASHITSU(12, 3);
        final int base, extra; Kind(int b, int e) { base = b; extra = e; } }
    private static final int[] KIND_WEIGHTS = {20, 25, 20, 22, 13};

    private record Side(boolean left, boolean wide) {
        int wall() { return left ? EX - (wide ? WIDE : NARROW) : EX + (wide ? WIDE : NARROW); }
        int in(int k) { return left ? wall() + k : wall() - k; }
        Direction inward() { return left ? Direction.EAST : Direction.WEST; }
        Direction outward() { return inward().getOpposite(); }
    }

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        int local = Sections.localRoom(state, roomNumber);
        Kind kind = Kind.values()[SectionTheme.pickWeighted(rand, KIND_WEIGHTS)];
        int d = kind.base + rand.nextInt(kind.extra + 1);

        boolean[][] tries = kind == Kind.HALLWAY ? new boolean[][]{{false, false}} : new boolean[][]{{true, true}, {true, false}, {false, true}, {false, false}};
        Side left = null, right = null;
        String fit = "";
        for (boolean[] t : tries) {
            Side l = new Side(true, t[0]), r = new Side(false, t[1]);
            if (isClear(origin, l.wall(), r.wall(), d, state.generatedRooms)) { left = l; right = r; fit = (t[0] ? "L" : "-") + (t[1] ? "R" : "-"); break; }
        }
        if (left == null) { left = new Side(true, false); right = new Side(false, false); fit = "forced"; RoomsDebug.warn("R room {} overlaps an older room, using the narrow shape", roomNumber); }

        buildShell(level, origin, left, right, d, kind, theme, rand);
        BlockPos exitDoor = origin.offset(EX, 1, d + 1);
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(exitDoor, lower);
        level.setBlockAndUpdate(exitDoor.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        RoomGenerator.sealExit(level, exitDoor);

        // 使用中の z（側ごと）：隠れ場所とその前後、扉の前
        boolean[][] used = new boolean[2][d + 2];
        used[0][0] = used[1][0] = used[0][1] = used[1][1] = true;
        used[0][d] = used[1][d] = used[0][d + 1] = used[1][d + 1] = true;
        List<int[]> hides = planHiding(rand, theme, d); // {side, z, type(0=locker,1=table)}
        for (int[] h : hides) for (int z = h[1] - 1; z <= h[1] + 1; z++) if (z >= 0 && z <= d + 1) used[h[0]][z] = true;
        String hiding = placeHiding(level, origin, left, right, hides);

        // 開かない扉（クローゼット・勝手口）
        int closets = 0;
        int want = 1 + (rand.nextFloat() < 0.4f ? 1 : 0);
        for (int tryN = 0; tryN < 12 && closets < want; tryN++) {
            int si = rand.nextInt(2);
            Side s = si == 0 ? left : right;
            int z = 3 + rand.nextInt(Math.max(1, d - 5));
            if (used[si][z] || used[si][z - 1] || used[si][z + 1]) continue;
            closetDoor(level, origin, s, z, kind);
            used[si][z - 1] = used[si][z] = used[si][z + 1] = true;
            closets++;
        }

        int furn = 0;
        for (int si = 0; si < 2; si++) {
            Side s = si == 0 ? left : right;
            if (!s.wide()) { furn += narrowDecor(level, origin, s, d, used[si], rand, kind); continue; }
            furn += switch (kind) {
                case LIVING -> living(level, origin, s, d, used[si], rand, si == 0);
                case KITCHEN -> kitchen(level, origin, s, d, used[si], rand, si == 0);
                case BEDROOM -> bedroom(level, origin, s, d, used[si], rand, si == 0);
                case WASHITSU -> washitsu(level, origin, s, d, used[si], rand);
                case HALLWAY -> narrowDecor(level, origin, s, d, used[si], rand, kind);
            };
        }
        if (kind == Kind.HALLWAY) for (int x = EX - 1; x <= EX + 1; x++) set(level, origin, x, 1, 1, Blocks.BROWN_CARPET.defaultBlockState()); // 玄関マット
        if (kind == Kind.LIVING || kind == Kind.WASHITSU) rug(level, origin, d, kind, rand);

        lights(level, origin, d, theme, local);
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = List.of(origin.offset(EX, 1, 0), origin.offset(EX, 1, 1), origin.offset(EX, 1, d), exitDoor);
        List<BoundingBox> boxes = List.of(box(origin, left.wall(), right.wall(), d));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=R origin={} kind={} depth={} width={} boxes={} exitDoor={} furniture={} closets={} {}",
                    roomNumber, Sections.formatRoom(state, roomNumber), origin, kind, d, fit, boxes, exitDoor, furn, closets, hiding);
        return exitDoor;
    }

    private static BoundingBox box(BlockPos o, int x0, int x1, int d) {
        return new BoundingBox(o.getX() + x0, o.getY(), o.getZ(), o.getX() + x1, o.getY() + H, o.getZ() + d + 1);
    }

    private static boolean isClear(BlockPos o, int x0, int x1, int d, List<List<BoundingBox>> rooms) {
        BoundingBox mine = box(o, x0, x1, d);
        for (int i = 0; i < rooms.size(); i++) {
            BoundingBox test = i == rooms.size() - 1 ? mine : mine.inflatedBy(1);
            for (BoundingBox other : rooms.get(i)) if (test.intersects(other)) return false;
        }
        return true;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }
    private static BlockState b(Block b) { return b.defaultBlockState(); }

    /** 床・壁・天井。壁は腰板（木）＋白い漆喰（和室は砂岩＋白、柱は白樺）。 */
    private static void buildShell(ServerLevel l, BlockPos o, Side left, Side right, int d, Kind kind, SectionTheme theme, Random rand) {
        int x0 = left.wall(), x1 = right.wall();
        BlockState floorA, floorB, wainscot, upper, ceil = theme.palette().ceiling().get(), air = b(Blocks.AIR);
        switch (kind) {
            case KITCHEN -> { floorA = b(Blocks.WHITE_TERRACOTTA); floorB = b(Blocks.LIGHT_GRAY_TERRACOTTA); wainscot = b(Blocks.SMOOTH_STONE); upper = theme.palette().wall().get(); }
            case WASHITSU -> { floorA = b(Blocks.BAMBOO_MOSAIC); floorB = b(Blocks.BAMBOO_PLANKS); wainscot = b(Blocks.SMOOTH_SANDSTONE); upper = b(Blocks.WHITE_CONCRETE); }
            case HALLWAY -> { floorA = b(Blocks.DARK_OAK_PLANKS); floorB = b(Blocks.SPRUCE_PLANKS); wainscot = b(Blocks.STRIPPED_DARK_OAK_LOG); upper = theme.palette().wall().get(); }
            default -> { floorA = theme.palette().floor().get(); floorB = b(Blocks.SPRUCE_PLANKS); wainscot = b(Blocks.STRIPPED_SPRUCE_LOG); upper = theme.palette().wall().get(); }
        }
        BlockState pillar = kind == Kind.WASHITSU ? b(Blocks.STRIPPED_BIRCH_LOG) : b(Blocks.STRIPPED_OAK_LOG);
        BlockState stain = b(Blocks.LIGHT_GRAY_TERRACOTTA);
        for (int x = x0; x <= x1; x++)
            for (int z = 0; z <= d + 1; z++) {
                boolean w = x == x0 || x == x1 || z == 0 || z == d + 1;
                boolean board = kind == Kind.WASHITSU ? ((x / 2 + z / 3) & 1) == 0 : (z & 1) == 0; // 床板の向き・畳の目
                set(l, o, x, 0, z, board ? floorA : floorB);
                for (int y = 1; y < H; y++) {
                    if (!w) { set(l, o, x, y, z, air); continue; }
                    boolean post = (x == x0 || x == x1) && z % 4 == 0;
                    BlockState s = post ? pillar : y == 1 ? wainscot : upper;
                    if (!post && y > 1 && rand.nextFloat() < 0.04f) s = stain; // 染み
                    set(l, o, x, y, z, s);
                }
                set(l, o, x, H, z, ceil);
            }
        set(l, o, EX, 1, 0, air);
        set(l, o, EX, 2, 0, air);
    }

    /** 壁際の開かない扉（後ろは壁、扉は壁の1マス手前）。 */
    private static void closetDoor(ServerLevel l, BlockPos o, Side s, int z, Kind kind) {
        Block door = kind == Kind.WASHITSU ? Blocks.BIRCH_DOOR : kind == Kind.KITCHEN ? Blocks.SPRUCE_DOOR : Blocks.OAK_DOOR;
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, s.inward());
        l.setBlock(o.offset(s.in(1), 1, z), lower, 2);
        l.setBlock(o.offset(s.in(1), 2, z), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        set(l, o, s.wall(), 3, z, b(Blocks.STRIPPED_OAK_LOG)); // 扉の上の鴨居
    }

    /** z から n マス空いているか（壁際の家具の列）。 */
    private static boolean free(boolean[] used, int z, int n) {
        for (int k = z; k < z + n; k++) if (k < 0 || k >= used.length || used[k]) return false;
        return true;
    }
    private static void take(boolean[] used, int z, int n) { for (int k = z; k < z + n && k < used.length; k++) used[k] = true; }
    private static int findFree(boolean[] used, int n, Random rand) {
        for (int t = 0; t < 10; t++) { int z = 2 + rand.nextInt(Math.max(1, used.length - 3 - n)); if (free(used, z, n)) return z; }
        for (int z = 2; z + n < used.length - 1; z++) if (free(used, z, n)) return z;
        return -1;
    }

    private static BlockState stair(Block b, Direction facing) { return b.defaultBlockState().setValue(StairBlock.FACING, facing); }
    private static BlockState topSlab(Block b) { return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP); }

    /** 細い側（廊下）：靴箱・傘立て・額縁代わりのボタン程度。 */
    private static int narrowDecor(ServerLevel l, BlockPos o, Side s, int d, boolean[] used, Random rand, Kind kind) {
        int n = 0;
        for (int t = 0; t < 2; t++) {
            int z = findFree(used, 1, rand);
            if (z < 0) break;
            take(used, z, 1);
            float f = rand.nextFloat();
            // 壁の飾り：開いた落とし戸（棚板）か、額縁に見立てた色付きの壁
            if (f < 0.5f) set(l, o, s.in(1), 3, z, b(Blocks.SPRUCE_TRAPDOOR).setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, s.inward()).setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true));
            else set(l, o, s.wall(), 2, z, b(rand.nextBoolean() ? Blocks.BROWN_TERRACOTTA : Blocks.CYAN_TERRACOTTA));
            n++;
        }
        // 靴箱・傘立て（廊下の入口側、壁際 1 マス。細い側は通路 x=8/12 の端）
        if (kind == Kind.HALLWAY) {
            int z = 2;
            if (free(used, z, 2)) {
                set(l, o, s.in(1), 1, z, b(Blocks.BARREL));
                set(l, o, s.in(1), 1, z + 1, rand.nextBoolean() ? b(Blocks.POTTED_BAMBOO) : b(Blocks.DECORATED_POT));
                take(used, z, 2); n += 2;
            }
        }
        return n;
    }

    /** リビング：ソファ（階段）、ローテーブル、テレビ台とテレビ、本棚。 */
    private static int living(ServerLevel l, BlockPos o, Side s, int d, boolean[] used, Random rand, boolean first) {
        int n = 0;
        if (first) {
            int z = findFree(used, 3, rand);
            if (z >= 0) { // ソファ（背を壁に）と手前のローテーブル
                for (int k = 0; k < 3; k++) set(l, o, s.in(1), 1, z + k, stair(Blocks.OAK_STAIRS, s.outward()));
                set(l, o, s.in(3), 1, z + 1, topSlab(Blocks.SPRUCE_SLAB));
                set(l, o, s.in(3), 2, z + 1, b(Blocks.FLOWER_POT));
                take(used, z, 3); n += 5;
            }
        } else {
            int z = findFree(used, 3, rand);
            if (z >= 0) { // テレビ台とテレビ（黒い画面、砂嵐に見える灰色のガラス）
                for (int k = 0; k < 3; k++) set(l, o, s.in(1), 1, z + k, b(Blocks.DARK_OAK_PLANKS));
                set(l, o, s.in(1), 2, z + 1, b(Blocks.BLACK_CONCRETE));
                set(l, o, s.in(2), 2, z + 1, b(Blocks.GRAY_STAINED_GLASS_PANE));
                take(used, z, 3); n += 5;
            }
        }
        int z = findFree(used, 2, rand);
        if (z >= 0) { set(l, o, s.in(1), 1, z, b(Blocks.BOOKSHELF)); set(l, o, s.in(1), 2, z, b(Blocks.BOOKSHELF)); set(l, o, s.in(1), 1, z + 1, b(Blocks.POTTED_FERN)); take(used, z, 2); n += 3; }
        return n;
    }

    /** キッチン：流し台・コンロ（かまど・燻製器・大釜）の列、冷蔵庫、食卓と椅子。 */
    private static int kitchen(ServerLevel l, BlockPos o, Side s, int d, boolean[] used, Random rand, boolean first) {
        int n = 0;
        if (first) {
            int z = findFree(used, 5, rand);
            int len = 5;
            if (z < 0) { z = findFree(used, 3, rand); len = 3; }
            if (z >= 0) {
                for (int k = 0; k < len; k++) {
                    BlockState c = k == 1 ? b(Blocks.CAULDRON) : k == len - 2 ? b(Blocks.SMOKER).setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.FACING, s.inward())
                            : b(Blocks.POLISHED_ANDESITE);
                    set(l, o, s.in(1), 1, z + k, c);
                    set(l, o, s.in(1), 3, z + k, topSlab(Blocks.SPRUCE_SLAB)); // 吊り戸棚
                }
                take(used, z, len); n += len * 2;
            }
            int f = findFree(used, 1, rand);
            if (f >= 0) { set(l, o, s.in(1), 1, f, b(Blocks.WHITE_CONCRETE)); set(l, o, s.in(1), 2, f, b(Blocks.WHITE_CONCRETE)); take(used, f, 1); n += 2; } // 冷蔵庫（飾り）
        } else {
            int z = findFree(used, 3, rand);
            if (z >= 0) { // 食卓（柵＋感圧板）と椅子
                set(l, o, s.in(2), 1, z + 1, b(Blocks.OAK_FENCE));
                set(l, o, s.in(2), 2, z + 1, b(Blocks.OAK_PRESSURE_PLATE));
                set(l, o, s.in(2), 1, z, stair(Blocks.OAK_STAIRS, Direction.NORTH));
                set(l, o, s.in(2), 1, z + 2, stair(Blocks.OAK_STAIRS, Direction.SOUTH));
                take(used, z, 3); n += 4;
            }
            int b2 = findFree(used, 2, rand);
            if (b2 >= 0) { set(l, o, s.in(1), 1, b2, b(Blocks.BARREL)); set(l, o, s.in(1), 1, b2 + 1, b(Blocks.BARREL)); take(used, b2, 2); n += 2; }
        }
        return n;
    }

    /** 寝室：ベッド（頭を壁に）、枕元の棚とろうそく、机、本棚。 */
    private static int bedroom(ServerLevel l, BlockPos o, Side s, int d, boolean[] used, Random rand, boolean first) {
        int n = 0;
        int z = findFree(used, 2, rand);
        if (z >= 0) {
            Block bed = new Block[]{Blocks.WHITE_BED, Blocks.LIGHT_BLUE_BED, Blocks.PINK_BED, Blocks.GRAY_BED}[rand.nextInt(4)];
            BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, s.outward()).setValue(BedBlock.PART, BedPart.FOOT);
            l.setBlock(o.offset(s.in(2), 1, z), foot, 2);
            l.setBlock(o.offset(s.in(1), 1, z), foot.setValue(BedBlock.PART, BedPart.HEAD), 2);
            set(l, o, s.in(1), 1, z + 1, b(Blocks.SPRUCE_PLANKS));
            if (rand.nextFloat() < 0.5f) set(l, o, s.in(1), 2, z + 1, b(Blocks.WHITE_CANDLE).setValue(CandleBlock.CANDLES, 1).setValue(CandleBlock.LIT, false));
            take(used, z, 2); n += 3;
        }
        int w = findFree(used, 2, rand);
        if (w >= 0) {
            if (first) { set(l, o, s.in(1), 1, w, topSlab(Blocks.BIRCH_SLAB)); set(l, o, s.in(2), 1, w, stair(Blocks.BIRCH_STAIRS, s.inward().getOpposite())); }
            else { set(l, o, s.in(1), 1, w, b(Blocks.BOOKSHELF)); set(l, o, s.in(1), 2, w, b(Blocks.BOOKSHELF)); }
            take(used, w, 2); n += 2;
        }
        return n;
    }

    /** 和室：床の間（花瓶）、座布団（赤い絨毯）、行灯（ランタン）。ちゃぶ台は隠れ場所のテーブル。 */
    private static int washitsu(ServerLevel l, BlockPos o, Side s, int d, boolean[] used, Random rand) {
        int n = 0;
        int z = findFree(used, 2, rand);
        if (z >= 0) {
            set(l, o, s.in(1), 1, z, topSlab(Blocks.DARK_OAK_SLAB));
            set(l, o, s.in(1), 1, z + 1, topSlab(Blocks.DARK_OAK_SLAB));
            set(l, o, s.in(1), 2, z, b(Blocks.POTTED_CHERRY_SAPLING));
            take(used, z, 2); n += 3;
        }
        int c = findFree(used, 2, rand);
        if (c >= 0) { set(l, o, s.in(2), 1, c, b(Blocks.RED_CARPET)); set(l, o, s.in(2), 1, c + 1, b(Blocks.RED_CARPET)); take(used, c, 2); n += 2; }
        int a = findFree(used, 1, rand);
        if (a >= 0) { set(l, o, s.in(1), 1, a, b(Blocks.BIRCH_FENCE)); set(l, o, s.in(1), 2, a, b(Blocks.LANTERN)); take(used, a, 1); n += 2; }
        return n;
    }

    /** 通路の真ん中の敷物（歩ける絨毯）。 */
    private static void rug(ServerLevel l, BlockPos o, int d, Kind kind, Random rand) {
        if (kind == Kind.WASHITSU) return; // 畳の上には敷かない
        BlockState c = b(new Block[]{Blocks.RED_CARPET, Blocks.BROWN_CARPET, Blocks.GRAY_CARPET}[rand.nextInt(3)]);
        int z0 = 3 + rand.nextInt(Math.max(1, d - 8)), len = Math.min(5, d - z0 - 2);
        for (int z = z0; z < z0 + len; z++) for (int x = EX - 2; x <= EX + 2; x++) if (l.getBlockState(o.offset(x, 1, z)).isAir()) set(l, o, x, 1, z, c);
    }

    private static SectionTheme.HidingType pick(SectionTheme theme, float f) {
        float total = 0f;
        for (var w : theme.hidingMix()) total += w.weight();
        float r = f * total, cum = 0f;
        for (var w : theme.hidingMix()) { cum += w.weight(); if (r < cum) return w.value(); }
        return theme.hidingMix().get(theme.hidingMix().size() - 1).value();
    }

    /** 隠れ場所：6〜9 マスごとに左右交互（H と同じ考え方）。最後から出口まで MAX_HIDE_GAP を超えるなら足す。最低 MIN_HIDES、ロッカー1つ以上。 */
    private static List<int[]> planHiding(Random rand, SectionTheme theme, int d) {
        List<int[]> out = new ArrayList<>();
        int side = rand.nextInt(2);
        int z = 3 + rand.nextInt(3);
        while (z <= d - 2) {
            out.add(new int[]{side, z, pick(theme, rand.nextFloat()) == SectionTheme.HidingType.LOCKER ? 0 : 1});
            side ^= 1;
            z += 6 + rand.nextInt(4);
        }
        if (out.isEmpty() || d - out.get(out.size() - 1)[1] > MAX_HIDE_GAP) out.add(new int[]{side, Math.max(3, d - 2), 0});
        while (out.size() < MIN_HIDES) {
            int[] last = out.get(out.size() - 1);
            int nz = last[1] + 3 <= d - 2 ? last[1] + 3 : Math.max(3, last[1] - 3);
            out.add(new int[]{last[0] ^ 1, nz, pick(theme, rand.nextFloat()) == SectionTheme.HidingType.LOCKER ? 0 : 1});
        }
        boolean anyLocker = false;
        for (int[] h : out) if (h[2] == 0) anyLocker = true;
        if (!anyLocker) out.get(out.size() - 1)[2] = 0;
        return out;
    }

    private static String placeHiding(ServerLevel l, BlockPos o, Side left, Side right, List<int[]> hides) {
        int lockers = 0, tables = 0;
        List<Integer> zs = new ArrayList<>();
        for (int[] h : hides) {
            Side s = h[0] == 0 ? left : right;
            if (h[2] == 0) {
                RoomGenerator.placeHidingSpot(l, o.offset(s.wall(), 1, h[1]), s.inward()); // クローゼット・冷蔵庫・押し入れ（壁の窪み）
                lockers++;
            } else {
                RoomGenerator.placeHiding(l, o.offset(s.in(1), 1, h[1]), s.inward(), SectionTheme.HidingType.TABLE); // ベッドの下・ちゃぶ台
                tables++;
            }
            zs.add(h[1]);
        }
        return "lockers=" + lockers + " tables=" + tables + " z=" + zs;
    }

    private static void lights(ServerLevel l, BlockPos o, int d, SectionTheme theme, int localRoom) {
        RoomLighting.Fixture f = RoomLighting.fixture(RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        List<int[]> interiors = List.of(new int[]{EX - 2, 1, EX + 2, d});
        List<int[]> path = List.of(new int[]{EX, 0}, new int[]{EX, 1}, new int[]{EX, d}, new int[]{EX, d + 1});
        int placed = 0;
        for (int[] c : RoomLighting.candidates(interiors, f)) {
            if (placed >= f.maxCount()) break;
            BlockPos pos = o.offset(c[0], f.hanging() ? H - 1 : 1, c[1]);
            if (!l.getBlockState(pos).isAir()) continue;
            if (f.hanging() ? l.getBlockState(pos.above()).isAir() : l.getBlockState(pos.below()).isAir()) continue;
            if (!f.hanging() && RoomLighting.nearPath(path, c[0], c[1])) continue;
            if (c[0] == EX && c[1] == d) continue;
            l.setBlockAndUpdate(pos, f.state().get());
            placed++;
        }
    }
}
