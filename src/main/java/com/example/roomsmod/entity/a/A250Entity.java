package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSqueeze;
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

/**
 * A-250：縦穴の内側 5x5 の一部が数秒だけ壁になる。姿はない。残る（安全な）形は出るたびに抽選（{@link AbyssSqueeze.Shape}、向きもランダム）：
 * 真ん中の 3x3（少なめ）・片側へ寄せた 3x3・角の 3x3・壁沿いの 2x5・L 字・対角の 2x2 ふたつ。
 * <ul>
 *   <li>予告：低い地鳴りと、壁になるマスの中にだけ壁の粉が落ちる（残るマスには落ちない）。長さは形ごとに
 *       {@link AbyssSqueeze#warnTicks}：最も不利な位置から一番近い安全な位置まで、空中でスニークして動く時間（反応 0.5 秒込み）の
 *       {@value AbyssSqueeze#MARGIN} 倍（ハード {@value AbyssSqueeze#MARGIN_HARD} 倍）、最低 {@value AbyssSqueeze#MIN_WARN}（ハード {@value AbyssSqueeze#MIN_WARN_HARD}）tick。</li>
 *   <li>壁になる瞬間、壁になるマスに {@value AbyssSqueeze#TOLERANCE} ブロックより深く体が入っていればダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、
 *       一番近い残るマスへ押し出す（埋まらない）。残るマスにいる・横穴の中・ロッカーなら当たらない。</li>
 *   <li>壁の {@value #HOLD}（ハード {@value #HOLD_HARD}）tick の間も横穴の入口はふさがない。プレイヤーの体にかかるマスには壁を置かない。残るマスは上から下まで空いている
 *       （落下は止まらない）。終わったら置いた壁だけ元の空気に戻す（消えるときも必ず戻す）。</li>
 *   <li>A-90 / A-180 / A-50 / A-130 とは同時に出ない。</li>
 * </ul>
 */
public class A250Entity extends AbyssEnemy {
    public static final int HOLD = 80, HOLD_HARD = 100;
    public static final float DAMAGE = 5f, DAMAGE_HARD = 7f;
    private enum State { WARN, CLOSED }
    private State state = State.WARN;
    private int stateTicks = 0, openMask = 0, warn = AbyssSqueeze.MIN_WARN;
    private String shape = "";

    public A250Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-250"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level)) { RoomsDebug.log("A-250 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        if (anyAlive(level, A90Entity.class, A180Entity.class, A50Entity.class, A130Entity.class)) { RoomsDebug.log("A-250 prepare skipped: conflicting A enemy alive"); return null; }
        java.util.Random r = new java.util.Random(random.nextLong());
        AbyssSqueeze.Shape sh = AbyssSqueeze.pick(r);
        int rot = r.nextInt(4);
        openMask = AbyssSqueeze.mask(sh, rot);
        shape = sh.name() + "/" + rot;
        warn = AbyssSqueeze.warnTicks(openMask, hardSpawn);
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        RoomsDebug.log("A-250 prepare target={} shape={} warn={} worst={} sneakTicks={}", t.getGameProfile().getName(), shape, warn,
                f(AbyssSqueeze.worstDistance(openMask)), AbyssSqueeze.sneakTicks(AbyssSqueeze.worstDistance(openMask)));
        return blockPosition();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null || openMask == 0) { leave("no shaft"); return; }
        Player t = target(level, rs);
        stateTicks++;
        if (t != null) setVirtualPos(pv(rs, t));
        switch (state) {
            case WARN -> {
                if (t == null) { leave("target gone"); return; }
                if (life == 1) RoomsDebug.announce(java.util.List.of(t), "A-250：壁が動くわ（粉の落ちていない所へ、" + shape + "）", "A-250 telegraph shape={} warn={}", shape, warn);
                if (stateTicks % 8 == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
                if (stateTicks % 12 == 1) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.5f, 0.5f);
                if (warn - stateTicks == 15) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PISTON_CONTRACT, SoundSource.HOSTILE, 2.0f, 0.4f); // 直前の合図
                dust(level, rs);
                if (stateTicks >= warn) close(level, rs);
            }
            case CLOSED -> {
                if (!AbyssRooms.squeezed(rs)) AbyssRooms.setSqueeze(level, rs, openMask); // 読み込み直後など
                else AbyssRooms.tickSqueeze(level, rs);
                if (stateTicks >= (hardSpawn ? HOLD_HARD : HOLD) || t == null) {
                    AbyssRooms.clearSqueeze(level, rs);
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
            if (!AbyssRooms.inSqueezeZone(s, openMask, p, 0)) continue;
            boolean hit = AbyssRooms.inSqueezeZone(s, openMask, p, AbyssSqueeze.TOLERANCE);
            AbyssRooms.pushToOpen(sp, s, openMask, 0.05); // 壁の中に埋めない
            if (hit) {
                RoomsDamage.hurt(this, p, hardDamage(DAMAGE, DAMAGE_HARD));
                RoomsDebug.log("A-250 crushed {} shape={}", p.getGameProfile().getName(), shape);
            }
        }
        AbyssRooms.setSqueeze(level, rs, openMask);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 3.0f, 0.5f);
        state = State.CLOSED; stateTicks = 0;
    }

    /** 予告：壁になるマスの中にだけ壁の粉を落とす（プレイヤーの上下数ブロック）。残るマスには何も出さない。 */
    private void dust(ServerLevel level, RoomsState rs) {
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.FALLING_DUST, Sections.A.palette().wall().get());
        double cx = cx(rs.abyss), cz = cz(rs.abyss);
        for (Player p : level.players()) if (p instanceof ServerPlayer sp && inShaft(rs, p))
            for (int dx = -AbyssRooms.IN; dx <= AbyssRooms.IN; dx++) for (int dz = -AbyssRooms.IN; dz <= AbyssRooms.IN; dz++) {
                if (AbyssSqueeze.open(openMask, dx, dz)) continue;
                for (int i = 0; i < 2; i++)
                    level.sendParticles(sp, opt, true, cx + dx + (random.nextDouble() - 0.5) * 0.8, p.getY() + (random.nextDouble() - 0.35) * 7, cz + dz + (random.nextDouble() - 0.5) * 0.8, 1, 0, 0, 0, 0);
            }
    }

    @Override
    public void onRemovedFromLevel() {
        // 消えるときは必ず壁を戻す（チャンクの読み込み解除・終了時は戻さない：読み込み後の refresh が縦穴の列を片付ける）
        if (level() instanceof ServerLevel sl && state == State.CLOSED) {
            RemovalReason r = getRemovalReason();
            if (r == null || r.shouldDestroy()) AbyssRooms.clearSqueeze(sl, RoomsState.get(sl));
        }
        super.onRemovedFromLevel();
    }

    @Override protected String debugState() { return "state=" + state + " t=" + stateTicks + " shape=" + shape + " warn=" + warn + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("State", state.name()); t.putInt("StateTicks", stateTicks); t.putInt("OpenMask", openMask); t.putInt("Warn", warn); t.putString("Shape", shape);
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        try { state = State.valueOf(t.getString("State")); } catch (IllegalArgumentException e) { state = State.WARN; }
        stateTicks = t.getInt("StateTicks");
        openMask = t.contains("OpenMask") ? t.getInt("OpenMask") : AbyssSqueeze.mask(AbyssSqueeze.Shape.CENTER, 0);
        warn = t.contains("Warn") ? t.getInt("Warn") : AbyssSqueeze.MIN_WARN; shape = t.getString("Shape");
    }
}
