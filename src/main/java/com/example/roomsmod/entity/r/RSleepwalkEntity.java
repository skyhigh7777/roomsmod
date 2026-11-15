package com.example.roomsmod.entity.r;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * R-230（寝歩き）。H-30 と同じく遅く徘徊する。通路で止まっていれば見逃す。歩いていると見える範囲で当たる。
 * テーブルの下（ベッドの下）は覗き込まれる（{@link #findsUnderTable}）。ロッカーなら安全。
 * R-100 / R-200 とは同時に出ない（r_family）。
 */
public class RSleepwalkEntity extends VPathWalker {
    public static final double SPEED = 0.12, HIT_RANGE = 5.0, TABLE_RANGE = 6.0, MOVE_EPS = 0.06;
    public static final int GRACE = 60, ROOMS_BEHIND = 1;
    public static final float DAMAGE = 25f;

    public RSleepwalkEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-230"; }
    @Override public String homeSection() { return "r"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected int loopTicks() { return 25; }
    @Override protected boolean chainsOnFinish() { return false; }
    @Override protected SoundEvent approachSound() { return SoundEvents.WOOL_STEP; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, rs.roomPaths.size() - 1);
        RoomsDebug.log("{} start room={} playerRoom={} hard={} offSection={}", vName(), from, playerRoom, hardSpawn, offSection);
        return s;
    }

    /** 止まっている人は見逃す。歩いているか、テーブルの下にいると見える。 */
    @Override public boolean canSeeVirtual(Player t) {
        if (LockerManager.isUnderTable(t) && virtualDistanceTo(t) <= TABLE_RANGE) return true;
        Vec3 d = t.getDeltaMovement();
        boolean moving = Math.sqrt(d.x * d.x + d.z * d.z) > MOVE_EPS || t.isSprinting();
        if (!moving) return false;
        return super.canSeeVirtual(t);
    }

    public static boolean findsUnderTable(net.minecraft.world.entity.Entity attacker, Player p) {
        return attacker instanceof RSleepwalkEntity s && LockerManager.isUnderTable(p) && s.virtualDistanceTo(p) <= TABLE_RANGE;
    }

    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 10 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 1.6, getZ(), 0, 0.01, 0);
    }
}
