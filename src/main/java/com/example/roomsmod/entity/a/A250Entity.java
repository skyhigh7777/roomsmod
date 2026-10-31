package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
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
 * A-250：縦穴の壁が数秒だけ内側へ 1 列せり出す（内側 5x5 → 3x3）。姿はない。
 * <ul>
 *   <li>予告 {@value #WARN}（ハード {@value #WARN_HARD}）tick：低い地鳴りと、せり出す外周の列に沿って壁の粉が落ちる。</li>
 *   <li>せり出す瞬間、外周の列に {@value #TOLERANCE} ブロックより深く体が入っていればダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、
 *       内側へ押し出す（埋まらない）。真ん中寄り（中心から約 1.2 ブロック以内）にいる・横穴の中・ロッカーなら当たらない。</li>
 *   <li>せり出している {@value #HOLD}（ハード {@value #HOLD_HARD}）tick の間も横穴の入口はふさがない（横穴へ逃げられる）。プレイヤーの体にかかるマスには壁を置かない。
 *       落ちる列の真ん中 3x3 は空いたままなので落下は止まらない。終わったら置いた壁だけ元の空気に戻す（消えるときも必ず戻す）。</li>
 *   <li>A-90 / A-180 / A-50 / A-130 とは同時に出ない（反対側の壁際・雲の隙間へ逃げる必要がある敵と、要求が逆になるため）。</li>
 * </ul>
 */
public class A250Entity extends AbyssEnemy {
    public static final int WARN = 50, WARN_HARD = 44, HOLD = 80, HOLD_HARD = 100;
    public static final float DAMAGE = 5f, DAMAGE_HARD = 7f;
    public static final double TOLERANCE = 0.1;
    private enum State { WARN, CLOSED }
    private State state = State.WARN;
    private int stateTicks = 0;

    public A250Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-250"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        if (anyAlive(level, A90Entity.class, A180Entity.class, A50Entity.class, A130Entity.class)) { RoomsDebug.log("A-250 prepare skipped: conflicting A enemy alive"); return null; }
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-250 prepare target={}", t.getGameProfile().getName());
        return blockPosition();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (t != null) setVirtualPos(pv(rs, t));
        switch (state) {
            case WARN -> {
                if (t == null) { leave("target gone"); return; }
                if (life == 1) RoomsDebug.announce(java.util.List.of(t), "A-250：壁が動くわ（真ん中へ）", "A-250 telegraph");
                if (stateTicks % 8 == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
                if (stateTicks % 12 == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.5f, 0.5f);
                dust(level, rs);
                if (stateTicks >= (hardSpawn ? WARN_HARD : WARN)) close(level, rs);
            }
            case CLOSED -> {
                if (!AbyssRooms.squeezed(rs)) AbyssRooms.setSqueeze(level, rs, true); // 読み込み直後など
                else AbyssRooms.tickSqueeze(level, rs);
                if (stateTicks >= (hardSpawn ? HOLD_HARD : HOLD) || t == null) {
                    AbyssRooms.setSqueeze(level, rs, false);
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.HOSTILE, 3.0f, 0.5f);
                    leave(t == null ? "target gone" : "opened");
                }
            }
        }
    }

    private void close(ServerLevel level, RoomsState rs) {
        AbyssRooms.Shaft s = rs.abyss;
        for (Player p : level.players()) {
            if (!inShaft(rs, p) || !(p instanceof ServerPlayer sp) || HidingRules.isHiding(p)) continue;
            if (!AbyssRooms.inSqueezeRing(s, p, 0)) continue;
            boolean hit = AbyssRooms.inSqueezeRing(s, p, TOLERANCE);
            AbyssRooms.pushInside(sp, s, 0.05); // 壁の中に埋めない
            if (hit) {
                RoomsDamage.hurt(this, p, hardDamage(DAMAGE, DAMAGE_HARD));
                RoomsDebug.log("A-250 crushed {}", p.getGameProfile().getName());
            }
        }
        AbyssRooms.setSqueeze(level, rs, true);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 3.0f, 0.5f);
        state = State.CLOSED; stateTicks = 0;
    }

    /** 予告：せり出す外周の列に沿って壁の粉を落とす（プレイヤーの上下数ブロック）。 */
    private void dust(ServerLevel level, RoomsState rs) {
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.FALLING_DUST, Sections.A.palette().wall().get());
        double c = AbyssRooms.IN + 0.5, half = AbyssRooms.SQUEEZE_HALF;
        for (Player p : level.players()) if (p instanceof ServerPlayer sp && inShaft(rs, p))
            for (int i = 0; i < 10; i++) {
                double along = (random.nextDouble() * 2 - 1) * c, depth = half + random.nextDouble() * (c - half);
                int side = random.nextInt(4);
                double dx = side < 2 ? (side == 0 ? depth : -depth) : along, dz = side < 2 ? along : (side == 2 ? depth : -depth);
                level.sendParticles(sp, opt, true, cx(rs.abyss) + dx, p.getY() + (random.nextDouble() - 0.3) * 6, cz(rs.abyss) + dz, 1, 0, 0, 0, 0);
            }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず壁を戻す（チャンクの読み込み解除・終了時は戻さない：読み込み後の refresh が縦穴の列を片付ける）
        if (level() instanceof ServerLevel sl && state == State.CLOSED) {
            RemovalReason r = getRemovalReason();
            if (r == null || r.shouldDestroy()) AbyssRooms.setSqueeze(sl, RoomsState.get(sl), false);
        }
        super.onRemovedFromLevel();
    }

    @Override protected String debugState() { return "state=" + state + " t=" + stateTicks + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("State", state.name()); t.putInt("StateTicks", stateTicks); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.WARN; }
        stateTicks = t.getInt("StateTicks");
    }
}
