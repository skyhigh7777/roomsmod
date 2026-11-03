package com.example.roomsmod.entity.a;

import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A-1050「見上げる眼」：プレイヤーの約 {@value #DEPTH_ROOMS} 部屋下を一緒に落ちる大きな眼。ときどき目を開けて見上げる。A-1000 の逆（遅く落ちろ）。
 * <ul>
 *   <li>出現：A-1050 から。最初の {@value #GRACE} tick は離れたまま近づくだけ。</li>
 *   <li>くり返し {@value #CYCLES} 回（ハード {@value #CYCLES_HARD}）：閉じている {@value #IDLE} tick（ハード {@value #IDLE_HARD}）→
 *       予告 {@value #WARN} tick（ハード {@value #WARN_HARD}：低いうなりと目の光、アクションバー「下で何かが目を開ける…」）→
 *       開いている {@value #OPEN} tick（ハード {@value #OPEN_HARD}）。</li>
 *   <li>開いている間、スニーク（ゆっくり落ちる）・横穴（体の中心）・ロッカー・引き止め中なら見つからない。全速で落ちている tick を数え、
 *       {@value #SEEN} tick（ハード {@value #SEEN_HARD}）たまるとダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 回開くごとに 1 回だけ。</li>
 *   <li>全速で落ち続けないといけない敵・重力・床・ほかの深い A の敵（A-50 / A-450 / A-600 / A-800 / A-1000 / A-1100 / A-1111 / A-1180）とは同時に出ない。</li>
 * </ul>
 */
public class A1050Entity extends AbyssEnemy {
    public static final double DEPTH_ROOMS = 1.7, MOVE = 0.6;
    public static final int GRACE = 40, CYCLES = 3, CYCLES_HARD = 4, IDLE = 50, IDLE_HARD = 40, WARN = 30, WARN_HARD = 24, OPEN = 60, OPEN_HARD = 80, SEEN = 8, SEEN_HARD = 6;
    public static final float DAMAGE = 5f, DAMAGE_HARD = 7f;
    private enum State { APPROACH, IDLE, WARN, OPEN, GONE }
    private State state = State.APPROACH;
    private int stateTicks = 0, cycles = 0, seen = 0;
    private boolean hitThisOpen = false;

    public A1050Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-1050"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A50Entity.class, A450Entity.class, A600Entity.class, A800Entity.class, A1000Entity.class, A1100Entity.class, A1111Entity.class, A1180Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A1050Entity.class)) { RoomsDebug.log("A-1050 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        Vec3 p = pv(rs, t);
        setVirtualPos(new Vec3(cx(rs.abyss), p.y - (DEPTH_ROOMS + 0.8) * AbyssRooms.L, cz(rs.abyss)));
        RoomsDebug.log("A-1050 prepare target={} cycles={}", t.getGameProfile().getName(), hardSpawn ? CYCLES_HARD : CYCLES);
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        Vec3 v = virtualPos();
        stateTicks++;
        if (t == null || state == State.GONE) { // 下へ沈んで消える
            setVirtualPos(v.add(0, -0.5, 0));
            if (state != State.GONE) setState(State.GONE);
            if (stateTicks > 40) leave(t == null ? "target gone" : "done");
            return;
        }
        Vec3 p = pv(rs, t);
        double want = p.y - DEPTH_ROOMS * AbyssRooms.L;
        double ny = v.y + Math.max(-MOVE - AbyssRooms.FALL_CAP, Math.min(MOVE, want - v.y)); // プレイヤーと一緒に落ちながら決まった深さへ
        setVirtualPos(new Vec3(cx(rs.abyss), ny, cz(rs.abyss)));
        faceTowards(position().add(p.subtract(virtualPos())));
        switch (state) {
            case APPROACH -> {
                if (stateTicks == 1) {
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 2.5f, 0.4f);
                    RoomsDebug.announce(java.util.List.of(t), "A-1050：眼が開いたらスニーク（ゆっくり落ちる）か横穴", "A-1050 telegraph target={}", t.getGameProfile().getName());
                }
                if (stateTicks >= GRACE) setState(State.IDLE);
            }
            case IDLE -> { if (stateTicks >= (hardSpawn ? IDLE_HARD : IDLE)) setState(State.WARN); }
            case WARN -> {
                if (stateTicks == 1) {
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 2.5f, 0.5f);
                    t.displayClientMessage(Component.literal("下で何かが目を開ける…").withStyle(ChatFormatting.GOLD), true);
                }
                if (stateTicks % 10 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 1.2f, 0.5f);
                glow(level, rs, t, 2);
                if (stateTicks >= (hardSpawn ? WARN_HARD : WARN)) {
                    setState(State.OPEN); seen = 0; hitThisOpen = false;
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.HOSTILE, 3.0f, 0.4f);
                }
            }
            case OPEN -> {
                glow(level, rs, t, 4);
                boolean safe = t.isShiftKeyDown() || sheltered(rs, t, 0.2) || AbyssRooms.isHeld(t);
                if (stateTicks % 10 == 1) t.displayClientMessage(Component.literal(safe ? "見られている…（そのまま）" : "見られている！スニーク").withStyle(safe ? ChatFormatting.GRAY : ChatFormatting.RED), true);
                if (!safe && !hitThisOpen && ++seen >= (hardSpawn ? SEEN_HARD : SEEN)) {
                    hitThisOpen = true;
                    boolean hit = RoomsDamage.hurt(this, t, hardDamage(DAMAGE, DAMAGE_HARD));
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.6f);
                    RoomsDebug.log("A-1050 gaze hit {} applied={}", t.getGameProfile().getName(), hit);
                }
                if (stateTicks >= (hardSpawn ? OPEN_HARD : OPEN)) {
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENDER_EYE_DEATH, SoundSource.HOSTILE, 1.5f, 0.6f);
                    if (++cycles >= (hardSpawn ? CYCLES_HARD : CYCLES)) { RoomsDebug.log("A-1050 done after {} cycles", cycles); setState(State.GONE); }
                    else setState(State.IDLE);
                }
            }
            default -> {}
        }
    }

    /** 眼の光：眼からプレイヤーへ向かう光の粒（本人だけに送る）。 */
    private void glow(ServerLevel level, RoomsState rs, Player t, int n) {
        if (!(t instanceof ServerPlayer sp) || abyssHidden[0]) return;
        for (int i = 0; i < n; i++)
            level.sendParticles(sp, ParticleTypes.END_ROD, true, getX() + (random.nextDouble() - 0.5) * 3.0, getY() + 1.0 + random.nextDouble(), getZ() + (random.nextDouble() - 0.5) * 3.0, 0, 0, 0.6, 0, 1.0);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(3) == 0)
            level().addParticle(ParticleTypes.SCULK_SOUL, getX() + (random.nextDouble() - 0.5) * 2.0, getY() + random.nextDouble() * 2.0, getZ() + (random.nextDouble() - 0.5) * 2.0, 0, 0.03, 0);
    }

    @Override protected String debugState() { return "state=" + state + " t=" + stateTicks + " cycles=" + cycles + " seen=" + seen + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("StateTicks", stateTicks); t.putInt("Cycles", cycles); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.IDLE; }
        stateTicks = t.getInt("StateTicks"); cycles = t.getInt("Cycles");
    }
}
