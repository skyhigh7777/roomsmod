package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A-1111「数え違い」：これから {@value #SPAN} 部屋（ハード {@value #SPAN_HARD}）の看板が、1 つを除いて間違った番号（±1〜2）になる。
 * 見た目だけの横穴も {@value #FAKES} つ（ハード {@value #FAKES_HARD}）増える。看板の番号が画面の部屋番号（HUD）と合う部屋の横穴だけが本物の出口。姿はない。
 * <ul>
 *   <li>出現：プレイヤーの部屋 R の R+{@value #MIN_AHEAD} 以降の、本物の（A-90 のいない・予約されていない）横穴の部屋を 1 つ出口に選ぶ。
 *       それ以外の部屋 R+1 .. R+span-1 の看板は別の番号（AbyssRooms.setSignOverrides。色はいつもどおり：見分けるのは番号だけ）。
 *       横穴のない部屋に見た目だけの横穴（AbyssRooms.setFake、ロッカーつき・照明なし）を足す。出口の横穴は予約（A-90 は潜まない）。</li>
 *   <li>予告：数を数えるような音とアクションバー「…番号が合わない」。</li>
 *   <li>抜ける：出口の部屋の横穴に入る（体の中心、またはそこのロッカー）。看板と横穴を元に戻し、澄んだ音。</li>
 *   <li>罠：ほかの横穴（本物でも見た目だけでも）に入る・ロッカーに隠れるとダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、ロッカーから出され、終わる。
 *       出口を 1 部屋ぶんより下まで素通りする、または {@value #TIMEOUT} tick（ハード {@value #TIMEOUT_HARD}）たっても同じダメージ。</li>
 *   <li>終わる・消えるときは必ず看板と見た目だけの横穴を元に戻す（どちらも保存しないので、読み込み直したときも作り直しで元に戻る）。
 *       看板・横穴・隠れる・暗さ・床・加速の敵（A-25 / A-50 / A-90 / A-130 / A-350 / A-450 / A-525 / A-600 / A-666 / A-750 / A-800 / A-1000 / A-1050 / A-1100 / A-1180）とは同時に出ない。</li>
 * </ul>
 */
public class A1111Entity extends AbyssEnemy {
    public static final int SPAN = 9, SPAN_HARD = 11, MIN_AHEAD = 3, FAKES = 2, FAKES_HARD = 3, TIMEOUT = 760, TIMEOUT_HARD = 660, GRACE = 10;
    public static final float DAMAGE = 7f, DAMAGE_HARD = 10f;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private int baseRoom = -1, exitRoom = -1;
    /** 見た目だけの横穴：部屋 → [向き, 高さ]。 */
    private final Map<Integer, int[]> fakes = new HashMap<>();
    /** 間違った番号：部屋 → ずれ（±1〜2）。 */
    private final Map<Integer, Integer> wrong = new HashMap<>();
    private boolean done = false;
    private int doneTicks = 0;

    public A1111Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-1111"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override public int reservedAlcove() { return done ? -1 : exitRoom; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A350Entity.class, A450Entity.class, A525Entity.class, A600Entity.class,
                A666Entity.class, A750Entity.class, A800Entity.class, A1000Entity.class, A1050Entity.class, A1100Entity.class, A1180Entity.class};
    }

    private int span() { return hardSpawn ? SPAN_HARD : SPAN; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A1111Entity.class)) { RoomsDebug.log("A-1111 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t) || sheltered(rs, t, 0.2)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        java.util.Set<Integer> bad = claimedOrReserved(level);
        List<Integer> exits = new ArrayList<>(), plain = new ArrayList<>();
        for (int r = room + 1; r < room + span(); r++) {
            if (!AbyssSpace.isAbyssRoom(rs, r)) continue;
            boolean real = AbyssRooms.alcoveSpot(level, rs.abyss, r) != null;
            if (real && r >= room + MIN_AHEAD && !bad.contains(r)) exits.add(r);
            if (!real && !AbyssRooms.hasFake(rs, r)) plain.add(r);
        }
        if (exits.isEmpty()) { RoomsDebug.log("A-1111 prepare skipped: no free alcove in rooms {}..{}", room + MIN_AHEAD, room + span() - 1); return null; }
        targetId = t.getUUID();
        baseRoom = room;
        exitRoom = exits.get(random.nextInt(exits.size()));
        java.util.Collections.shuffle(plain, new java.util.Random(random.nextLong()));
        for (int i = 0; i < Math.min(hardSpawn ? FAKES_HARD : FAKES, plain.size()); i++) fakes.put(plain.get(i), new int[]{random.nextInt(4), 1 + random.nextInt(7)});
        for (int r = room + 1; r < room + span(); r++) if (r != exitRoom) wrong.put(r, (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(2)));
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-1111 prepare target={} base={} exit={} ({}) fakes={} span={}", t.getGameProfile().getName(), room, exitRoom, Sections.formatRoom(rs, exitRoom), fakes.keySet(), span());
        return blockPosition();
    }

    private void apply(ServerLevel level, RoomsState rs) {
        Map<Integer, AbyssRooms.SignOverride> m = new HashMap<>();
        for (var e : wrong.entrySet()) m.put(e.getKey(), new AbyssRooms.SignOverride(Sections.formatRoom(rs, e.getKey() + e.getValue()), null));
        AbyssRooms.setSignOverrides(level, rs, m);
        ensureFakes(level, rs);
    }

    private void ensureFakes(ServerLevel level, RoomsState rs) {
        for (var e : fakes.entrySet()) if (!AbyssRooms.hasFake(rs, e.getKey())) AbyssRooms.setFake(level, rs, e.getKey(), SIDES[e.getValue()[0]], e.getValue()[1]);
    }

    private void restore(ServerLevel level, RoomsState rs, String why) {
        if (!done) RoomsDebug.log("A-1111 end: {}", why);
        done = true;
        doneTicks = 0;
        if (rs.abyss == null) return;
        AbyssRooms.setSignOverrides(level, rs, Map.of());
        for (int r : fakes.keySet()) if (AbyssRooms.hasFake(rs, r)) AbyssRooms.setFake(level, rs, r, null, 0);
    }

    private void penalty(ServerLevel level, RoomsState rs, Player t, String why) {
        if (t instanceof ServerPlayer sp && HidingRules.isHiding(sp)) LockerManager.exit(sp, "A-1111");
        level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2.5f, 0.6f);
        if (t instanceof ServerPlayer sp) level.sendParticles(sp, ParticleTypes.REVERSE_PORTAL, true, t.getX(), t.getY() + 1, t.getZ(), 30, 0.6, 1.0, 0.6, 0.05);
        boolean hit = RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
        t.displayClientMessage(Component.literal("数え違えた").withStyle(ChatFormatting.DARK_RED), true);
        RoomsDebug.log("A-1111 penalty player={} reason={} applied={}", t.getGameProfile().getName(), why, hit);
        restore(level, rs, why);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        if (done || t == null || baseRoom < 0) {
            if (!done) restore(level, rs, "target gone");
            if (++doneTicks > 5) leave("done");
            return;
        }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        if (life == 1) {
            apply(level, rs);
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            t.displayClientMessage(Component.literal("…番号が合わない").withStyle(ChatFormatting.GRAY), true);
            RoomsDebug.announce(java.util.List.of(t), "A-1111：看板が画面の番号と合う部屋の横穴へ（" + Sections.formatRoom(rs, exitRoom) + "）", "A-1111 telegraph exit={}", exitRoom);
        } else ensureFakes(level, rs);
        if (life % 30 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.0f, 0.5f + (life / 30 % 4) * 0.1f); // 数える音
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, exitRoom);
        if (spot == null) { restore(level, rs, "exit alcove missing"); return; }
        Vec3 p = pv(rs, t);
        boolean nearExit = Math.abs(p.y - spot.floorY()) < 1.5 && Math.hypot(p.x - spot.back().x, p.z - spot.back().z) < 2.6;
        boolean in = sheltered(rs, t, 0.2);
        if (in && (nearExit || HidingRules.isHiding(t) && Math.abs(p.y - spot.floorY()) < 3)) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3.0f, 1.2f);
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.4f);
            t.displayClientMessage(Component.literal("数が合った").withStyle(ChatFormatting.AQUA), true);
            RoomsDebug.log(t, "A-1111: 抜けた", "A-1111 escaped player={} exit={}", t.getGameProfile().getName(), exitRoom);
            restore(level, rs, "escaped");
            return;
        }
        if (in && life > GRACE) { penalty(level, rs, t, "wrong alcove"); return; }
        boolean passed = p.y < spot.floorY() - AbyssRooms.L;
        if (passed || life > (hardSpawn ? TIMEOUT_HARD : TIMEOUT)) penalty(level, rs, t, passed ? "passed exit" : "timeout");
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず看板と見た目だけの横穴を元に戻す（チャンクの読み込み解除・終了時は保存されないので作り直しで戻る）
        if (level() instanceof ServerLevel sl && !done) {
            RemovalReason r = getRemovalReason();
            if (r == null || r.shouldDestroy()) restore(sl, RoomsState.get(sl), "removed");
        }
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "base=" + baseRoom + " exit=" + exitRoom + " fakes=" + fakes.keySet() + " done=" + done + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Base", baseRoom); t.putInt("Exit", exitRoom); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        baseRoom = t.getInt("Base"); exitRoom = t.getInt("Exit");
        done = true; // 読み込み直後は終わり（看板・見た目だけの横穴は作り直しで元に戻っている）
    }
}
