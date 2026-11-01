package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A-750「くり返し」：何部屋ものあいだ、番号の看板が同じ番号をくり返す。1 つだけ違う番号（赤い字）の部屋があり、その部屋の横穴に入ると抜けられる。姿はない。
 * <ul>
 *   <li>出現：プレイヤーの部屋 R の次から {@value #SPAN} 部屋（ハード {@value #SPAN_HARD}）の看板を R の番号にする（AbyssRooms.setSignOverrides。看板は層を作るときに差し替えた文字で作る）。
 *       そのうち R+{@value #MIN_AHEAD} 以降の、本物の（A-90 のいない）横穴のある部屋を 1 つ選び、その看板だけ本当の番号を赤い字で。選んだ横穴は予約（A-90 は潜まない）。</li>
 *   <li>予告：時計の針のような音とアクションバー「…さっきも同じ番号を見た」。</li>
 *   <li>抜ける：赤い番号の部屋の横穴に入る（体の中心、またはそこのロッカー）。看板を元に戻し、澄んだ音。</li>
 *   <li>罰：赤い番号の横穴を 1 部屋ぶん（{@value AbyssRooms#L} ブロック）より下まで素通りする、または {@value #TIMEOUT} tick（ハード {@value #TIMEOUT_HARD}）たつと、
 *       ダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）。看板は元に戻る。</li>
 *   <li>終わる・消えるときは必ず看板を元に戻す（保存しないので、読み込み直したときも作り直しで元に戻る）。
 *       隠れる・寄る・止まる・見えないの敵（A-25 / A-50 / A-90 / A-130 / A-350 / A-450 / A-525 / A-600 / A-666 / A-800 / A-1000）とは同時に出ない。</li>
 * </ul>
 */
public class A750Entity extends AbyssEnemy {
    public static final int SPAN = 8, SPAN_HARD = 10, MIN_AHEAD = 3, TIMEOUT = 700, TIMEOUT_HARD = 600;
    public static final float DAMAGE = 6f, DAMAGE_HARD = 9f;

    private int baseRoom = -1, exitRoom = -1;
    private boolean done = false;
    private int doneTicks = 0;

    public A750Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-750"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override public int reservedAlcove() { return done ? -1 : exitRoom; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A350Entity.class, A450Entity.class,
                A525Entity.class, A600Entity.class, A666Entity.class, A800Entity.class, A1000Entity.class};
    }

    private int span() { return hardSpawn ? SPAN_HARD : SPAN; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A750Entity.class)) { RoomsDebug.log("A-750 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        int room = AbyssRooms.roomOf(t, playerRoom);
        java.util.Set<Integer> bad = claimedOrReserved(level);
        java.util.List<Integer> cand = new java.util.ArrayList<>();
        for (int r = room + MIN_AHEAD; r < room + span(); r++)
            if (AbyssSpace.isAbyssRoom(rs, r) && !bad.contains(r) && AbyssRooms.alcoveSpot(level, rs.abyss, r) != null) cand.add(r);
        if (cand.isEmpty()) { RoomsDebug.log("A-750 prepare skipped: no free alcove in rooms {}..{}", room + MIN_AHEAD, room + span() - 1); return null; }
        targetId = t.getUUID();
        baseRoom = room;
        exitRoom = cand.get(random.nextInt(cand.size()));
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-750 prepare target={} base={} ({}) exit={} ({}) span={}", t.getGameProfile().getName(), room, Sections.formatRoom(rs, room), exitRoom, Sections.formatRoom(rs, exitRoom), span());
        return blockPosition();
    }

    /** 看板の差し替え：R+1 .. R+span-1 は R の番号、出口の部屋だけ本当の番号を赤で。 */
    private void apply(ServerLevel level, RoomsState rs) {
        java.util.Map<Integer, AbyssRooms.SignOverride> m = new java.util.HashMap<>();
        String same = Sections.formatRoom(rs, baseRoom);
        for (int r = baseRoom + 1; r < baseRoom + span(); r++)
            m.put(r, r == exitRoom ? new AbyssRooms.SignOverride(Sections.formatRoom(rs, r), DyeColor.RED) : new AbyssRooms.SignOverride(same, null));
        AbyssRooms.setSignOverrides(level, rs, m);
    }

    private void restore(ServerLevel level, RoomsState rs, String why) {
        if (!done) RoomsDebug.log("A-750 loop end: {}", why);
        done = true;
        doneTicks = 0;
        if (rs.abyss != null) AbyssRooms.setSignOverrides(level, rs, java.util.Map.of());
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
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
            t.displayClientMessage(Component.literal("…さっきも同じ番号を見た").withStyle(ChatFormatting.GRAY), true);
            RoomsDebug.announce(java.util.List.of(t), "A-750：赤い番号の部屋の横穴へ（" + Sections.formatRoom(rs, exitRoom) + "）", "A-750 telegraph exit={}", exitRoom);
        }
        if (life % 20 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 1.2f, life % 40 == 0 ? 0.5f : 0.6f); // 時計の針
        AbyssRooms.AlcoveSpot spot = AbyssRooms.alcoveSpot(level, rs.abyss, exitRoom);
        if (spot == null) { restore(level, rs, "exit alcove missing"); return; }
        Vec3 p = pv(rs, t);
        // 抜けた：赤い番号の部屋の横穴の中（体の中心）か、そこのロッカー
        boolean inExit = Math.abs(p.y - spot.floorY()) < 1.5 && Math.hypot(p.x - spot.back().x, p.z - spot.back().z) < 2.6 && sheltered(rs, t, 0.2);
        if (inExit || HidingRules.isHiding(t) && Math.abs(p.y - spot.floorY()) < 3) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 3.0f, 1.2f);
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.4f);
            t.displayClientMessage(Component.literal("番号が進みだした").withStyle(ChatFormatting.AQUA), true);
            RoomsDebug.log(t, "A-750: 抜けた", "A-750 escaped player={} exit={}", t.getGameProfile().getName(), exitRoom);
            restore(level, rs, "escaped");
            return;
        }
        boolean passed = p.y < spot.floorY() - AbyssRooms.L;
        if (passed || life > (hardSpawn ? TIMEOUT_HARD : TIMEOUT)) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 2.0f, 0.5f);
            if (t instanceof ServerPlayer sp) level.sendParticles(sp, ParticleTypes.REVERSE_PORTAL, true, t.getX(), t.getY() + 1, t.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
            boolean hit = RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
            RoomsDebug.log("A-750 penalty player={} reason={} applied={}", t.getGameProfile().getName(), passed ? "passed exit" : "timeout", hit);
            restore(level, rs, passed ? "passed exit" : "timeout");
        }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず看板を元に戻す
        if (level() instanceof ServerLevel sl && !done) { RoomsState rs = RoomsState.get(sl); if (rs.abyss != null) AbyssRooms.setSignOverrides(sl, rs, java.util.Map.of()); }
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "base=" + baseRoom + " exit=" + exitRoom + " done=" + done + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Base", baseRoom); t.putInt("Exit", exitRoom); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        baseRoom = t.getInt("Base"); exitRoom = t.getInt("Exit");
        done = true; // 読み込み直後は終わり（看板は作り直しで元に戻っている）
    }
}
