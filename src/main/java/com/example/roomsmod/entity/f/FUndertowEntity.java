package com.example.roomsmod.entity.f;

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
 * F-200（引き波）。5 部屋後ろから水の唸りとともに押し寄せ、1 部屋先まで走り抜ける（折り返しなし。ハードは HardMode の追加で 0 のまま）。
 * 動きは URusher（加減速つき）。<b>テーブルの下は水に浸かるので守られない</b>（HidingRules の例外、TABLE_RANGE 以内なら見えていなくても当たる）。
 * ロッカーに隠れる。
 */
public class FUndertowEntity extends URusher {
    public static final double SPEED = 1.5, ACCEL = 0.05, DECEL = 0.10, HIT_RANGE = 22.0, TABLE_RANGE = 6.0;
    public static final int ROOMS_BEHIND = 5, ROOMS_AHEAD = 1, GRACE = 80;
    public FUndertowEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-200"; }
    @Override public String homeSection() { return "f"; }
    @Override protected double speed() { return SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return DECEL; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return ROOMS_AHEAD; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.NONE; }
    @Override protected boolean rushNoise() { return true; }
    @Override protected float noisePitch() { return 0.7f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.F200_WAVE.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.2f, 0.45f, 0.6f); }

    /** テーブルの下は水が流れ込む：近くなら見えていなくても当たる（HidingRules の例外と組み合わせ）。 */
    @Override public boolean canSeeVirtual(Player t) {
        if (LockerManager.isUnderTable(t) && virtualDistanceTo(t) <= TABLE_RANGE) return true;
        return super.canSeeVirtual(t);
    }
    public static boolean floodsTable(net.minecraft.world.entity.Entity attacker, Player p) {
        return attacker instanceof FUndertowEntity u && LockerManager.isUnderTable(p) && u.virtualDistanceTo(p) <= TABLE_RANGE;
    }

    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SPLASH, getX() + (random.nextDouble() - 0.5) * 2.0, getY() + 0.3 + random.nextDouble() * 1.5, getZ() + (random.nextDouble() - 0.5) * 2.0, 0, 0.1, 0);
    }
}
