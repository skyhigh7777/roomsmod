package com.example.roomsmod.entity.h;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * H-30（徘徊する患者）。1 つ後ろの部屋の入口から、最新の部屋の出口までゆっくり歩き続ける（新しい部屋ができれば延長）。
 * ときどき立ち止まって咳き込む（その間は動かず、当たりもない）。歩く速さはプレイヤーより遅い。
 * 当たりは RusherHits（HIT_RANGE 以内で見えているプレイヤー。隠れていれば・遮蔽物の陰なら助かる）。
 * 先へ進んで距離を取るか、隠れてやり過ごす。
 */
public class HPatientEntity extends VPathWalker {
    public static final double SPEED = 0.15;
    public static final int GRACE = 60, ROOMS_BEHIND = 1;
    public static final double HIT_RANGE = 5.0;
    public static final float DAMAGE = 25f;
    /** 歩いている時間（この tick 数ごとに PAUSE_CHANCE で立ち止まる）。 */
    public static final int PAUSE_EVERY = 100, PAUSE_TICKS = 40;
    public static final float PAUSE_CHANCE = 0.6f;
    private int pause = 0;

    public HPatientEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-30"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected int loopTicks() { return 20; }
    @Override protected boolean chainsOnFinish() { return false; }
    @Override protected SoundEvent approachSound() { return RoomsMod.H30_DRAG.get(); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, rs.roomPaths.size() - 1);
        RoomsDebug.log("{} start room={} playerRoom={} hard={} offSection={}", vName(), from, playerRoom, hardSpawn, offSection);
        return s;
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        if (super.holdTick(level)) return true;
        if (pause > 0) { pause--; return true; }
        if (life % PAUSE_EVERY == 0 && random.nextFloat() < PAUSE_CHANCE) {
            pause = PAUSE_TICKS;
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.H30_DRAG.get(), SoundSource.HOSTILE, 1.2f, 0.6f);
            RoomsDebug.log("{} pause {} ticks", vName(), PAUSE_TICKS);
            return true;
        }
        return false;
    }

    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 8 == 0) level().addParticle(ParticleTypes.FALLING_DRIPSTONE_WATER, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 1.2, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
    }

    @Override protected String debugState() { return super.debugState() + " pause=" + pause; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Pause", pause); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); pause = t.getInt("Pause"); }
}
