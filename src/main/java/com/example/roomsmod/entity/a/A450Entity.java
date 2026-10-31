package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A-450：しばらくプレイヤーの重力が逆さになる（上へ「落ちる」）。姿はない（プレイヤーのまわりの逆さのポータルの粒）。
 * <ul>
 *   <li>予告：{@value #WARN} tick（ハード {@value #WARN_HARD}）、低い音と、プレイヤーのまわりから上へ昇る粒。</li>
 *   <li>逆さ：{@value #INVERT} tick（ハード {@value #INVERT_HARD}）。落ちる速さが {@value #RAMP} tick かけて反転し、毎 tick
 *       {@value #UP}（ハード {@value #UP_HARD}）ブロック上へ（スニーク中は半分）。横は自由に動ける。
 *       上がるのは逆さになった位置から最大 {@value #RISE_MAX} ブロック、かつ AbyssRooms.holdCeiling（入口の高さの 4 ブロック下）まで：
 *       そこで浮いて止まる（縦穴の上の蓋・天井へは決して押し付けない）。</li>
 *   <li>1 周（テレポート）：上へ進む間は 1 周しない（部屋は戻らない・進まない。仮想座標は「前の部屋の下のほう」に戻るだけで、敵の位置関係もそのまま）。
 *       見張り（止まっている判定）は引き止め中なので数えない。</li>
 *   <li>戻す：時間切れ・プレイヤーが縦穴を出た／死んだ／ログアウト（AbyssRooms が期限 3 tick で自動的に放す）・この敵が消えたとき。
 *       縦穴の中は落下ダメージなし。逆さの間は浮遊の効果（5 tick ずつ延長、戻すときに外す）も付ける：
 *       専用サーバーの「浮きすぎ（80 tick）」切断の対象外にするため（速さは AbyssRooms.hold が毎 tick 上書き）。ダメージはない（怖さは向きの混乱と、そのあいだ一緒に出てよい A-200 の弾）。</li>
 *   <li>A-200 / A-350 以外の A の敵とは同時に出ない（隠れる・寄る・引き止めの要求とぶつかる）。A-50 の引き止めと同じ仕組み（AbyssRooms.hold）を使うので、引き止め中には出ない。</li>
 * </ul>
 */
public class A450Entity extends AbyssEnemy {
    public static final float UP = 0.12f, UP_HARD = 0.15f;
    public static final double RISE_MAX = 10;
    public static final int WARN = 60, WARN_HARD = 50, INVERT = 140, INVERT_HARD = 180, RAMP = 20;

    private enum State { WARN, INVERT, GONE }
    private State state = State.WARN;
    private int stateTicks = 0;
    private double startY = Double.NaN;

    public A450Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-450"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A25Entity.class, A50Entity.class, A90Entity.class, A130Entity.class, A180Entity.class, A250Entity.class, A300Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A450Entity.class)) { RoomsDebug.log("A-450 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || HidingRules.isHiding(t) || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-450 prepare target={}", t.getGameProfile().getName());
        return blockPosition();
    }

    private void setState(State s) { state = s; stateTicks = 0; }

    private void restore(ServerLevel level, Player t, String why) {
        if (t instanceof ServerPlayer sp) sp.removeEffect(MobEffects.LEVITATION);
        if (t instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) {
            AbyssRooms.release(sp);
            sp.fallDistance = 0;
            level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f, 0.7f);
        }
        if (state != State.GONE) RoomsDebug.log("A-450 restore: {}", why);
        setState(State.GONE);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (state == State.GONE || t == null) {
            if (state != State.GONE) restore(level, targetId == null ? null : level.getPlayerByUUID(targetId), "target gone");
            if (stateTicks > 10) leave("done");
            return;
        }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        if (life == 1) {
            level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 3.0f, 0.5f);
            RoomsDebug.announce(java.util.List.of(t), "A-450：もうすぐ上と下が入れ替わるわ", "A-450 telegraph warn={}", hardSpawn ? WARN_HARD : WARN);
        }
        switch (state) {
            case WARN -> {
                motes(level, rs, t, 3);
                if (stateTicks % 20 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1.5f, 0.5f + stateTicks / 120f);
                if (stateTicks >= (hardSpawn ? WARN_HARD : WARN)) {
                    setState(State.INVERT);
                    startY = pv(rs, t).y; // 仮想座標（逆さになる直前に 1 周しても上限がずれない）
                    level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.5f);
                    RoomsDebug.log("A-450 invert vy={} ceiling={}", f(startY), f(AbyssRooms.holdCeiling(rs)));
                }
            }
            case INVERT -> {
                if (!(t instanceof ServerPlayer sp)) { restore(level, t, "not a server player"); return; }
                motes(level, rs, t, 2);
                if (stateTicks >= (hardSpawn ? INVERT_HARD : INVERT)) { restore(level, t, "time over"); return; }
                if (HidingRules.isHiding(t)) { if (AbyssRooms.isHeld(sp)) AbyssRooms.release(sp); sp.removeEffect(MobEffects.LEVITATION); return; } // ロッカーの中は動かさない
                float up = (hardSpawn ? UP_HARD : UP) * (t.isShiftKeyDown() ? 0.5f : 1f);
                float k = Math.min(1f, stateTicks / (float) RAMP);
                float vy = -(float) AbyssRooms.MAX_FALL * (1 - k) + up * k;
                // 上限（仮想の上がり幅と、実際の天井側の上限の低いほう）：そこで浮いて止まる
                double room = Math.min(startY + RISE_MAX - pv(rs, t).y, AbyssRooms.holdCeiling(rs) - t.getY());
                if (room <= 0) vy = Math.min(vy, 0f);
                else if (vy > room) vy = (float) room;
                AbyssRooms.hold(sp, vy, 3);
                // 浮遊の効果も付ける（見た目の合図。サーバーの「浮きすぎ」切断の対象外にする。速さは hold が毎 tick 上書き）
                sp.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5, 0, false, false, true));
                if (stateTicks % 25 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
            default -> {}
        }
    }

    /** プレイヤーのまわりから上へ昇る粒（縦穴の中のプレイヤーにだけ）。 */
    private void motes(ServerLevel level, RoomsState rs, Player t, int n) {
        for (Player q : level.players()) if (q instanceof ServerPlayer sp && inShaft(rs, q))
            for (int i = 0; i < n; i++)
                level.sendParticles(sp, ParticleTypes.REVERSE_PORTAL, true, t.getX() + (random.nextDouble() - 0.5) * 3, t.getY() - 1 + random.nextDouble() * 3, t.getZ() + (random.nextDouble() - 0.5) * 3, 1, 0, 0.2, 0, 0.05);
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず重力を戻す（AbyssRooms も期限で自動的に戻す）
        if (level() instanceof ServerLevel sl && targetId != null && sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp && AbyssRooms.isHeld(sp)) { AbyssRooms.release(sp); sp.removeEffect(MobEffects.LEVITATION); sp.fallDistance = 0; }
        super.onRemovedFromLevel();
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "state=" + state + " t=" + stateTicks + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.GONE; }
        if (state == State.INVERT) state = State.GONE; // 読み込み直後は逆さにし直さない
        stateTicks = 0;
    }
}
