package com.example.roomsmod.entity.r;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.u.URusher;
import com.example.roomsmod.hiding.LockerManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * R-100（家族）。テレビの砂嵐と足音（接近ループ）とともに 4 部屋後ろから家の中を駆け抜け、1 部屋先まで（折り返しなし）。動きは URusher。
 * <b>テーブルの下（ベッドの下・ちゃぶ台）は覗き込まれる</b>（HidingRules の例外 {@link #findsUnderTable}、TABLE_RANGE 以内なら見えていなくても当たる）。
 * ロッカー（クローゼット・押し入れ）なら助かる。
 */
public class RFamilyEntity extends URusher {
    public static final double SPEED = 1.25, ACCEL = 0.05, DECEL = 0.10, HIT_RANGE = 20.0, TABLE_RANGE = 7.0;
    public static final int ROOMS_BEHIND = 4, ROOMS_AHEAD = 1, GRACE = 80;
    public RFamilyEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-100"; }
    @Override public String homeSection() { return "r"; }
    @Override protected double speed() { return SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return DECEL; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return ROOMS_AHEAD; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.NONE; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected float noisePitch() { return 1.0f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.R100_STATIC.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.55f, 0.55f, 0.6f); }

    /** テーブルの下は覗き込まれる：近くなら見えていなくても当たる。 */
    @Override public boolean canSeeVirtual(Player t) {
        if (LockerManager.isUnderTable(t) && virtualDistanceTo(t) <= TABLE_RANGE) return true;
        return super.canSeeVirtual(t);
    }
    public static boolean findsUnderTable(net.minecraft.world.entity.Entity attacker, Player p) {
        return attacker instanceof RFamilyEntity f && LockerManager.isUnderTable(p) && f.virtualDistanceTo(p) <= TABLE_RANGE;
    }

    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + 1.4 + random.nextDouble() * 0.6, getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0, 0);
    }
}
