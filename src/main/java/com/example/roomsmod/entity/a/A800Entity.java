package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.DropGuard;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A-800「落ちる床」：プレイヤーの下に、縦穴の内側 5x5 いっぱいの薄い床（ハーフブロック）が現れる。ひびの入ったタイルだけ抜けられる。姿はない。
 * <ul>
 *   <li>床：プレイヤーの足の {@value #DROP} ブロック（ハード {@value #DROP_HARD}）下に、ふつうのタイル（滑らかな石のハーフ、明るい灰色）と
 *       ひびのタイル（深層岩の丸石のハーフ、暗くでこぼこ）。ひびは必ず 2x2 のかたまりが 1 つ（縦穴のどこからでも届く）と、ばらばらに 0〜2 枚。
 *       置くときに石の割れる音。看板・松明の高さは避ける（その段の角のマスに穴が開かないように）。</li>
 *   <li>間に合うか：一番遠い角から反対側の 2x2 の上まで約 4.3 ブロック、空中を歩いて約 0.2 ブロック/tick → 反応 10 tick 込みで約 31 tick。
 *       全速で落ちて床まで 52 tick（ハード 44 tick）、スニークなら落ちる時間は倍（空中のスニークは遅いが、それでも間に合う）。</li>
 *   <li>ひびのタイルは、足が床の {@value #CRUMBLE} ブロック上まで来たら崩れて消える（割れる粒と音）。体がひびの上に収まっていればそのまま落ちて抜ける。</li>
 *   <li>ふつうのタイルに乗る（ぶつかる）と、ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、床は全部割れて消える（そのまま落ち続ける。挟まらない）。</li>
 *   <li>全部で {@value #FLOORS} 枚（ハード {@value #FLOORS_HARD}）、抜けてから {@value #REST} tick あけて次。床は仮想座標で持ち、1 周（テレポート）のたびに同じだけ上へ置き直す
 *       （1 周の refresh・見張りの clearColumn が消しても、ここで置き直す）。床が {@value #FLOOR_MAX} tick 残ったら（横穴で止まっている等）消して次へ。</li>
 *   <li>後片付け：置いたブロックだけ（同じ種類なら）空気に戻す：抜けた・当たった・時間切れ・消えるとき（死亡・縦穴を出た・リセット・ログアウト）。
 *       置いた位置は保存し、読み込み直したら最初の tick に片付けて消える。縦穴にブロックを残さない。</li>
 *   <li>A-200 / A-1000 などほかの A の敵とは同時に出ない（床で止まる・狭まる・隠れる・重力とぶつかる）。</li>
 * </ul>
 */
public class A800Entity extends AbyssEnemy {
    public static final int DROP = 13, DROP_HARD = 11, FLOORS = 3, FLOORS_HARD = 4, REST = 50, FLOOR_MAX = 240, WARN = 20;
    public static final double CRUMBLE = 2.5;
    public static final float DAMAGE = 5f, DAMAGE_HARD = 8f;
    private static final int FLAGS = 2 | 16;

    private static BlockState solid() { return Blocks.SMOOTH_STONE_SLAB.defaultBlockState(); }
    private static BlockState cracked() { return Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState(); }

    private enum State { WARN, FLOOR, REST, GONE }
    private State state = State.WARN;
    private int stateTicks = 0, floors = 0;
    /** 床の仮想の y（ブロック）。MIN_VALUE = なし。 */
    private int floorVY = Integer.MIN_VALUE;
    /** 5x5 のひびのマス（ビット (lx+2)*5+(lz+2)）。 */
    private int crackMask = 0;
    private boolean crumbled = false;
    /** 今置いているブロック（実際の位置 → 置いた状態）。 */
    private final java.util.Map<BlockPos, BlockState> placed = new java.util.HashMap<>();
    /** 読み込み直後に片付ける位置（保存していたもの）。 */
    private long[] leftover = null;

    public A800Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-800"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class,
                A300Entity.class, A350Entity.class, A450Entity.class, A525Entity.class, A600Entity.class, A666Entity.class, A750Entity.class, A1000Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A800Entity.class)) { RoomsDebug.log("A-800 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t) || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-800 prepare target={} floors={} drop={}", t.getGameProfile().getName(), hardSpawn ? FLOORS_HARD : FLOORS, hardSpawn ? DROP_HARD : DROP);
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    private static int bit(int lx, int lz) { return 1 << ((lx + 2) * 5 + (lz + 2)); }

    /** ひびの形：2x2 のかたまり 1 つ（必ず）と、ばらばらに 0〜2 枚。 */
    private int pickCracks() {
        int gx = -2 + random.nextInt(4), gz = -2 + random.nextInt(4);
        int m = bit(gx, gz) | bit(gx + 1, gz) | bit(gx, gz + 1) | bit(gx + 1, gz + 1);
        int extra = random.nextInt(3);
        for (int i = 0; i < extra; i++) m |= bit(-2 + random.nextInt(5), -2 + random.nextInt(5));
        return m;
    }

    /** 床の仮想の y：足の DROP 下。看板・松明の高さ（層の底から TORCH_OFF / SIGN_OFF）を避ける。 */
    private int pickFloorY(AbyssRooms.Shaft s, Vec3 feet) {
        int y = (int) Math.floor(feet.y) - (hardSpawn ? DROP_HARD : DROP);
        for (int i = 0; i < 3; i++) {
            int off = Math.floorMod(y - s.segBottom(1), AbyssRooms.L); // 仮想と実際の差は L の倍数なので、層の中の高さは同じ
            if (off != AbyssRooms.TORCH_OFF && off != AbyssRooms.SIGN_OFF) break;
            y--;
        }
        return y;
    }

    /** 床の実際の y（縦穴の見えている範囲の外なら MIN_VALUE）。 */
    private int physicalFloorY(RoomsState rs) {
        if (floorVY == Integer.MIN_VALUE || rs.abyss == null) return Integer.MIN_VALUE;
        AbyssRooms.Shaft s = rs.abyss;
        boolean[] hidden = new boolean[1];
        Vec3 p = AbyssSpace.toPhysical(rs, new Vec3(cx(s), floorVY + 0.5, cz(s)), hidden);
        if (hidden[0]) return Integer.MIN_VALUE;
        int y = (int) Math.floor(p.y);
        return y > s.bottomY && y <= s.colTop() ? y : Integer.MIN_VALUE;
    }

    /** 置いたブロックを（同じ種類のままなら）空気に戻す。 */
    private void clearPlaced(ServerLevel level) {
        if (placed.isEmpty()) return;
        DropGuard.begin();
        try {
            for (var e : placed.entrySet()) if (level.getBlockState(e.getKey()) == e.getValue()) level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), FLAGS);
        } finally { DropGuard.end("a800 clear"); }
        placed.clear();
    }

    /** 床を今の実際の位置に合わせる（違う高さに置いてあれば外して置き直す。空気のマスにだけ置く）。 */
    private void syncFloor(ServerLevel level, RoomsState rs) {
        int py = physicalFloorY(rs);
        if (py == Integer.MIN_VALUE) { clearPlaced(level); return; }
        if (!placed.isEmpty()) {
            BlockPos any = placed.keySet().iterator().next();
            boolean intact = any.getY() == py;
            if (intact) for (var e : placed.entrySet()) if (level.getBlockState(e.getKey()) != e.getValue()) { intact = false; break; }
            if (intact) return;
            clearPlaced(level);
        }
        AbyssRooms.Shaft s = rs.abyss;
        DropGuard.begin();
        try {
            for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
                boolean crack = (crackMask & bit(lx, lz)) != 0;
                if (crack && crumbled) continue;
                BlockPos q = s.at(AbyssRooms.CX + lx, py, AbyssRooms.CZ + lz);
                if (!level.getBlockState(q).isAir()) continue; // 松明・看板は残す（そもそもその高さは避けている）
                // プレイヤーの体にかかるマスには置かない（埋まらない）
                boolean body = false;
                AABB cell = new AABB(q);
                for (Player p : level.players()) if (!p.isSpectator() && p.getBoundingBox().intersects(cell)) { body = true; break; }
                if (body) continue;
                BlockState st = crack ? cracked() : solid();
                level.setBlock(q, st, FLAGS);
                placed.put(q, st);
            }
        } finally { DropGuard.end("a800 place"); }
    }

    /** ひびのタイルを崩す。 */
    private void crumble(ServerLevel level) {
        crumbled = true;
        DropGuard.begin();
        try {
            var it = placed.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue() != cracked()) continue;
                if (level.getBlockState(e.getKey()) == e.getValue()) {
                    level.levelEvent(2001, e.getKey(), Block.getId(e.getValue())); // 割れる粒と音
                    level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), FLAGS);
                }
                it.remove();
            }
        } finally { DropGuard.end("a800 crumble"); }
    }

    /** 床を全部割って消す（当たった・時間切れ）。 */
    private void shatter(ServerLevel level) {
        DropGuard.begin();
        try {
            for (var e : placed.entrySet()) if (level.getBlockState(e.getKey()) == e.getValue()) {
                level.levelEvent(2001, e.getKey(), Block.getId(e.getValue()));
                level.setBlock(e.getKey(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
        } finally { DropGuard.end("a800 shatter"); }
        placed.clear();
    }

    private void endFloor(ServerLevel level, String why) {
        RoomsDebug.log("A-800 floor {} end: {}", floors + 1, why);
        floorVY = Integer.MIN_VALUE;
        floors++;
        setState(floors >= (hardSpawn ? FLOORS_HARD : FLOORS) ? State.GONE : State.REST);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (leftover != null) { cleanLeftover(level); leave("reloaded"); return; }
        if (rs.abyss == null) { placed.clear(); leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (state == State.GONE || t == null) {
            clearPlaced(level);
            floorVY = Integer.MIN_VALUE;
            if (stateTicks > 5 || t == null) leave(t == null ? "target gone" : "done");
            return;
        }
        Vec3 p = pv(rs, t);
        setVirtualPos(p); // プレイヤーと一緒（姿なし）
        if (life == 1) RoomsDebug.announce(java.util.List.of(t), "A-800：下に床。ひびの入ったタイルの上へ", "A-800 telegraph target={}", t.getGameProfile().getName());
        switch (state) {
            case WARN -> {
                if (stateTicks == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
                if (stateTicks >= WARN) {
                    floorVY = pickFloorY(rs.abyss, p);
                    crackMask = pickCracks();
                    crumbled = false;
                    syncFloor(level, rs);
                    setState(State.FLOOR);
                    level.playSound(null, t.getX(), t.getY() - 6, t.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
                    level.playSound(null, t.getX(), t.getY() - 6, t.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.HOSTILE, 3.0f, 0.6f);
                    RoomsDebug.log("A-800 floor {} at vy={} (feet vy={}) cracks={} placed={}", floors + 1, floorVY, f(p.y), Integer.bitCount(crackMask), placed.size());
                }
            }
            case FLOOR -> {
                syncFloor(level, rs);
                double top = floorVY + 0.5; // ハーフブロックの上面（仮想）
                if (!crumbled && p.y - top < CRUMBLE) {
                    crumble(level);
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.HOSTILE, 2.0f, 0.8f);
                }
                // ぶつかった：ふつうのタイルの上に乗った（足が上面のすぐ上、体がふつうのタイルにかかる）
                for (Player q : level.players()) {
                    if (!inShaft(rs, q) || HidingRules.isHiding(q)) continue;
                    Vec3 qv = pv(rs, q);
                    if (qv.y < top - 0.05 || qv.y > top + 0.3) continue;
                    if (!overSolid(rs, q)) continue;
                    boolean hit = q == t ? RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD)) : false;
                    level.playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.6f);
                    RoomsDebug.log("A-800 impact player={} applied={} -> shatter", q.getGameProfile().getName(), hit);
                    shatter(level);
                    endFloor(level, "impact");
                    return;
                }
                if (p.y < floorVY - 0.5) { clearPlaced(level); endFloor(level, "passed through"); return; }
                if (stateTicks > FLOOR_MAX) { shatter(level); endFloor(level, "timeout"); }
            }
            case REST -> { if (stateTicks >= REST) setState(State.WARN); }
            default -> {}
        }
    }

    /** プレイヤーの体（足元の横の範囲）がふつうのタイルのマスにかかっているか。 */
    private boolean overSolid(RoomsState rs, Player q) {
        AbyssRooms.Shaft s = rs.abyss;
        AABB bb = q.getBoundingBox();
        for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) {
            if ((crackMask & bit(lx, lz)) != 0) continue;
            double x0 = s.ox + AbyssRooms.CX + lx, z0 = s.oz + AbyssRooms.CZ + lz;
            if (bb.maxX > x0 && bb.minX < x0 + 1 && bb.maxZ > z0 && bb.minZ < z0 + 1) return true;
        }
        return false;
    }

    @Override
    public double abyssResync(ServerLevel level, RoomsState rs) {
        double dy = super.abyssResync(level, rs);
        if (state == State.FLOOR && floorVY != Integer.MIN_VALUE && !isRemoved()) syncFloor(level, rs); // 1 周したら床も同じだけ上へ
        return dy;
    }

    private void cleanLeftover(ServerLevel level) {
        DropGuard.begin();
        try {
            for (long l : leftover) {
                BlockPos q = BlockPos.of(l);
                BlockState st = level.getBlockState(q);
                if (st.is(Blocks.SMOOTH_STONE_SLAB) || st.is(Blocks.COBBLED_DEEPSLATE_SLAB)) level.setBlock(q, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        } finally { DropGuard.end("a800 leftover"); }
        RoomsDebug.log("A-800 cleaned {} leftover blocks after reload", leftover.length);
        leftover = null;
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず床を片付ける
        if (level() instanceof ServerLevel sl) {
            try { clearPlaced(sl); } catch (RuntimeException e) { RoomsDebug.warn("A-800 cleanup failed: {}", e.toString()); }
        }
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "state=" + state + " floors=" + floors + " floorVY=" + (floorVY == Integer.MIN_VALUE ? "-" : floorVY) + " placed=" + placed.size() + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        long[] a = new long[placed.size()];
        int i = 0;
        for (BlockPos q : placed.keySet()) a[i++] = q.asLong();
        t.putLongArray("Placed", a);
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        leftover = t.getLongArray("Placed");
        state = State.GONE;
    }
}
