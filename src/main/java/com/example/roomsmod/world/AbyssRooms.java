package com.example.roomsmod.world;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * A（Abyss）区画：縦穴を落ち続ける。
 * <ul>
 *   <li>A の最初の部屋（A-001）を作るとき、前の部屋の出口扉の先に小部屋（床に穴）と、その下の縦穴を1本だけ作る（{@link Shaft}）。</li>
 *   <li>縦穴は高さ {@value #L} ブロックの層（segment）を {@value #SEGMENTS} 段重ねたもの。プレイヤーは層1を落ち、層2に入ったら
 *       位置・向き・速度をそのまま {@value #L} ブロック上へ相対テレポート（層1の同じ場所）して 1 部屋進む。</li>
 *   <li>層 k の中身（横穴・ロッカー・番号の看板）は「先頭のプレイヤーの部屋 + (k - 1)」の部屋のもの。1 周ごとに中身を 1 層ぶん上へずらすので、
 *       テレポートの前後で周りの見た目は同じ（ブロックの差し替えはテレポートより先にクライアントへ送る）。</li>
 *   <li>横穴（ロッカー2つ）は 2〜3 部屋ごと。落ちながら横穴の高さで壁の方へ動いて入る（縦穴の列には何も置かない＝途中で止まらない）。最初の 1 周で小部屋の穴をふさぐ（上へは戻れない）。</li>
 *   <li>縦穴の中では落下ダメージなし。落下速度の上限（約 12 ブロック/秒、スニーク中は約半分）はクライアント側（ClientAbyss / AbyssClient）。</li>
 * </ul>
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID)
public final class AbyssRooms {
    private AbyssRooms() {}

    /** 1 部屋（1 周）の高さ。 */
    public static final int L = 12;
    /** 縦穴の層の数（層0=上、層1=プレイヤーが落ちる層、層2・3=下に見える層）。 */
    public static final int SEGMENTS = 4;
    /**
     * 最初の部屋を過ぎた（小部屋をふさぐ）あと、小部屋の場所から上へ足す層の数（層 -1 .. -EXTRA_ABOVE、中身は「部屋 lead + (k - 1)」）。
     * 上にも同じ縦穴が続いて見える（天井は層1の上端から {@value #EXTRA_ABOVE}+1 部屋ぶん上）。
     */
    public static final int EXTRA_ABOVE = 3;
    /** 層の配列の大きさ（添字 = k + EXTRA_ABOVE）。 */
    static final int TOTAL = EXTRA_ABOVE + SEGMENTS;
    /** 最下層の下の余白（ここは常に空、底は黒い床）。 */
    public static final int BOTTOM_GAP = 4;
    /** 落下速度の上限（ブロック/tick）。5 ブロック/秒（1 部屋 約 2.4 秒）。スニーク中はこの半分。 */
    public static final double MAX_FALL = 0.25, MAX_FALL_SNEAK = 0.125;

    // ローカル座標（小部屋の origin から）。縦穴の内側 x 8..12, z 3..7、壁は 3 ブロック厚（x 5..15, z 0..10）
    public static final int CX = 10, CZ = 5, IN = 2, X0 = 5, X1 = 15, Z0 = 0, Z1 = 10;
    /** 看板と照明の層内の高さ（層の底から）。 */
    static final int SIGN_OFF = 9, TORCH_OFF = 3;
    /** 横穴の床の層内の高さの範囲（底から）。床+4 が天井で層内に収まる。 */
    static final int ALCOVE_OFF_MIN = 1, ALCOVE_OFF_MAX = L - 5;

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** 縦穴1本の位置（RoomsState に保存）。shown は今ブロックとして置いてある各層の中身（保存しない＝読み込み後は全部作り直す）。 */
    public static final class Shaft {
        public final int ox, oy, oz, entryRoom, bottomY;
        final Alcove[] shown = new Alcove[TOTAL];
        final int[] shownRoom = new int[TOTAL];
        boolean built = false, sealed = false;
        /** 今ブロックで見えている層1の部屋（先頭）。保存しない（AbyssSpace.lead が補う）。 */
        int lead = Integer.MIN_VALUE;
        /** 本物の横穴のない部屋に足した「見た目だけの横穴」（部屋 → 横穴）。保存しない（置いた敵が毎 tick 登録し直す）。 */
        final Map<Integer, Alcove> fakes = new HashMap<>();
        /** 縦穴を狭めている間か（{@link #setSqueeze}）と、そのために置いたブロック。保存しない（読み込み後は clearColumn が片付ける）。 */
        boolean squeeze = false;
        /** 狭めている間に残すマス（AbyssSqueeze のマスク）。 */
        int squeezeOpen = 0;
        final java.util.Set<BlockPos> squeezeCells = new java.util.HashSet<>();

        Shaft(int ox, int oy, int oz, int entryRoom, int bottomY) { this.ox = ox; this.oy = oy; this.oz = oz; this.entryRoom = entryRoom; this.bottomY = bottomY; }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", ox); t.putInt("y", oy); t.putInt("z", oz); t.putInt("entry", entryRoom); t.putInt("bottom", bottomY);
            return t;
        }

        public static Shaft load(CompoundTag t) { return new Shaft(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("entry"), t.getInt("bottom")); }

        public BlockPos at(int x, int y, int z) { return new BlockPos(ox + x, y, oz + z); }
        /** 層 k のいちばん上のブロックの y。 */
        public int segTop(int k) { return oy - 1 - k * L; }
        public int segBottom(int k) { return segTop(k) - L + 1; }
        /** 足がこの y より下（層2）に入ったら 1 周。 */
        int loopY() { return segBottom(1); }

        /** 落ちる列の空気のいちばん上の y（小部屋があれば床の下、ふさいだ後は上へ足した層の上端）。 */
        public int colTop() { return sealed ? segTop(-EXTRA_ABOVE) : oy - 1; }
        /** 上へ足した層のふた（天井）の y。 */
        int capY() { return segTop(-EXTRA_ABOVE) + 1; }

        /** 縦穴（と小部屋・上へ足した層）の水平範囲・高さの範囲に入っているか。 */
        public boolean contains(Vec3 p) {
            return p.x >= ox + X0 && p.x < ox + X1 + 1 && p.z >= oz + Z0 && p.z < oz + Z1 + 1 && p.y >= bottomY && p.y < Math.max(oy + 6, colTop() + 2);
        }
        /** 縦穴の中（小部屋の床より下、ふさいだ後は上へ足した層も）か。 */
        boolean inShaft(Vec3 p) { return contains(p) && p.y < colTop() + 1; }

        @Override public String toString() { return "Shaft[o=(" + ox + "," + oy + "," + oz + ") entry=" + entryRoom + " bottom=" + bottomY + "]"; }
    }

    /** 横穴（side の壁、床の高さは層の底から off）。fake は見た目だけの横穴（照明なし）。 */
    public record Alcove(Direction side, int off, boolean fake) {}

    // ===================== 生成 =====================

    /**
     * RoomGenerator.generateNextRoom から（部屋 roomNumber が A のとき）。first（前の部屋が A ではない／縦穴がまだない）なら
     * origin に小部屋と縦穴を作る。それ以外は同じ縦穴に部屋（経路・範囲）を 1 つ足すだけ。
     * 返り値は仮の出口（扉はない。RoomsTravel が次の部屋の origin に使う：縦穴の範囲の外）。
     */
    static BlockPos generate(ServerLevel level, BlockPos origin, int roomNumber, RoomsState state, boolean first) {
        Shaft s = state.abyss;
        if (first || s == null) {
            int bottom = origin.getY() - 1 - SEGMENTS * L - BOTTOM_GAP;
            int minY = level.getMinBuildHeight() + 1;
            if (bottom < minY) { RoomsDebug.warn("abyss shaft bottom {} below world floor, clamped to {}", bottom, minY); bottom = minY; }
            s = new Shaft(origin.getX(), origin.getY(), origin.getZ(), roomNumber, bottom);
            state.abyss = s;
            buildStructure(level, s);
            RoomsDebug.log("abyss shaft built for room {} ({}) {}", roomNumber, Sections.formatRoom(state, roomNumber), s);
        }
        List<BoundingBox> boxes = new ArrayList<>();
        boxes.add(new BoundingBox(s.ox + X0, s.bottomY, s.oz + Z0, s.ox + X1, s.oy, s.oz + Z1));
        List<BlockPos> path = new ArrayList<>();
        if (roomNumber == s.entryRoom) {
            boxes.add(new BoundingBox(s.ox + X0, s.oy, s.oz + Z0, s.ox + X1, s.oy + 5, s.oz + Z1));
            path.add(s.at(CX, s.oy + 1, 0));
            path.add(s.at(CX, s.oy + 1, CZ));
        } else {
            path.add(s.at(CX, s.segTop(1), CZ));
        }
        path.add(s.at(CX, s.segBottom(1), CZ));
        state.addRoom(boxes, path);
        state.setExpectedExitDoor(null); // 扉はない（扉のクリックは無視される）
        state.setCurrentRoom(roomNumber);
        refresh(level, state, roomNumber);
        if (RoomsDebug.isEnabled())
            RoomsDebug.log("room generated #{} ({}) section=A shaft={} boxes={} alcoveHere={}", roomNumber, Sections.formatRoom(state, roomNumber), s, boxes, alcoveFor(level, s, roomNumber));
        return s.at(CX, s.oy + 1, Z1 + 5);
    }

    private static BlockState wall() { return Sections.A.palette().wall().get(); }
    private static BlockState floor() { return Sections.A.palette().floor().get(); }

    private static final int FLAGS = 2 | 16; // クライアントへ送る・隣の形の更新はしない（ロッカー・松明が外れない）

    private static void set(ServerLevel level, BlockPos p, BlockState st, List<BlockPos> changed) {
        if (level.getBlockState(p) == st) return;
        level.setBlock(p, st, FLAGS);
        if (changed != null) changed.add(p);
    }

    /** 小部屋・縦穴の外壁・底を作る（層の中身は refresh）。 */
    private static void buildStructure(ServerLevel level, Shaft s) {
        DropGuard.begin();
        try {
            BlockState w = wall(), air = Blocks.AIR.defaultBlockState();
            entryOrTower(level, s, false, null);
            // 縦穴：壁と中の空気（底まで）、底は黒い床
            for (int y = s.oy - 1; y >= s.bottomY; y--)
                for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
                    boolean inside = Math.abs(x - CX) <= IN && Math.abs(z - CZ) <= IN;
                    set(level, s.at(x, y, z), y == s.bottomY ? Blocks.BLACK_CONCRETE.defaultBlockState() : inside ? air : w, null);
                }
        } finally { DropGuard.end("abyss shaft build"); }
        s.built = false; // 層の中身（照明・横穴・看板）は次の refresh で全部作る
        s.sealed = false;
        java.util.Arrays.fill(s.shown, null);
        java.util.Arrays.fill(s.shownRoom, Integer.MIN_VALUE);
    }

    /**
     * 小部屋の場所（y = oy .. ふた）を作る。tower=false：小部屋（床 y=oy は穴あき、壁、天井 oy+5、入口 (10, oy+1..2, 0)）、その上は空気。
     * tower=true：上へ足す層 -1..-EXTRA_ABOVE（壁と中の空気・照明。中身は refresh）と、てっぺんのふた。
     */
    private static void entryOrTower(ServerLevel level, Shaft s, boolean tower, List<BlockPos> changed) {
        BlockState w = wall(), f = floor(), air = Blocks.AIR.defaultBlockState();
        if (tower) {
            for (int k = -1; k >= -EXTRA_ABOVE; k--) segmentShell(level, s, k, changed);
            for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) set(level, s.at(x, s.capY(), z), w, changed);
            RoomsDebug.log("abyss tower built above the entry room: column top y={} cap y={}", s.colTop(), s.capY());
            return;
        }
        for (int y = s.capY(); y > s.oy + 5; y--) for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) set(level, s.at(x, y, z), air, changed);
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            boolean hole = Math.abs(x - CX) <= IN && Math.abs(z - CZ) <= IN;
            set(level, s.at(x, s.oy, z), hole ? air : f, changed);
            boolean edge = x == X0 || x == X1 || z == Z0 || z == Z1;
            for (int y = 1; y <= 4; y++) set(level, s.at(x, s.oy + y, z), edge ? w : air, changed);
            set(level, s.at(x, s.oy + 5, z), w, changed);
        }
        set(level, s.at(CX, s.oy + 1, 0), air, changed);
        set(level, s.at(CX, s.oy + 2, 0), air, changed);
        set(level, s.at(CX, s.oy + 4, CZ - IN - 1 + 1), Sections.A.palette().light().get(), changed);
    }

    /** ローカルの横穴座標（north の向きで dx, dz）を side の向きへ回して、縦穴の中心からのワールド座標に。 */
    private static BlockPos rot(Shaft s, Direction side, int dx, int y, int dz) {
        int rx, rz;
        switch (side) {
            case EAST -> { rx = -dz; rz = dx; }
            case SOUTH -> { rx = -dx; rz = -dz; }
            case WEST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return s.at(CX + rx, y, CZ + rz);
    }

    /** 層 k に横穴 a を作る（remove=true なら元の壁に戻す）。 */
    private static void alcove(ServerLevel level, Shaft s, int k, Alcove a, boolean remove, List<BlockPos> changed) {
        int fy = s.segBottom(k) + a.off();
        BlockState w = wall(), air = Blocks.AIR.defaultBlockState();
        // 縦穴の内側（落ちる列）には何も置かない：壁沿いに落ちているプレイヤーが乗って止まらないように。
        // 横穴へは、横穴の高さで壁の方へ動けば横穴の床に乗れる（古い張り出しが残っていれば消す）
        for (int dx = -1; dx <= 1; dx++) set(level, rot(s, a.side(), dx, fy, -IN), air, changed);
        // 横穴の中（奥行き 2、幅 3、高さ 3）
        for (int dz = -IN - 1; dz >= -IN - 2; dz--) for (int dx = -1; dx <= 1; dx++) for (int y = 1; y <= 3; y++)
            set(level, rot(s, a.side(), dx, fy + y, dz), remove ? w : air, changed);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -IN - 1; dz >= -IN - 2; dz--) set(level, rot(s, a.side(), dx, fy, dz), remove ? w : floor(), changed);
        if (remove) return;
        Direction facing = a.side().getOpposite();
        for (int dx : new int[]{-1, 1}) {
            BlockPos p = rot(s, a.side(), dx, fy + 1, -IN - 2);
            var lower = RoomsMod.LOCKER.get().defaultBlockState().setValue(com.example.roomsmod.block.LockerBlock.FACING, facing);
            set(level, p, lower, changed);
            set(level, p.above(), lower.setValue(com.example.roomsmod.block.LockerBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), changed);
        }
        if (!a.fake()) set(level, rot(s, a.side(), 0, fy + 3, -IN - 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), changed);
    }

    /** 層 k の照明（四隅の魂の松明）と外壁の内側（作り直し用）。 */
    private static void segmentShell(ServerLevel level, Shaft s, int k, List<BlockPos> changed) {
        BlockState w = wall(), air = Blocks.AIR.defaultBlockState();
        for (int y = s.segTop(k); y >= s.segBottom(k); y--)
            for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
                boolean inside = Math.abs(x - CX) <= IN && Math.abs(z - CZ) <= IN;
                set(level, s.at(x, y, z), inside ? air : w, changed);
            }
        int ty = s.segBottom(k) + TORCH_OFF;
        BlockState t = Blocks.SOUL_WALL_TORCH.defaultBlockState();
        set(level, s.at(CX - IN, ty, CZ - IN), t.setValue(WallTorchBlock.FACING, Direction.EAST), changed);
        set(level, s.at(CX - IN, ty, CZ + IN), t.setValue(WallTorchBlock.FACING, Direction.EAST), changed);
        set(level, s.at(CX + IN, ty, CZ - IN), t.setValue(WallTorchBlock.FACING, Direction.WEST), changed);
        set(level, s.at(CX + IN, ty, CZ + IN), t.setValue(WallTorchBlock.FACING, Direction.WEST), changed);
    }

    /** 層 k の番号の看板（北の壁、x=12）。 */
    private static BlockPos signPos(Shaft s, int k) { return s.at(CX + IN, s.segBottom(k) + SIGN_OFF, CZ - IN); }

    private static void sign(ServerLevel level, RoomsState rs, Shaft s, int k, int room, List<BlockPos> changed, List<BlockPos> signs) {
        BlockPos p = signPos(s, k);
        SectionTheme.SignStyle st = Sections.A.sign();
        set(level, p, st.wallSign().get().defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), changed);
        if (level.getBlockEntity(p) instanceof SignBlockEntity be) {
            String text = room >= s.entryRoom ? Sections.formatRoom(rs, room) : "";
            SignText t = new SignText().setMessage(1, Component.literal(text)).setColor(st.color()).setHasGlowingText(st.glowing());
            be.setText(t, true);
            be.setWaxed(true);
            be.setChanged();
            level.sendBlockUpdated(p, be.getBlockState(), be.getBlockState(), 3);
            signs.add(p);
        }
    }

    /** 部屋 room に横穴があるか・どこか（entryRoom から 2〜3 部屋ごと、最初の部屋にも1つ）。 */
    static Alcove alcoveFor(ServerLevel level, Shaft s, int room) {
        if (room < s.entryRoom) return null;
        int a = s.entryRoom;
        while (a < room) a += 2 + (int) Math.floorMod(mix(level.getSeed(), a), 2L);
        if (a != room) return null;
        Random r = new Random(mix(level.getSeed(), room * 31L + 7));
        return new Alcove(SIDES[r.nextInt(4)], ALCOVE_OFF_MIN + r.nextInt(ALCOVE_OFF_MAX - ALCOVE_OFF_MIN + 1), false);
    }

    /** 部屋 room に見えている横穴（本物、なければ見た目だけのもの）。 */
    static Alcove shownFor(ServerLevel level, Shaft s, int room) {
        Alcove a = alcoveFor(level, s, room);
        return a != null ? a : s.fakes.get(room);
    }

    /** 部屋 room の横穴（仮想座標）：奥（ロッカーの間）・入口（張り出し）の立ち位置と、横穴の床の y。なければ null。 */
    public record AlcoveSpot(Vec3 back, Vec3 mouth, double floorY) {}

    public static AlcoveSpot alcoveSpot(ServerLevel level, Shaft s, int room) {
        Alcove a = alcoveFor(level, s, room);
        if (a == null) return null;
        int fy = s.segBottom(1) + a.off();
        int off = AbyssSpace.roomOffset(s, room);
        Vec3 back = Vec3.atBottomCenterOf(rot(s, a.side(), 0, fy + 1, -IN - 2)).subtract(0, off, 0);
        Vec3 mouth = Vec3.atBottomCenterOf(rot(s, a.side(), 0, fy + 1, -IN)).subtract(0, off, 0);
        return new AlcoveSpot(back, mouth, fy + 1 - off);
    }

    private static long mix(long seed, long v) {
        long z = seed ^ (v * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 層 k の横穴 a の中（縦穴の列は含まない：床〜天井、奥行き 2）の範囲。 */
    private static net.minecraft.world.phys.AABB alcoveInterior(Shaft s, int k, Alcove a) {
        int fy = s.segBottom(k) + a.off();
        BoundingBox b = BoundingBox.fromCorners(rot(s, a.side(), -1, fy, -IN - 1), rot(s, a.side(), 1, fy + 4, -IN - 2));
        return new net.minecraft.world.phys.AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** プレイヤーの体が今見えているどれかの横穴の中にかかっているか（横穴に立っている・入りかけ）。見た目だけの横穴も含む。 */
    public static boolean inAnyAlcove(Shaft s, net.minecraft.world.entity.player.Player p) {
        net.minecraft.world.phys.AABB bb = p.getBoundingBox().inflate(0.05);
        for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++) if (s.shown[k + EXTRA_ABOVE] != null && alcoveInterior(s, k, s.shown[k + EXTRA_ABOVE]).intersects(bb)) return true;
        return false;
    }

    /**
     * 横穴の中にプレイヤーがいる（隠れている）か。いれば、その層は作り直さない。
     * 縦穴の列（壁沿いに落ちているだけ）は含めない：通りかかっただけで層の更新が止まらないように。
     */
    private static boolean occupied(ServerLevel level, Shaft s, int k, Alcove a) {
        if (a == null) return false;
        net.minecraft.world.phys.AABB box = alcoveInterior(s, k, a);
        for (ServerPlayer p : level.players()) {
            if (box.intersects(p.getBoundingBox().deflate(0.01))) return true;
            BlockPos h = LockerManager.getSpotPos(p);
            if (h != null && box.contains(Vec3.atCenterOf(h))) return true;
        }
        return false;
    }

    /**
     * 縦穴の落ちる列（内側 5x5、小部屋の床の下〜底の上）に当たり判定のあるブロックがあれば空気にする（松明・看板は残る）。
     * 何があっても落下の途中で止まらないように、refresh のたびと見張り（{@link #onPlayerTick}）から呼ぶ。
     */
    private static int clearColumn(ServerLevel level, Shaft s, List<BlockPos> changed) {
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = s.colTop(); y > s.bottomY; y--)
            for (int x = CX - IN; x <= CX + IN; x++) for (int z = CZ - IN; z <= CZ + IN; z++) {
                m.set(s.ox + x, y, s.oz + z);
                if (s.squeeze && s.squeezeCells.contains(m)) continue; // 狭めている間の壁は残す
                BlockState st = level.getBlockState(m);
                if (st.isAir() || st.getCollisionShape(level, m).isEmpty()) continue;
                BlockPos q = m.immutable();
                level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
                if (changed != null) changed.add(q);
                n++;
            }
        return n;
    }

    private static void sendBlocks(ServerLevel level, Shaft s, List<BlockPos> changed) {
        if (changed.isEmpty()) return;
        Vec3 c = Vec3.atCenterOf(s.at(CX, s.segTop(1), CZ));
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) > 96 * 96) continue;
            for (BlockPos q : changed) p.connection.send(new ClientboundBlockUpdatePacket(level, q));
        }
    }

    /** 層 k を部屋 room の中身にする（横穴に人がいれば作り直さない）。作り直したら true。 */
    private static boolean updateSegment(ServerLevel level, RoomsState rs, Shaft s, int k, int room, boolean force, List<BlockPos> changed, List<BlockPos> signs) {
        Alcove want = shownFor(level, s, room), have = s.shown[k + EXTRA_ABOVE];
        if (!force && occupied(level, s, k, have)) { RoomsDebug.log("abyss segment {} kept (alcove occupied) room={}", k, s.shownRoom[k + EXTRA_ABOVE]); return false; }
        if (force) segmentShell(level, s, k, changed);
        else if (have != null && !have.equals(want)) alcove(level, s, k, have, true, changed);
        if (want != null && (force || !want.equals(have))) alcove(level, s, k, want, false, changed);
        if (force || s.shownRoom[k + EXTRA_ABOVE] != room) sign(level, rs, s, k, room, changed, signs);
        s.shown[k + EXTRA_ABOVE] = want;
        s.shownRoom[k + EXTRA_ABOVE] = room;
        return true;
    }

    /**
     * 層 k の中身を「部屋 lead + (k - 1)」に合わせる。差分だけブロックを置き換え、置き換えたブロックはすぐ近くのプレイヤーへ送る
     * （この後のテレポートより先に届くように）。
     */
    static void refresh(ServerLevel level, RoomsState rs, int lead) {
        Shaft s = rs.abyss;
        if (s == null) return;
        List<BlockPos> changed = new ArrayList<>(), signs = new ArrayList<>();
        DropGuard.begin();
        try {
            boolean force = !s.built;
            if (force) { java.util.Arrays.fill(s.shown, null); java.util.Arrays.fill(s.shownRoom, Integer.MIN_VALUE); }
            // 最初の部屋より先へ進んだら、小部屋をなくして上にも層を足す（上へは戻れない：てっぺんはふた。すぐ上がふさがって見えないように）
            boolean seal = lead > s.entryRoom;
            if (seal != s.sealed || force) {
                entryOrTower(level, s, seal, changed);
                s.sealed = seal;
                for (int k = -EXTRA_ABOVE; k < 0; k++) { s.shown[k + EXTRA_ABOVE] = null; s.shownRoom[k + EXTRA_ABOVE] = Integer.MIN_VALUE; }
            }
            for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++) {
                if (k < 0 && !s.sealed) continue;
                int room = lead + k - 1;
                if (!force && s.shownRoom[k + EXTRA_ABOVE] == room && java.util.Objects.equals(s.shown[k + EXTRA_ABOVE], shownFor(level, s, room))) continue;
                updateSegment(level, rs, s, k, room, force, changed, signs);
            }
            s.built = true;
            s.lead = lead;
            int cleared = clearColumn(level, s, changed);
            if (cleared > 0) RoomsDebug.log("abyss refresh cleared {} solid blocks from the fall column", cleared);
            if (s.squeeze) reconcileSqueeze(level, s, changed);
        } finally { DropGuard.end("abyss refresh"); }
        if (changed.isEmpty() && signs.isEmpty()) return;
        Vec3 c = Vec3.atCenterOf(s.at(CX, s.segTop(1), CZ));
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(c) > 96 * 96) continue;
            for (BlockPos q : changed) p.connection.send(new ClientboundBlockUpdatePacket(level, q));
            for (BlockPos q : signs) if (level.getBlockEntity(q) instanceof SignBlockEntity be) p.connection.send(ClientboundBlockEntityDataPacket.create(be));
        }
        RoomsDebug.log("abyss refresh lead={} ({}) blocks={} signs={}", lead, Sections.formatRoom(rs, lead), changed.size(), signs.size());
    }

    /** SafeSpot.find から：A の部屋のワープ先（最初の部屋は小部屋の床、以降は層1の上端で落ち始める）。A でなければ null。 */
    public static Vec3 spotFor(RoomsState rs, int room) {
        Shaft s = rs.abyss;
        if (s == null || Sections.forRoom(rs, room) != Sections.A || room < s.entryRoom) return null;
        if (room == s.entryRoom && !s.sealed) return new Vec3(s.ox + CX + 0.5, s.oy + 1, s.oz + 1.5);
        return safeColumnPoint(s, s.ox + CX + 0.5, s.segTop(1) - 1, s.oz + CZ + 0.5, 0.35);
    }


    // ===================== 敵が使う仕掛け（見た目だけの横穴・縦穴を狭める・引き止め） =====================

    /** 部屋 room の層を今見えている層から探して作り直す（見た目だけの横穴を足した・消したとき）。 */
    private static void refreshRoom(ServerLevel level, RoomsState rs, Shaft s, int room) {
        List<BlockPos> changed = new ArrayList<>(), signs = new ArrayList<>();
        DropGuard.begin();
        try {
            for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++)
                if (s.built && s.shownRoom[k + EXTRA_ABOVE] == room && !java.util.Objects.equals(s.shown[k + EXTRA_ABOVE], shownFor(level, s, room)))
                    updateSegment(level, rs, s, k, room, false, changed, signs);
            if (s.squeeze) reconcileSqueeze(level, s, changed);
        } finally { DropGuard.end("abyss fake alcove"); }
        sendBlocks(level, s, changed);
    }

    /**
     * 本物の横穴のない部屋 room に見た目だけの横穴を置く（null で消す）。本物の横穴がある部屋には置かない。
     * 敵の出現条件（{@link AbyssSpace#alcoveWithin}・{@link #alcoveSpot}）は本物の横穴だけを数えるので、隠れ場所の数は減らない。
     */
    public static boolean setFake(ServerLevel level, RoomsState rs, int room, Direction side, int off) {
        Shaft s = rs.abyss;
        if (s == null || alcoveFor(level, s, room) != null) return false;
        Alcove want = side == null ? null : new Alcove(side, Math.max(ALCOVE_OFF_MIN, Math.min(ALCOVE_OFF_MAX, off)), true);
        Alcove had = want == null ? s.fakes.remove(room) : s.fakes.put(room, want);
        if (!java.util.Objects.equals(had, want)) refreshRoom(level, rs, s, room);
        return true;
    }

    public static boolean hasFake(RoomsState rs, int room) { return rs.abyss != null && rs.abyss.fakes.containsKey(room); }

    /** 部屋 room の見た目だけの横穴の床の高さ（仮想座標）と奥・入口。なければ null。 */
    public static AlcoveSpot fakeSpot(RoomsState rs, int room) {
        Shaft s = rs.abyss;
        Alcove a = s == null ? null : s.fakes.get(room);
        if (a == null) return null;
        int fy = s.segBottom(1) + a.off();
        int off = AbyssSpace.roomOffset(s, room);
        Vec3 back = Vec3.atBottomCenterOf(rot(s, a.side(), 0, fy + 1, -IN - 2)).subtract(0, off, 0);
        Vec3 mouth = Vec3.atBottomCenterOf(rot(s, a.side(), 0, fy + 1, -IN)).subtract(0, off, 0);
        return new AlcoveSpot(back, mouth, fy + 1 - off);
    }

    /** 部屋 room の見た目だけの横穴が今ブロックとして見えていれば、その中の範囲（実際の座標）。 */
    public static net.minecraft.world.phys.AABB fakeInterior(RoomsState rs, int room) {
        Shaft s = rs.abyss;
        if (s == null) return null;
        for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++)
            if (s.shownRoom[k + EXTRA_ABOVE] == room && s.shown[k + EXTRA_ABOVE] != null && s.shown[k + EXTRA_ABOVE].fake()) return alcoveInterior(s, k, s.shown[k + EXTRA_ABOVE]);
        return null;
    }

    /** プレイヤーの体の中心（足元から胸まで）が見えているどれかの横穴の中（inflate だけ縦穴側へ甘く）にあるか。見た目だけの横穴も含む。 */
    public static boolean centerInAlcove(Shaft s, net.minecraft.world.entity.player.Player p, double inflate) {
        Vec3 c = p.position().add(0, 0.5, 0);
        for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++) if (s.shown[k + EXTRA_ABOVE] != null && alcoveInterior(s, k, s.shown[k + EXTRA_ABOVE]).inflate(inflate).contains(c)) return true;
        return false;
    }


    /** 見えている横穴の入口（外周の列のうち、横穴の床〜天井の高さ）。狭めてもふさがない。 */
    private static java.util.Set<BlockPos> alcoveMouths(Shaft s) {
        java.util.Set<BlockPos> out = new java.util.HashSet<>();
        for (int k = -EXTRA_ABOVE; k < SEGMENTS; k++) {
            Alcove a = s.shown[k + EXTRA_ABOVE];
            if (a == null) continue;
            int fy = s.segBottom(k) + a.off();
            for (int dx = -1; dx <= 1; dx++) for (int y = 0; y <= 3; y++) out.add(rot(s, a.side(), dx, fy + y, -IN));
        }
        return out;
    }

    /** 狭めている間、縦穴の内側のマス (x, z)（小部屋の origin からのローカル座標）が壁になるか（横穴の入口は別に除く）。 */
    private static boolean closing(Shaft s, int x, int z) {
        return s.squeeze && Math.abs(x - CX) <= IN && Math.abs(z - CZ) <= IN && !AbyssSqueeze.open(s.squeezeOpen, x - CX, z - CZ);
    }

    /**
     * 狭めている間：形（{@link AbyssSqueeze}）で残すマス以外を壁にする。横穴の入口・松明・看板・プレイヤーの体にかかるマスには置かない（埋まらない）。
     * 残すマスに壁が残っていれば空ける（落ちる道は上から下まで途切れない）。refresh（1 周ごと）と敵の tick から呼ぶ。
     */
    private static void reconcileSqueeze(ServerLevel level, Shaft s, List<BlockPos> changed) {
        java.util.Set<BlockPos> mouths = alcoveMouths(s);
        List<net.minecraft.world.phys.AABB> bodies = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && s.contains(p.position())) bodies.add(p.getBoundingBox().inflate(0.1));
        BlockState w = wall(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = s.colTop(); y > s.bottomY; y--)
            for (int x = CX - IN; x <= CX + IN; x++) for (int z = CZ - IN; z <= CZ + IN; z++) {
                m.set(s.ox + x, y, s.oz + z);
                boolean placed = s.squeezeCells.contains(m);
                boolean cell = closing(s, x, z);
                boolean want = cell && !mouths.contains(m);
                if (want) for (var b : bodies) if (b.intersects(m.getX(), m.getY(), m.getZ(), m.getX() + 1, m.getY() + 1, m.getZ() + 1)) { want = false; break; }
                if (want && !placed) {
                    BlockState cur = level.getBlockState(m);
                    if (cur == w) { s.squeezeCells.add(m.immutable()); continue; } // 読み込み前に置いた壁（縦穴の内側に壁があるのは狭めた時だけ）
                    if (!cur.isAir()) continue; // 松明・看板は残す
                    BlockPos q = m.immutable();
                    set(level, q, w, changed);
                    s.squeezeCells.add(q);
                } else if (!want && placed) {
                    BlockPos q = m.immutable();
                    if (level.getBlockState(q) == w) set(level, q, air, changed);
                    s.squeezeCells.remove(q);
                } else if (!cell && !placed && level.getBlockState(m) == w) {
                    set(level, m.immutable(), air, changed); // 残すマスの古い壁（読み込み前のもの）
                }
            }
    }

    /** 縦穴を狭める（openMask：残すマス、{@link AbyssSqueeze}）。 */
    public static void setSqueeze(ServerLevel level, RoomsState rs, int openMask) {
        Shaft s = rs.abyss;
        if (s == null) return;
        List<BlockPos> changed = new ArrayList<>();
        DropGuard.begin();
        try { s.squeeze = true; s.squeezeOpen = openMask; reconcileSqueeze(level, s, changed); }
        finally { DropGuard.end("abyss squeeze"); }
        sendBlocks(level, s, changed);
        RoomsDebug.log("abyss squeeze on mask={} blocks={}", Integer.toBinaryString(openMask), changed.size());
    }

    /** 元に戻す：置いた壁だけを空気に戻す。 */
    public static void clearSqueeze(ServerLevel level, RoomsState rs) {
        Shaft s = rs.abyss;
        if (s == null) return;
        List<BlockPos> changed = new ArrayList<>();
        DropGuard.begin();
        try {
            BlockState w = wall(), air = Blocks.AIR.defaultBlockState();
            for (BlockPos q : s.squeezeCells) if (level.getBlockState(q) == w) set(level, q, air, changed);
            s.squeezeCells.clear();
            s.squeeze = false;
        } finally { DropGuard.end("abyss squeeze"); }
        sendBlocks(level, s, changed);
        RoomsDebug.log("abyss squeeze off blocks={}", changed.size());
    }

    /** 狭めている間に毎 tick（プレイヤーが空けたマスを埋め、横穴の入口を空ける）。 */
    public static void tickSqueeze(ServerLevel level, RoomsState rs) {
        Shaft s = rs.abyss;
        if (s == null || !s.squeeze) return;
        List<BlockPos> changed = new ArrayList<>();
        DropGuard.begin();
        try { reconcileSqueeze(level, s, changed); } finally { DropGuard.end("abyss squeeze"); }
        sendBlocks(level, s, changed);
    }

    public static boolean squeezed(RoomsState rs) { return rs.abyss != null && rs.abyss.squeeze; }

    /**
     * 形 openMask で狭めたとき、プレイヤーの体が壁になるマス（横穴の入口を除く）に横に tolerance より深く入っているか（横穴の中にかかっていれば除く）。
     */
    public static boolean inSqueezeZone(Shaft s, int openMask, net.minecraft.world.entity.player.Player p, double tolerance) {
        if (inAnyAlcove(s, p)) return false;
        net.minecraft.world.phys.AABB bb = p.getBoundingBox().deflate(tolerance, 0, tolerance);
        java.util.Set<BlockPos> mouths = alcoveMouths(s);
        int x0 = (int) Math.floor(bb.minX), x1 = (int) Math.floor(bb.maxX - 1.0E-7), y0 = (int) Math.floor(bb.minY), y1 = (int) Math.floor(bb.maxY - 1.0E-7);
        int z0 = (int) Math.floor(bb.minZ), z1 = (int) Math.floor(bb.maxZ - 1.0E-7);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int lx = x - s.ox, lz = z - s.oz;
            if (Math.abs(lx - CX) > IN || Math.abs(lz - CZ) > IN || AbyssSqueeze.open(openMask, lx - CX, lz - CZ)) continue;
            for (int y = y0; y <= y1; y++) if (!mouths.contains(new BlockPos(x, y, z))) return true;
        }
        return false;
    }

    /**
     * 縦穴の中の (x, z) に一番近い、落ちる道の上の安全な位置（狭めていなければそのまま（中心寄せは呼び出し側）、狭めていれば残るマスに体が収まる位置）。
     * 見張り・噛みつきの吐き出し・ワープなど、縦穴の中へプレイヤーを置くときに使う（壁の中に置かない）。
     */
    public static Vec3 safeColumnPoint(Shaft s, double x, double y, double z, double halfWidth) {
        double cx = s.ox + CX + 0.5, cz = s.oz + CZ + 0.5;
        if (!s.squeeze) return new Vec3(x, y, z);
        double[] q = AbyssSqueeze.nearestFit(s.squeezeOpen, x - cx, z - cz, halfWidth);
        return q == null ? new Vec3(x, y, z) : new Vec3(cx + q[0], y, cz + q[1]);
    }

    /** プレイヤーを狭めたあとも残るマスへ寄せる（一番近い位置、高さはそのまま、下向きの速さ）。 */
    public static void pushToOpen(ServerPlayer sp, Shaft s, int openMask, double margin) {
        double cx = s.ox + CX + 0.5, cz = s.oz + CZ + 0.5;
        double[] q = AbyssSqueeze.nearestFit(openMask, sp.getX() - cx, sp.getZ() - cz, sp.getBbWidth() / 2 + margin);
        if (q == null) return;
        sp.connection.teleport(cx + q[0], sp.getY(), cz + q[1], sp.getYRot(), sp.getXRot());
        sp.setDeltaMovement(0, -MAX_FALL, 0);
        sp.fallDistance = 0;
        LAST.put(sp.getUUID(), sp.position());
    }

    /** サーバー → クライアント：引き止め（on の間、縦の速さを vy に固定。横は自由）。 */
    public record HoldPayload(boolean on, float vy) implements CustomPacketPayload {
        public static final Type<HoldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "abyss_hold"));
        public static final StreamCodec<ByteBuf, HoldPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, HoldPayload::on, ByteBufCodecs.FLOAT, HoldPayload::vy, HoldPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側：引き止められている間の縦の速さ（NaN = なし）。 */
    public static volatile float clientHoldVy = Float.NaN;

    /** 引き止め中のプレイヤー → 期限（ゲーム時間）。期限が切れたら自動で放す（敵が消えても取り残さない）。 */
    private static final Map<UUID, Long> HELD = new HashMap<>();
    private static final Map<UUID, Float> HELD_VY = new HashMap<>();

    /** 引き止める（vy：縦の速さ、ticks：この tick 数だけ有効。毎 tick 呼び直して延ばす）。見張り（止まっている判定）は引き止め中は数えない。 */
    public static void hold(ServerPlayer sp, float vy, int ticks) {
        UUID id = sp.getUUID();
        HELD.put(id, sp.serverLevel().getGameTime() + ticks);
        Float prev = HELD_VY.put(id, vy);
        if (prev == null || Math.abs(prev - vy) > 1.0E-4f) PacketDistributor.sendToPlayer(sp, new HoldPayload(true, vy));
        sp.fallDistance = 0;
        STILL.remove(id);
    }

    public static void release(ServerPlayer sp) {
        UUID id = sp.getUUID();
        HELD.remove(id);
        if (HELD_VY.remove(id) != null) PacketDistributor.sendToPlayer(sp, new HoldPayload(false, 0f));
        STILL.remove(id);
        LAST.put(id, sp.position());
    }

    public static boolean isHeld(net.minecraft.world.entity.player.Player p) { return HELD.containsKey(p.getUUID()); }

    /** 引き止めで上がってよい足の高さの上限（実際の座標。小部屋の床・天井に頭をぶつけない）。 */
    public static double holdCeiling(RoomsState rs) { return rs.abyss == null ? Double.MAX_VALUE : Math.min(rs.abyss.colTop() - 3.0, rs.abyss.oy - 4.0); }

    // ===================== 落下・1周 =====================

    /** プレイヤーごとの今の部屋（縦穴の中にいる間だけ）、周回数、前の tick の位置。 */
    private static final Map<UUID, Integer> ROOM = new HashMap<>();
    private static final Map<UUID, Integer> LOOPS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final java.util.Set<UUID> ACTIVE = new java.util.HashSet<>();
    /** 縦穴の列の中で落ちていない（止まっている）tick 数。 */
    private static final Map<UUID, Integer> STILL = new HashMap<>();
    /** 見張り：この tick 数止まっていたら列を片付けて中心へ寄せる、さらにこの tick 数で強制的に 1 周。 */
    static final int STALL_CLEAR_TICKS = 40, STALL_LOOP_TICKS = 100;

    /** サーバー → クライアント：縦穴の中にいるか（落下速度の上限）。 */
    public record AbyssPayload(boolean active) implements CustomPacketPayload {
        public static final Type<AbyssPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RoomsMod.MOD_ID, "abyss"));
        public static final StreamCodec<ByteBuf, AbyssPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, AbyssPayload::active, AbyssPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** クライアント側で受け取った状態（クライアント専用クラスに依存しない）。 */
    public static volatile boolean clientActive = false;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(AbyssPayload.TYPE, AbyssPayload.STREAM_CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> clientActive = msg.active()))
                .playToClient(AbyssSpace.ShiftPayload.TYPE, AbyssSpace.ShiftPayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> AbyssSpace.clientShift.accept(msg)))
                .playToClient(HoldPayload.TYPE, HoldPayload.STREAM_CODEC,
                        (msg, ctx) -> ctx.enqueueWork(() -> clientHoldVy = msg.on() ? msg.vy() : Float.NaN)));
    }

    /** プレイヤーの今の部屋（縦穴の中）。わからなければ fallback。 */
    public static int roomOf(net.minecraft.world.entity.player.Player p, int fallback) {
        Integer r = ROOM.get(p.getUUID());
        return r != null ? r : fallback;
    }

    private static void setActive(ServerPlayer p, boolean on) {
        if (on ? ACTIVE.add(p.getUUID()) : ACTIVE.remove(p.getUUID())) PacketDistributor.sendToPlayer(p, new AbyssPayload(on));
    }

    private static Shaft shaftFor(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel sl) || !sl.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return null;
        return RoomsState.get(sl).abyss;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Shaft s = shaftFor(sp);
        UUID id = sp.getUUID();
        Vec3 pos = sp.position();
        if (s == null || !s.contains(pos) || sp.isSpectator()) {
            setActive(sp, false);
            if (HELD.containsKey(id)) release(sp);
            ROOM.remove(id); LAST.remove(id); STILL.remove(id);
            return;
        }
        Long heldUntil = HELD.get(id);
        if (heldUntil != null && sp.serverLevel().getGameTime() > heldUntil) { RoomsDebug.log("abyss hold expired player={}", sp.getGameProfile().getName()); release(sp); }
        setActive(sp, true);
        sp.fallDistance = 0;
        ServerLevel level = sp.serverLevel();
        RoomsState rs = RoomsState.get(level);
        // 今の部屋：小部屋から入ったら最初の部屋、ワープ等で縦穴へ直接来たら（または大きく動いたら）先頭の部屋
        Vec3 last = LAST.put(id, pos);
        Integer cur = ROOM.get(id);
        if (cur == null || last == null || last.distanceToSqr(pos) > 8 * 8) {
            cur = pos.y >= s.oy && !s.sealed ? s.entryRoom : Math.max(s.entryRoom, rs.currentRoom);
            ROOM.put(id, cur);
        }
        if (watchStall(level, rs, s, sp, last, cur)) return;
        if (!s.inShaft(pos) || pos.y >= s.loopY() || LockerManager.getSpot(sp) != null) return;
        loop(level, rs, s, sp, cur);
    }

    /** 縦穴の落ちる列の中（横穴の中は含まない）か。 */
    private static boolean inFallColumn(Shaft s, Vec3 p) {
        double cx = s.ox + CX + 0.5, cz = s.oz + CZ + 0.5, r = IN + 0.5;
        return s.inShaft(p) && Math.abs(p.x - cx) <= r && Math.abs(p.z - cz) <= r;
    }

    /**
     * 見張り：縦穴の列の中で（横穴・ロッカー以外で）2 秒落ちていなければ、列の中の当たり判定のあるブロックを消して中心へ寄せる。
     * それでも 5 秒止まったままなら強制的に 1 周させて層1の上端から落とし直す。処理したら true。
     */
    private static boolean watchStall(ServerLevel level, RoomsState rs, Shaft s, ServerPlayer sp, Vec3 last, int cur) {
        UUID id = sp.getUUID();
        Vec3 pos = sp.position();
        boolean candidate = inFallColumn(s, pos) && LockerManager.getSpot(sp) == null && !sp.getAbilities().flying
                && !sp.isPassenger() && !inAnyAlcove(s, sp) && !HELD.containsKey(id); // 引き止め中（A-50）は止まっていて正常
        boolean falling = last != null && pos.y < last.y - 0.02;
        if (!candidate || falling) { STILL.remove(id); return false; }
        int t = STILL.merge(id, 1, Integer::sum);
        if (t == STALL_CLEAR_TICKS) {
            List<BlockPos> changed = new ArrayList<>();
            int n;
            DropGuard.begin();
            try { n = clearColumn(level, s, changed); } finally { DropGuard.end("abyss stall clear"); }
            sendBlocks(level, s, changed);
            Vec3 c = safeColumnPoint(s, s.ox + CX + 0.5, pos.y, s.oz + CZ + 0.5, sp.getBbWidth() / 2 + 0.05); // 狭めている間は残るマスへ
            sp.connection.teleport(c.x, c.y, c.z, sp.getYRot(), sp.getXRot());
            sp.setDeltaMovement(0, -MAX_FALL, 0);
            sp.fallDistance = 0;
            LAST.put(id, sp.position());
            RoomsDebug.log("abyss stall player={} room={} y={} onGround={} -> cleared {} blocks, centered",
                    sp.getGameProfile().getName(), cur, String.format("%.1f", pos.y), sp.onGround(), n);
            return true;
        }
        if (t >= STALL_LOOP_TICKS) {
            STILL.remove(id);
            RoomsDebug.log("abyss stall player={} room={} y={} still stuck -> forced loop", sp.getGameProfile().getName(), cur, String.format("%.1f", pos.y));
            loop(level, rs, s, sp, cur);
            if (sp.level() == level && s.contains(sp.position())) {
                Vec3 c = safeColumnPoint(s, s.ox + CX + 0.5, s.segTop(1) - 1, s.oz + CZ + 0.5, sp.getBbWidth() / 2 + 0.05);
                sp.connection.teleport(c.x, c.y, c.z, sp.getYRot(), sp.getXRot());
                sp.setDeltaMovement(0, -MAX_FALL, 0);
                sp.fallDistance = 0;
                LAST.put(id, sp.position());
            }
            return true;
        }
        return false;
    }

    private static void loop(ServerLevel level, RoomsState rs, Shaft s, ServerPlayer sp, int cur) {
        UUID id = sp.getUUID();
        int next = cur + 1;
        boolean generated = false;
        if (next > rs.currentRoom) {
            if (Sections.forRoom(rs, next) != Sections.A) { exitTo(level, rs, s, sp, next); return; }
            RoomGenerator.generateNextRoom(level, s.at(CX, s.oy, Z1 + 6), next); // 層の中身をずらす（ブロックはテレポートより先に送る）
            generated = true;
        }
        // 位置・向き・速度はそのまま、L ブロック上へ（相対テレポート）
        sp.connection.teleport(sp.getX(), sp.getY() + L, sp.getZ(), sp.getYRot(), sp.getXRot(), EnumSet.allOf(RelativeMovement.class));
        sp.fallDistance = 0;
        LAST.put(id, sp.position());
        ROOM.put(id, next);
        if (generated) AbyssSpace.afterLoop(level, rs, s); // 敵（とパーティクル）も同じだけ上へ：位置関係を保つ
        int loops = LOOPS.merge(id, 1, Integer::sum);
        if (RoomsDebug.isEnabled()) {
            int depth = (next - s.entryRoom) * L + (s.oy - (int) Math.floor(sp.getY()));
            String fmt = "abyss loop player={} loops={} room={} ({}) depth={} blocks y={} generated={}";
            if (loops % 10 == 0) RoomsDebug.log(sp, "A: " + loops + " 周目 " + Sections.formatRoom(rs, next) + " 深さ " + depth, fmt,
                    sp.getGameProfile().getName(), loops, next, Sections.formatRoom(rs, next), depth, String.format("%.1f", sp.getY()), generated);
            else RoomsDebug.log(fmt, sp.getGameProfile().getName(), loops, next, Sections.formatRoom(rs, next), depth, String.format("%.1f", sp.getY()), generated);
        }
        if (generated) com.example.roomsmod.event.DoorEventHandler.onRoomAdvanced(level, rs, sp, next);
    }

    /** A の次が別のセクションになった（/rooms section ... で変えた）：縦穴の横に普通の部屋を作ってそこへ送る。 */
    private static void exitTo(ServerLevel level, RoomsState rs, Shaft s, ServerPlayer sp, int next) {
        BlockPos origin = s.at(0, s.oy, Z1 + 6);
        try { RoomGenerator.generateNextRoom(level, origin, next); }
        catch (RuntimeException e) { RoomsDebug.error("abyss exit room generation failed (room=" + next + ")", e); return; }
        Vec3 v = SafeSpot.find(level, rs, next);
        if (v == null) v = Vec3.atBottomCenterOf(origin.offset(CX, 1, 2));
        sp.teleportTo(level, v.x, v.y, v.z, sp.getYRot(), sp.getXRot());
        sp.setDeltaMovement(Vec3.ZERO);
        sp.fallDistance = 0;
        ROOM.remove(sp.getUUID()); LAST.remove(sp.getUUID());
        RoomsDebug.log(sp, "縦穴を抜けたわ（" + Sections.formatRoom(rs, next) + "）", "abyss exit player={} -> room {} ({}) at {}", sp.getGameProfile().getName(), next, Sections.formatRoom(rs, next), v);
        com.example.roomsmod.event.DoorEventHandler.onRoomAdvanced(level, rs, sp, next);
    }

    /** 縦穴の中では落下ダメージなし。 */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Shaft s = shaftFor(sp);
        if (s != null && s.contains(sp.position())) { event.setDistance(0); event.setCanceled(true); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ROOM.remove(id); LOOPS.remove(id); LAST.remove(id); ACTIVE.remove(id); STILL.remove(id); HELD.remove(id); HELD_VY.remove(id);
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) { setActive(sp, false); if (HELD.containsKey(sp.getUUID())) release(sp); }
    }
}
