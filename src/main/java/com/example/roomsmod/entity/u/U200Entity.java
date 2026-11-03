package com.example.roomsmod.entity.u;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * U-200：後ろの部屋から経路に沿って追ってくる。歩きより少し速く、ダッシュより遅い。
 * 隠れていない人が見えないと少し速くなる。経路の終点で少しうろついてから去る。突進ノイズはなく足音だけ。
 */
public class U200Entity extends URusher {
    public static final double SPEED = 0.24, LOST_SPEED = 0.32, ACCEL = 0.01;
    public static final int ROOMS_BEHIND = 3, GRACE = 60, LINGER = 60, LIFE = 20 * 45, STEP_INTERVAL = 8;
    public static final double HIT_RANGE = 2.5, SIGHT = 48.0;
    private boolean lingered = false, seesSomeone = true;

    public U200Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "U-200"; }
    @Override protected int maxLifeTicks() { return LIFE; }
    @Override protected double speed() { return seesSomeone ? SPEED : LOST_SPEED; }
    @Override protected double baseAccel() { return ACCEL; }
    @Override protected double baseDecel() { return ACCEL; }
    @Override protected double minMoveSpeed() { return 0.1; }
    @Override protected int roomsBehind() { return ROOMS_BEHIND; }
    @Override protected int roomsAhead() { return 1; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected SpawnNoise spawnNoise() { return SpawnNoise.BOOM; }
    @Override protected float noisePitch() { return 0.4f; }
    @Override protected boolean rushNoise() { return false; }
    @Override protected boolean chainsOnFinish() { return false; } // 突進型ではない
    @Override protected SoundEvent approachSound() { return RoomsMod.U200_STEP.get(); }
    @Override protected Vector3f color() { return new Vector3f(0.2f, 0.5f, 0.95f); }

    @Override
    protected boolean holdTick(ServerLevel level) {
        if (super.holdTick(level)) return true;
        if (tickCount % 10 == 0) {
            boolean sees = false;
            for (Player p : playersNear(SIGHT)) if (!HidingRules.isHiding(p) && canSee(p)) { sees = true; break; }
            if (sees != seesSomeone) RoomsDebug.log("{} {} target (speed -> {})", vName(), sees ? "sees" : "lost", f(sees ? SPEED : LOST_SPEED));
            seesSomeone = sees;
        }
        if (tickCount % STEP_INTERVAL == 0) level.playSound(null, getX(), getY(), getZ(), RoomsMod.U200_STEP.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
        return false;
    }

    @Override
    protected boolean onPathEnd(ServerLevel level) {
        if (lingered) return false;
        lingered = true;
        pause = LINGER;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.HOSTILE, 2.0f, 0.6f);
        RoomsDebug.log("{} reached path end, lingering {} ticks", vName(), LINGER);
        return true;
    }

    @Override protected String debugState() { return super.debugState() + " sees=" + seesSomeone + " lingered=" + lingered; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putBoolean("Lingered", lingered); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); lingered = t.getBoolean("Lingered"); }
}
