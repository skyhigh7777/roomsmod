package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * V-50：前方（生成済みの最新の部屋の出口）に潜み、
 * 警告（時計のチキチキ音）→ ブザー → 爆発音と共に経路を逆向きに高速で突進して来る。
 * 当たり判定（視線・隠れ場所ルール）は突進開始からのみ有効。
 */
public class V50Entity extends VPathWalker {
    /** プレイヤーの何部屋後ろまで走って消えるか。 */
    public static final int ROOMS_BEHIND = 2;
    /** 警告（時計の音）の長さ（tick）。 */
    public static final int TICK_WARN_TICKS = 70;
    /** 時計音ファイル（1秒）の再生間隔。 */
    public static final int TICK_SOUND_INTERVAL = 20;
    /** ブザーの長さ（tick、音声は0.5秒）。 */
    public static final int BUZZER_TICKS = 10;
    /** 突進速度（ブロック/tick、A-20=1.8, A-40=2.25）。 */
    public static final double RUSH_SPEED = 2.4;
    public static final double HIT_RANGE = 16.0;
    public static final float DAMAGE = 8.0f;
    public static final float WARN_VOLUME = 1.0f;

    /** 0=待機前, 1=警告, 2=ブザー, 3=突進 */
    private int phase = 0;
    private int phaseTicks = 0;

    public V50Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public String vName() { return "V-50"; }
    @Override protected double speed() { return RUSH_SPEED; }
    @Override protected int graceTicks() { return TICK_WARN_TICKS + BUZZER_TICKS; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent approachSound() { return RoomsMod.V50_APPROACH.get(); }
    @Override protected int loopTicks() { return 60; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int ahead = rs.roomPaths.size() - 1; // 扉を開けて生成された次の部屋（最も前方）
        if (ahead <= playerRoom) return null; // 前方に部屋がない
        return placeAtStart(rs, ahead, Math.max(0, playerRoom - ROOMS_BEHIND));
    }

    /** 警告音はプレイヤーの位置で（非定位で）鳴らし、どこにいても確実に聞こえるようにする。 */
    private void notifyAll(ServerLevel level, SoundEvent s) {
        for (ServerPlayer p : level.players()) p.playNotifySound(s, SoundSource.HOSTILE, WARN_VOLUME, 1.0f);
    }

    private void enter(int ph, String what) {
        phase = ph; phaseTicks = 0;
        RoomsDebug.log("V-50 phase {} ({})", what, debugState());
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        if (phase == 0) enter(1, "warn");
        if (phase == 1) {
            if (phaseTicks % TICK_SOUND_INTERVAL == 0) notifyAll(level, RoomsMod.V50_TICK.get());
            if (++phaseTicks >= TICK_WARN_TICKS) { enter(2, "buzzer"); notifyAll(level, RoomsMod.V50_BUZZER.get()); }
            return true;
        }
        if (phase == 2) {
            if (++phaseTicks >= BUZZER_TICKS) {
                enter(3, "burst");
                notifyAll(level, RoomsMod.V50_EXPLODE.get());
                level.playSound(null, getX(), getY(), getZ(), RoomsMod.V50_EXPLODE.get(), SoundSource.HOSTILE, 4.0f, 0.9f);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
                RoomsDebug.log("V-50 phase rush (speed {}, hits enabled)", RUSH_SPEED);
            }
            return true;
        }
        return false; // 突進中：移動＋当たり判定
    }

    @Override
    protected void clientTick() {
        // クライアントは phase を知らないので、出現からの経過で突進中の演出を出す
        if (tickCount < TICK_WARN_TICKS + BUZZER_TICKS) return;
        super.clientTick();
        level().addParticle(ParticleTypes.SQUID_INK, getX(), getY() + 1.0 + random.nextDouble(), getZ(), 0, 0.02, 0);
    }

    @Override
    protected String debugState() { return super.debugState() + " phase=" + phase; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase); tag.putInt("PhaseTicks", phaseTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        phase = tag.getInt("Phase"); phaseTicks = tag.getInt("PhaseTicks");
        super.readAdditionalSaveData(tag);
    }
}
