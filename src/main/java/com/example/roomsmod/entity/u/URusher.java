package com.example.roomsmod.entity.u;

import com.example.roomsmod.entity.A20Entity;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.HardMode;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * U 区画の動き型エンティティ共通（突進・リバウンド・往復）。経路は VPathWalker、加減速つき。
 * <ul>
 *   <li>折り返し：経路の終点で turnPause 待ってから、turnRooms 部屋ぶん逆向きに走る（turns 回）。終点の手前で decel で減速する。</li>
 *   <li>出現ノイズ：SpawnNoise（なし / 1回の衝撃音 / 折り返し回数+1 回のチャイム）。突進ノイズ：rushNoise() の間は接近ループ音。</li>
 *   <li>当たりは RusherHits（隠れる・遮蔽で回避）。</li>
 *   <li>U 以外の部屋に出たとき（ハードの合同抽選）：速度 x{@value #OFF_SECTION_SPEED_MUL}、猶予 x{@value #OFF_SECTION_GRACE_MUL}、
 *       加速は少し強く・最低速度を上げて、曲がり角の多い部屋でも止まりかけないようにする。</li>
 * </ul>
 */
public abstract class URusher extends VPathWalker {
    public static final double OFF_SECTION_SPEED_MUL = 0.85;
    public static final double OFF_SECTION_GRACE_MUL = 1.25;
    /** U 以外の部屋での最低速度（最高速に対する割合）。 */
    public static final double OFF_SECTION_MIN_SPEED_FRAC = 0.4;
    public static final double OFF_SECTION_ACCEL_MUL = 1.5;

    public enum SpawnNoise { NONE, BOOM, CHIMES }

    protected int turnsDone = 0, pause = 0, chimes = 0;

    protected URusher(EntityType<? extends Mob> type, Level level) { super(type, level); }

    // ---- 種類ごとの値 ----
    protected abstract double baseAccel();
    protected abstract double baseDecel();
    /** 出現位置：プレイヤーの部屋から何部屋後ろ（負なら前）。 */
    protected abstract int roomsBehind();
    /** 最初の経路の終点：プレイヤーの部屋から何部屋先（負なら後ろ）。 */
    protected int roomsAhead() { return 1; }
    /** 折り返す回数（ハードで +1）。 */
    protected int maxTurns() { return 0; }
    /** 折り返したあと何部屋走るか。 */
    protected int turnRooms() { return 0; }
    protected int turnPause() { return 30; }
    protected abstract SpawnNoise spawnNoise();
    protected abstract boolean rushNoise();
    protected abstract Vector3f color();
    protected float noisePitch() { return 0.6f; }

    @Override public String homeSection() { return "u"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected float damage() { return 40f; }

    /** 実際の折り返し回数。 */
    protected final int turns() { int n = maxTurns(); return n > 0 && hardSpawn ? n + HardMode.EXTRA_TURNS : n; }

    @Override protected double accel() { double a = baseAccel(); return offSection ? a * OFF_SECTION_ACCEL_MUL : a; }
    @Override protected double decel() { return baseDecel(); }
    @Override protected boolean brakeAtPathEnd() { return turnsDone < turns(); }
    @Override protected double minMoveSpeed() { return offSection ? Math.max(super.minMoveSpeed(), maxSpeed() * OFF_SECTION_MIN_SPEED_FRAC) : super.minMoveSpeed(); }
    @Override protected double speedMul() { return super.speedMul() * (offSection ? OFF_SECTION_SPEED_MUL : 1.0); }
    @Override protected double graceMul() { return super.graceMul() * (offSection ? OFF_SECTION_GRACE_MUL : 1.0); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int last = rs.roomPaths.size() - 1;
        int from = Math.max(0, Math.min(last, playerRoom - roomsBehind()));
        int to = Math.max(0, Math.min(last, playerRoom + roomsAhead()));
        BlockPos at = placeAtStart(rs, from, to);
        RoomsDebug.log("{} prepare playerRoom={} rooms {}->{} hard={} offSection={} turns={} grace={} vmax={}",
                vName(), playerRoom, from, to, hardSpawn, offSection, turns(), effectiveGraceTicks(), f(maxSpeed()));
        return at;
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        switch (spawnNoise()) {
            case BOOM -> { if (chimes == 0) { chimes = 1; A20Entity.playApproachCue(level, this, SoundEvents.WARDEN_SONIC_BOOM, noisePitch()); } }
            case CHIMES -> {
                if (chimes <= turns() && tickCount % 10 == 1) {
                    level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, noisePitch());
                    chimes++;
                }
            }
            case NONE -> {}
        }
        if (life == 1) RoomsDebug.announce(playersNear(160), vName() + " が来るわ（折り返し " + turns() + " 回）",
                "{} telegraph noise={} rush={} turns={} rooms {}->{}", vName(), spawnNoise(), rushNoise(), turns(), startRoom, endRoom);
        if (super.holdTick(level)) return true;
        if (pause > 0) { pause--; return true; }
        return false;
    }

    @Override
    protected boolean onPathEnd(ServerLevel level) {
        if (turnsDone >= turns()) return false;
        turnsDone++;
        pause = turnPause();
        resetSpeed();
        boolean wasForward = endRoom >= startRoom;
        int target = wasForward ? endRoom - turnRooms() : endRoom + turnRooms();
        setPath(level, endRoom, target);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0f, noisePitch() + 0.1f);
        RoomsDebug.log("{} turnaround {}/{} -> rooms {}->{} pause={}", vName(), turnsDone, turns(), startRoom, endRoom, pause);
        return true;
    }

    @Override
    protected void clientTick() {
        if (rushNoise()) super.clientTick();
        for (int i = 0; i < 3; i++) {
            DustParticleOptions d = new DustParticleOptions(i == 0 ? new Vector3f(0.95f, 0.95f, 0.95f) : color(), 1.5f);
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 1.4, getY() + random.nextDouble() * 2.0, getZ() + (random.nextDouble() - 0.5) * 1.4, 0, 0, 0);
        }
    }

    @Override protected String debugState() { return super.debugState() + " turns=" + turnsDone + "/" + turns() + " pause=" + pause; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("TurnsDone", turnsDone); t.putInt("Pause", pause); t.putInt("Chimes", chimes); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); turnsDone = t.getInt("TurnsDone"); pause = t.getInt("Pause"); chimes = t.getInt("Chimes"); }
}
