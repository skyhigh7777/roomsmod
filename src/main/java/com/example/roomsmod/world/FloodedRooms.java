package com.example.roomsmod.world;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * F（Flooded、水没区画）の部屋：まっすぐな部屋（入口 x=10 の z=0、出口 x=10 の最大Z面）。床一面に浅い水。
 * <ul>
 *   <li>水：内側の床（y=0）を水浸しの下付きハーフブロックにする（水面は床から約0.9、プレイヤーは0.5の高さに立つ＝くるぶしほど）。
 *       水源は床の中だけなので流れ出さない（外周の床・扉の下は普通のブロック）。y=1 より上には水を置かないので、通路・隠れ場所・扉はふさがない。
 *       頭は水に入らない。念のため区画の中では空気を満タンに保つ（溺れない）。</li>
 *   <li>形：CULVERT（内寸幅5）/ HALL（幅9、両側に柱）/ CISTERN（幅11、柱2列）。前の部屋と重なる時は細い形にする。
 *       奥行き SHORT 20 / STD 30 / LONG 42 / VLONG 56（VLONG は F-250 以降）。</li>
 *   <li>見た目：苔むした・ひび割れた石レンガ、天井から鎖、天井に光るコケ（暗い緑の光）。通路の真ん中 3 マス（x=9..11）は常に空ける。</li>
 *   <li>隠れ場所：壁を窪ませたロッカー（床は普通のブロック）／壁際のテーブル。間隔 MAX_HIDE_GAP 以内、最低 MIN_HIDES、ロッカー1つ以上。</li>
 *   <li>区画の中：ダッシュのスタミナ消費 STAMINA_DRAIN_MUL 倍（Stamina）、ときどき天井から滴る音。</li>
 * </ul>
 */
@EventBusSubscriber(modid = "roomsmod")
public final class FloodedRooms {
    private FloodedRooms() {}

    /** F の中でダッシュした時のスタミナ消費の倍率（通常 8 秒で 0 → 約 5.3 秒）。 */
    public static final float STAMINA_DRAIN_MUL = 1.5f;

    static final int H = 5;
    static final int EX = 10;
    static final int MAX_HIDE_GAP = 16;
    static final int MIN_HIDES = 2;
    static final int VLONG_FROM = 250;

    enum Length { SHORT(20), STD(30), LONG(42), VLONG(56); final int depth; Length(int d) { depth = d; } }
    enum Shape { CULVERT(2), HALL(4), CISTERN(5); final int half; Shape(int h) { half = h; } }

    private static final int[] LENGTH_WEIGHTS = {25, 45, 25, 5};
    private static final int[] SHAPE_WEIGHTS = {30, 45, 25};

    // ===================== 区画の中の効果 =====================

    /** プレイヤーが今 F の部屋にいるか（サーバー側、部屋ディメンションだけ）。 */
    public static boolean inFlooded(Player p) {
        if (!(p instanceof ServerPlayer sp) || !sp.level().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return false;
        RoomsState rs = RoomsState.get(sp.serverLevel());
        int idx = com.example.roomsmod.event.RoomTracker.roomIndexAt(rs, sp.blockPosition());
        return idx > 0 && Sections.forRoom(rs, idx) == Sections.F;
    }

    /** ダッシュのスタミナ消費の倍率（F の中だけ STAMINA_DRAIN_MUL）。 */
    public static float staminaDrainMul(Player p) { return inFlooded(p) ? STAMINA_DRAIN_MUL : 1f; }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.tickCount % 5 != 0 || !inFlooded(p)) return;
        if (p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(p.getMaxAirSupply()); // 溺れない（頭は水に入らないが念のため）
        var r = p.getRandom();
        if (r.nextInt(24) == 0) { // 平均 6 秒ごとに天井から滴る音
            BlockPos at = p.blockPosition().offset(r.nextInt(9) - 4, 3, r.nextInt(9) - 4);
            p.serverLevel().playSound(null, at, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.AMBIENT, 0.8f, 0.7f + r.nextFloat() * 0.4f);
        }
    }

    // ===================== 生成 =====================

    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, Random rand, RoomsState state) {
        SectionTheme theme = Sections.forRoom(state, roomNumber);
        int local = Sections.localRoom(state, roomNumber);
        Length len = Length.values()[SectionTheme.pickWeighted(rand, LENGTH_WEIGHTS)];
        if (len == Length.VLONG && local < VLONG_FROM) len = Length.LONG;
        Shape shape = Shape.values()[SectionTheme.pickWeighted(rand, SHAPE_WEIGHTS)];
        int d = len.depth;
        Shape picked = shape;
        // 前の部屋と重ならない形（選んだ形 → 細い形の順）
        while (!isClear(origin, shape.half, d, state.generatedRooms) && shape != Shape.CULVERT) shape = Shape.values()[shape.ordinal() - 1];
        if (shape != picked) RoomsDebug.log("F room {} shape {} overlaps an older room, using {}", roomNumber, picked, shape);
        int half = shape.half, x0 = EX - half - 1, x1 = EX + half + 1;

        buildShell(level, origin, x0, x1, d, theme, rand);
        BlockPos exitDoor = origin.offset(EX, 1, d + 1);
        BlockState lower = theme.palette().door().get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(exitDoor, lower);
        level.setBlockAndUpdate(exitDoor.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        RoomGenerator.sealExit(level, exitDoor);

        List<Integer> hideZ = new ArrayList<>();
        String hides = placeHiding(level, origin, half, d, rand, theme, hideZ);
        int pillars = shape == Shape.CULVERT ? 0 : placePillars(level, origin, shape, d, hideZ);
        int decor = decorate(level, origin, half, d, rand);
        lights(level, origin, half, d, theme, local);
        RoomGenerator.placeExitSign(level, exitDoor.offset(0, 2, -1), Sections.formatRoom(state, roomNumber + 1));

        List<BlockPos> path = List.of(origin.offset(EX, 1, 0), origin.offset(EX, 1, 1), origin.offset(EX, 1, d), exitDoor);
        List<BoundingBox> boxes = List.of(box(origin, half, d));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(exitDoor);
        state.setCurrentRoom(roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=F origin={} flooded length={} depth={} shape={} boxes={} exitDoor={} hiding={} pillars={} decor={}",
                    roomNumber, Sections.formatRoom(state, roomNumber), origin, len, d, shape, boxes, exitDoor, hides, pillars, decor);
        return exitDoor;
    }

    private static BoundingBox box(BlockPos o, int half, int d) {
        return new BoundingBox(o.getX() + EX - half - 1, o.getY(), o.getZ(), o.getX() + EX + half + 1, o.getY() + H, o.getZ() + d + 1);
    }

    /** RoomGenerator.isClear と同じ判定（直前の部屋とは接してよい、それ以外とは1マス空ける）。 */
    private static boolean isClear(BlockPos o, int half, int d, List<List<BoundingBox>> rooms) {
        BoundingBox mine = box(o, half, d);
        for (int i = 0; i < rooms.size(); i++) {
            BoundingBox test = i == rooms.size() - 1 ? mine : mine.inflatedBy(1);
            for (BoundingBox other : rooms.get(i)) if (test.intersects(other)) return false;
        }
        return true;
    }

    private static void set(ServerLevel l, BlockPos o, int x, int y, int z, BlockState s) { l.setBlockAndUpdate(o.offset(x, y, z), s); }

    /** 水浸しの床のハーフブロック。 */
    private static BlockState wetSlab(Random rand) {
        float f = rand.nextFloat();
        Block b = f < 0.55f ? Blocks.STONE_BRICK_SLAB : f < 0.80f ? Blocks.MOSSY_STONE_BRICK_SLAB : f < 0.93f ? Blocks.MOSSY_COBBLESTONE_SLAB : Blocks.COBBLESTONE_SLAB;
        return b.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(SlabBlock.WATERLOGGED, true);
    }

    private static boolean isWetFloor(BlockState s) { return s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.WATERLOGGED); }

    /** 外周（壁と、その下の床）は普通のブロック、内側の床は水浸しのハーフブロック。壁の一番下は苔むした石レンガ（水の跡）。 */
    private static void buildShell(ServerLevel l, BlockPos o, int x0, int x1, int d, SectionTheme theme, Random rand) {
        BlockState ring = theme.palette().floor().get(), moss = theme.palette().wall().get(), cracked = theme.palette().ceiling().get();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState(), ceil = Blocks.STONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        for (int x = x0; x <= x1; x++)
            for (int z = 0; z <= d + 1; z++) {
                boolean w = x == x0 || x == x1 || z == 0 || z == d + 1;
                set(l, o, x, 0, z, w ? ring : wetSlab(rand));
                for (int y = 1; y < H; y++) {
                    if (!w) { set(l, o, x, y, z, air); continue; }
                    float f = rand.nextFloat();
                    set(l, o, x, y, z, y == 1 ? moss : f < 0.35f ? moss : f < 0.5f ? cracked : brick);
                }
                set(l, o, x, H, z, rand.nextFloat() < 0.2f ? cracked : ceil);
            }
        set(l, o, EX, 1, 0, air);
        set(l, o, EX, 2, 0, air);
    }

    /** 隠れ場所（U と同じ並べ方）：z=3 から 10〜12 ごとに左右交互、出口まで MAX_HIDE_GAP を超えるなら足す。ロッカーは壁の窪み、テーブルは壁際。 */
    private static String placeHiding(ServerLevel l, BlockPos o, int half, int d, Random rand, SectionTheme theme, List<Integer> hideZ) {
        List<Integer> zs = new ArrayList<>();
        int z = 3 + rand.nextInt(4);
        while (z <= d - 2) { zs.add(z); z += 10 + rand.nextInt(3); }
        if (zs.isEmpty() || d - zs.get(zs.size() - 1) > MAX_HIDE_GAP) zs.add(Math.max(3, d - 3));
        while (zs.size() < MIN_HIDES) zs.add(Math.max(3, Math.min(d - 2, zs.get(zs.size() - 1) + 6)));
        boolean left = rand.nextBoolean();
        int lockers = 0, tables = 0;
        for (int i = 0; i < zs.size(); i++) {
            int hz = zs.get(i);
            if (hideZ.contains(hz)) hz++;
            SectionTheme.HidingType t = pick(theme, rand.nextFloat());
            if (t == SectionTheme.HidingType.TABLE && lockers == 0 && i == zs.size() - 1) t = SectionTheme.HidingType.LOCKER; // 最低1つはロッカー
            Direction in = left ? Direction.EAST : Direction.WEST;
            if (t == SectionTheme.HidingType.LOCKER) {
                int wx = left ? EX - half - 1 : EX + half + 1;
                RoomGenerator.placeHidingSpot(l, o.offset(wx, 1, hz), in);
                lockers++;
                RoomsDebug.log("hiding spot type=locker at {} (wall niche)", o.offset(wx, 1, hz));
            } else {
                int tx = left ? EX - half : EX + half;
                RoomGenerator.placeHiding(l, o.offset(tx, 1, hz), in, SectionTheme.HidingType.TABLE);
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

    /** 柱（床から天井まで、柱の下の床は普通のブロック）。x=7/13、HALL は 7 ブロックごと、CISTERN は 5 ブロックごと。隠れ場所の前後2マスは避ける。 */
    private static int placePillars(ServerLevel l, BlockPos o, Shape shape, int d, List<Integer> hideZ) {
        BlockState p = Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), base = Blocks.STONE_BRICKS.defaultBlockState();
        int[] xs = {EX - 3, EX + 3};
        int step = shape == Shape.CISTERN ? 5 : 7;
        int n = 0;
        for (int z = 6; z <= d - 4; z += step) {
            boolean near = false;
            for (int hz : hideZ) if (Math.abs(hz - z) <= 2) near = true;
            if (near) continue;
            for (int x : xs) {
                set(l, o, x, 0, z, base);
                for (int y = 1; y < H; y++) set(l, o, x, y, z, p);
            }
            n++;
        }
        return n;
    }

    /** 天井の鎖と光るコケ（通路の真ん中 3 マスと隠れ場所・出口の前は避ける）。置いた数を返す。 */
    private static int decorate(ServerLevel l, BlockPos o, int half, int d, Random rand) {
        int n = 0;
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true)
                .setValue(BlockStateProperties.WATERLOGGED, false);
        for (int x = EX - half; x <= EX + half; x++)
            for (int z = 2; z <= d - 1; z++) {
                if (Math.abs(x - EX) <= 1) continue;
                BlockPos top = o.offset(x, H - 1, z);
                if (!l.getBlockState(top).isAir()) continue;
                float f = rand.nextFloat();
                if (f < 0.03f) { // 天井から垂れた鎖（2段）
                    BlockPos mid = top.below();
                    if (!l.getBlockState(mid).isAir() || !l.getBlockState(mid.below()).isAir()) continue;
                    l.setBlockAndUpdate(top, Blocks.CHAIN.defaultBlockState());
                    l.setBlockAndUpdate(mid, Blocks.CHAIN.defaultBlockState());
                    n++;
                } else if (f < 0.08f) {
                    l.setBlockAndUpdate(top, lichen);
                    n++;
                }
            }
        return n;
    }

    /** 天井の照明（RoomLighting の段階どおり）。置き型（ろうそく）は下の床を普通のブロックにしてから置く（ハーフブロックの上には立たない）。 */
    private static void lights(ServerLevel l, BlockPos o, int half, int d, SectionTheme theme, int localRoom) {
        RoomLighting.Fixture f = RoomLighting.fixture(RoomLighting.tier(localRoom, theme.lightFadeStart(), theme.lightFadeStep()), theme.palette().light());
        List<int[]> interiors = List.of(new int[]{EX - half, 1, EX + half, d});
        List<int[]> path = List.of(new int[]{EX, 0}, new int[]{EX, 1}, new int[]{EX, d}, new int[]{EX, d + 1});
        int placed = 0;
        for (int[] c : RoomLighting.candidates(interiors, f)) {
            if (placed >= f.maxCount()) break;
            BlockPos pos = o.offset(c[0], f.hanging() ? H - 1 : 1, c[1]);
            if (!l.getBlockState(pos).isAir()) continue;
            if (f.hanging() && l.getBlockState(pos.above()).isAir()) continue;
            if (!f.hanging() && RoomLighting.nearPath(path, c[0], c[1])) continue;
            if (c[0] == EX && c[1] == d) continue; // 番号プレート
            if (!f.hanging()) {
                if (isWetFloor(l.getBlockState(pos.below()))) l.setBlockAndUpdate(pos.below(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                else if (l.getBlockState(pos.below()).isAir()) continue;
            }
            l.setBlockAndUpdate(pos, f.state().get());
            placed++;
        }
    }
}
