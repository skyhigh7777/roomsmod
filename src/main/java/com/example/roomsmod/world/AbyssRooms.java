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
 *   <li>横穴（ロッカー2つ）は 2〜3 部屋ごと。落ちながら横へ動いて入る。最初の 1 周で小部屋の穴をふさぐ（上へは戻れない）。</li>
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
    /** 最下層の下の余白（ここは常に空、底は黒い床）。 */
    static final int BOTTOM_GAP = 4;
    /** 落下速度の上限（ブロック/tick）。12 ブロック/秒。スニーク中はこの半分。 */
    public static final double MAX_FALL = 0.6, MAX_FALL_SNEAK = 0.3;

    // ローカル座標（小部屋の origin から）。縦穴の内側 x 8..12, z 3..7、壁は 3 ブロック厚（x 5..15, z 0..10）
    static final int CX = 10, CZ = 5, IN = 2, X0 = 5, X1 = 15, Z0 = 0, Z1 = 10;
    /** 看板と照明の層内の高さ（層の底から）。 */
    static final int SIGN_OFF = 9, TORCH_OFF = 3;
    /** 横穴の床の層内の高さの範囲（底から）。床+4 が天井で層内に収まる。 */
    static final int ALCOVE_OFF_MIN = 1, ALCOVE_OFF_MAX = L - 5;

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** 縦穴1本の位置（RoomsState に保存）。shown は今ブロックとして置いてある各層の中身（保存しない＝読み込み後は全部作り直す）。 */
    public static final class Shaft {
        public final int ox, oy, oz, entryRoom, bottomY;
        final Alcove[] shown = new Alcove[SEGMENTS];
        final int[] shownRoom = new int[SEGMENTS];
        boolean built = false, sealed = false;

        Shaft(int ox, int oy, int oz, int entryRoom, int bottomY) { this.ox = ox; this.oy = oy; this.oz = oz; this.entryRoom = entryRoom; this.bottomY = bottomY; }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("x", ox); t.putInt("y", oy); t.putInt("z", oz); t.putInt("entry", entryRoom); t.putInt("bottom", bottomY);
            return t;
        }

        public static Shaft load(CompoundTag t) { return new Shaft(t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("entry"), t.getInt("bottom")); }

        BlockPos at(int x, int y, int z) { return new BlockPos(ox + x, y, oz + z); }
        /** 層 k のいちばん上のブロックの y。 */
        int segTop(int k) { return oy - 1 - k * L; }
        int segBottom(int k) { return segTop(k) - L + 1; }
        /** 足がこの y より下（層2）に入ったら 1 周。 */
        int loopY() { return segBottom(1); }

        /** 縦穴（と小部屋）の水平範囲・高さの範囲に入っているか。 */
        boolean contains(Vec3 p) {
            return p.x >= ox + X0 && p.x < ox + X1 + 1 && p.z >= oz + Z0 && p.z < oz + Z1 + 1 && p.y >= bottomY && p.y < oy + 6;
        }
        /** 縦穴の中（小部屋の床より下）か。 */
        boolean inShaft(Vec3 p) { return contains(p) && p.y < oy; }

        @Override public String toString() { return "Shaft[o=(" + ox + "," + oy + "," + oz + ") entry=" + entryRoom + " bottom=" + bottomY + "]"; }
    }

    /** 横穴（side の壁、床の高さは層の底から off）。 */
    record Alcove(Direction side, int off) {}

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
            BlockState w = wall(), f = floor(), air = Blocks.AIR.defaultBlockState();
            // 小部屋：床 y=oy（穴あき）、壁、天井 oy+5、入口 (10, oy+1..2, 0)
            for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
                boolean hole = Math.abs(x - CX) <= IN && Math.abs(z - CZ) <= IN;
                set(level, s.at(x, s.oy, z), hole ? air : f, null);
                boolean edge = x == X0 || x == X1 || z == Z0 || z == Z1;
                for (int y = 1; y <= 4; y++) set(level, s.at(x, s.oy + y, z), edge ? w : air, null);
                set(level, s.at(x, s.oy + 5, z), w, null);
            }
            set(level, s.at(CX, s.oy + 1, 0), air, null);
            set(level, s.at(CX, s.oy + 2, 0), air, null);
            set(level, s.at(CX, s.oy + 4, CZ - IN - 1 + 1), Sections.A.palette().light().get(), null);
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
        // 張り出し（縦穴の内側の壁沿い 1 列、着地しやすく）
        for (int dx = -1; dx <= 1; dx++) set(level, rot(s, a.side(), dx, fy, -IN), remove ? air : floor(), changed);
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
        set(level, rot(s, a.side(), 0, fy + 3, -IN - 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), changed);
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
        return new Alcove(SIDES[r.nextInt(4)], ALCOVE_OFF_MIN + r.nextInt(ALCOVE_OFF_MAX - ALCOVE_OFF_MIN + 1));
    }

    private static long mix(long seed, long v) {
        long z = seed ^ (v * 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 横穴の範囲にプレイヤーがいる（隠れている）か。いれば、その層は作り直さない。 */
    private static boolean occupied(ServerLevel level, Shaft s, int k, Alcove a) {
        if (a == null) return false;
        int fy = s.segBottom(k) + a.off();
        BoundingBox box = BoundingBox.fromCorners(rot(s, a.side(), -1, fy, -IN), rot(s, a.side(), 1, fy + 4, -IN - 2));
        for (ServerPlayer p : level.players()) {
            if (box.isInside(p.blockPosition())) return true;
            BlockPos h = LockerManager.getSpotPos(p);
            if (h != null && box.isInside(h)) return true;
        }
        return false;
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
            for (int k = 0; k < SEGMENTS; k++) {
                int room = lead + k - 1;
                if (!force && s.shownRoom[k] == room) continue;
                Alcove want = alcoveFor(level, s, room), have = s.shown[k];
                if (!force && occupied(level, s, k, have)) { RoomsDebug.log("abyss segment {} kept (alcove occupied) room={}", k, s.shownRoom[k]); continue; }
                if (force) segmentShell(level, s, k, changed);
                else if (have != null && !have.equals(want)) alcove(level, s, k, have, true, changed);
                if (want != null && (force || !want.equals(have))) alcove(level, s, k, want, false, changed);
                sign(level, rs, s, k, room, changed, signs);
                s.shown[k] = want;
                s.shownRoom[k] = room;
            }
            s.built = true;
            // 最初の部屋より先へ進んだら、小部屋の穴をふさぐ（上へは戻れない）
            boolean seal = lead > s.entryRoom;
            if (seal != s.sealed || force) {
                BlockState st = seal ? wall() : Blocks.AIR.defaultBlockState();
                for (int x = CX - IN; x <= CX + IN; x++) for (int z = CZ - IN; z <= CZ + IN; z++) set(level, s.at(x, s.oy, z), st, changed);
                s.sealed = seal;
            }
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
        if (room == s.entryRoom) return new Vec3(s.ox + CX + 0.5, s.oy + 1, s.oz + 1.5);
        return new Vec3(s.ox + CX + 0.5, s.segTop(1) - 1, s.oz + CZ + 0.5);
    }

    // ===================== 落下・1周 =====================

    /** プレイヤーごとの今の部屋（縦穴の中にいる間だけ）、周回数、前の tick の位置。 */
    private static final Map<UUID, Integer> ROOM = new HashMap<>();
    private static final Map<UUID, Integer> LOOPS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();
    private static final java.util.Set<UUID> ACTIVE = new java.util.HashSet<>();

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
                (msg, ctx) -> ctx.enqueueWork(() -> clientActive = msg.active())));
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
            ROOM.remove(id); LAST.remove(id);
            return;
        }
        setActive(sp, true);
        sp.fallDistance = 0;
        ServerLevel level = sp.serverLevel();
        RoomsState rs = RoomsState.get(level);
        // 今の部屋：小部屋から入ったら最初の部屋、ワープ等で縦穴へ直接来たら（または大きく動いたら）先頭の部屋
        Vec3 last = LAST.put(id, pos);
        Integer cur = ROOM.get(id);
        if (cur == null || last == null || last.distanceToSqr(pos) > 8 * 8) {
            cur = pos.y >= s.oy ? s.entryRoom : Math.max(s.entryRoom, rs.currentRoom);
            ROOM.put(id, cur);
        }
        if (!s.inShaft(pos) || pos.y >= s.loopY() || LockerManager.getSpot(sp) != null) return;
        loop(level, rs, s, sp, cur);
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
        ROOM.remove(id); LOOPS.remove(id); LAST.remove(id); ACTIVE.remove(id);
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) setActive(sp, false);
    }
}
