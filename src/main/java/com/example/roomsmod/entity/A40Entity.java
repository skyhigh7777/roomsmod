package com.example.roomsmod.entity;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * B-40：B-20 と同様に後方から突進し、最深部屋の終端で折り返して数部屋戻り、
 * 再び最深部屋の終端まで突進してから消える。各往路・復路で視線判定ダメージ。
 */
public class A40Entity extends Mob {
    public static final int DEFAULT_ROOMS_BACK = 6;
    private static final double MAX_RAYCAST_DISTANCE = 22.5;
    private static final float DAMAGE = 8.0f;
    /** 移動速度（ブロック/tick、全フェーズ共通。調整用）。旧実効1.5（0.75がtravelで二重適用されていた）の1.5倍。 */
    public static final double SPEED = 2.25;
    private final WaypointMover mover = new WaypointMover();
    private final boolean[] reachedBuf = new boolean[1];
    private static final int GRACE_TICKS = 80;
    private static final int TURN_PAUSE = 20;
    private static final int RETURN_PAUSE = 12;

    // フェーズ: 0=待機 1=前進 2=折返し停止 3=後退 4=停止 5=再前進
    private int phase = 0;
    /** 移動中フラグ（phaseはクライアントへ同期されないため、ループ音用に同期する）。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_MOVING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(A40Entity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOVING, false);
    }
    private int phaseTicks = 0;
    private int startRoom = 0;
    private int roomsBack = DEFAULT_ROOMS_BACK;
    /** 出現時にハードモードだったか（猶予 -20%・速度 +10%・折り返し +1 回）。 */
    private boolean hard = false;
    /** ハードで追加した折り返しの回数。 */
    private int extraCycles = 0;
    public void setHard(boolean h) { this.hard = h; }
    private int waypointIndex = 0;
    private int backTargetIndex = 0;
    private final List<BlockPos> waypoints = new ArrayList<>();
    /** 各部屋の経路が waypoints 内で始まるインデックス。 */
    private final List<Integer> roomStartIdx = new ArrayList<>();
    private boolean waypointsBuilt = false;
    private int lastPathPointCount = 0;
    private ChunkPos lastForcedChunk = null;

    public A40Entity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
    }

    public void setStartRoom(int index) { this.startRoom = index; }
    public void setRoomsBack(int n) { this.roomsBack = Math.max(1, n); }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 1.2)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override public boolean isAlwaysTicking() { return true; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected boolean canRide(Entity entity) { return false; }

    private int expectedPointCount(RoomsState rs) {
        int n = 0;
        for (int i = Math.max(0, startRoom); i < rs.roomPaths.size(); i++) n += rs.roomPaths.get(i).size();
        return n;
    }

    private void buildWaypoints() {
        waypoints.clear();
        roomStartIdx.clear();
        waypointsBuilt = true;
        if (!(this.level() instanceof ServerLevel sl)) return;
        RoomsState rs = RoomsState.get(sl);
        for (int i = Math.max(0, startRoom); i < rs.roomPaths.size(); i++) {
            roomStartIdx.add(waypoints.size());
            for (BlockPos p : rs.roomPaths.get(i)) {
                if (waypoints.isEmpty() || !waypoints.get(waypoints.size() - 1).equals(p)) waypoints.add(p);
            }
        }
        lastPathPointCount = expectedPointCount(rs);
        waypointIndex = Math.max(0, Math.min(waypointIndex, waypoints.size() - 1));
    }

    private void refreshIfGrown() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        if (expectedPointCount(RoomsState.get(sl)) > lastPathPointCount) {
            List<BlockPos> old = new ArrayList<>(waypoints);
            int oldIdx = waypointIndex;
            int oldBack = backTargetIndex;
            buildWaypoints();
            waypointIndex = WaypointMover.preserveIndex(old, oldIdx, waypoints);
            backTargetIndex = WaypointMover.preserveIndex(old, oldBack, waypoints);
            if (waypointIndex != oldIdx) RoomsDebug.log("B-40 path extend remapped waypoint {} -> {}", oldIdx, waypointIndex);
            RoomsDebug.log("B-40 path extended: waypoints={}", waypoints.size());
        }
    }

    /** 最深部屋から roomsBack 部屋戻った部屋の開始インデックス（最初の部屋でクランプ）。 */
    private int computeBackTarget() {
        if (roomStartIdx.isEmpty()) return 0;
        int deepest = roomStartIdx.size() - 1;
        int target = Math.max(0, deepest - roomsBack);
        return Math.min(roomStartIdx.get(target), Math.max(0, waypoints.size() - 1));
    }

    /** 目標インデックスへ1tick分移動。到達したら true。 */
    private boolean stepToward(int targetIdx) {
        waypointIndex = mover.step(this, "B-40", waypoints, waypointIndex, targetIdx, com.example.roomsmod.world.HardMode.speed(SPEED, hard), reachedBuf);
        return reachedBuf[0];
    }


    /** 位置は WaypointMover で直接動かす。バニラの travel による二重移動・クライアント側の速度外挿を無効化。 */
    @Override
    public void travel(Vec3 input) { this.setDeltaMovement(Vec3.ZERO); }

    private void setPhase(int p) {
        RoomsDebug.log("B-40 phase {} -> {} waypoint={}/{}", phase, p, waypointIndex, waypoints.size());
        phase = p;
        phaseTicks = 0;
        mover.resetProgress(this);
    }

    @Override
    public void tick() {
        super.tick();
        // 輪郭（壁越しに見える）はデバッグ時のみ。OFF にしたら既存の個体も消える
        if (!level().isClientSide() && hasGlowingTag() != com.example.roomsmod.util.RoomsDebug.isEnabled()) setGlowingTag(com.example.roomsmod.util.RoomsDebug.isEnabled());
        this.noPhysics = true;
        if (this.level().isClientSide()) {
            spawnVisualParticles();
            // 接近ループ音（移動中のみ1秒ごと、エンティティに追従するクライアント側サウンド）
            if (this.entityData.get(DATA_MOVING) && this.tickCount % 20 == 0) {
                com.example.roomsmod.util.ApproachSoundHook.start(this, com.example.roomsmod.RoomsMod.A40_APPROACH.get());
            }
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        updateForcedChunks(sl);
        if (!waypointsBuilt) buildWaypoints();
        phaseTicks++;
        int end = Math.max(0, waypoints.size() - 1);

        switch (phase) {
            case 0 -> {
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTicks >= com.example.roomsmod.world.HardMode.grace(GRACE_TICKS, hard)) setPhase(1);
            }
            case 1, 5 -> {
                if (this.tickCount % 20 == 0) { refreshIfGrown(); end = Math.max(0, waypoints.size() - 1); }
                if (stepToward(end)) {
                    if (phase == 1) {
                        setPhase(2);
                        playCue(sl);
                    } else if (hard && extraCycles < com.example.roomsmod.world.HardMode.EXTRA_TURNS) {
                        extraCycles++;
                        RoomsDebug.log("B-40 hard extra turnaround {}/{}", extraCycles, com.example.roomsmod.world.HardMode.EXTRA_TURNS);
                        setPhase(2);
                        playCue(sl);
                    } else {
                        RoomsDebug.log("B-40 despawn: finished second rush at waypoint {}/{}", waypointIndex, waypoints.size());
                        this.discard();
                        com.example.roomsmod.world.HardMode.chainReroll(sl, "B-40");
                        return;
                    }
                }
            }
            case 2 -> {
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTicks >= TURN_PAUSE) {
                    backTargetIndex = computeBackTarget();
                    setPhase(3);
                }
            }
            case 3 -> {
                if (stepToward(backTargetIndex)) {
                    setPhase(4);
                    playCue(sl);
                }
            }
            case 4 -> {
                this.setDeltaMovement(Vec3.ZERO);
                if (phaseTicks >= RETURN_PAUSE) setPhase(5);
            }
            default -> setPhase(0);
        }
        this.setDeltaMovement(Vec3.ZERO);

        // 保険：異常に長生きしたら消滅
        if (this.tickCount > 12000) {
            RoomsDebug.log("B-40 despawn: timeout phase={}", phase);
            this.discard();
            return;
        }

        if (this.tickCount % 200 == 0 && RoomsDebug.isEnabled()) {
            Player nearest = sl.getNearestPlayer(this, 500);
            double nd = nearest != null ? this.distanceTo(nearest) : -1;
            RoomsDebug.log(nearest,
                nearest != null ? "B-40 phase " + phase + " Z: " + String.format("%.1f", this.getZ()) + " (player Z: " + String.format("%.1f", nearest.getZ()) + ")" : null,
                "B-40 progress phase={} waypoint={}/{} pos=({}, {}, {}) nearestPlayerDist={}",
                phase, waypointIndex, waypoints.size(), String.format("%.1f", this.getX()), String.format("%.1f", this.getY()), String.format("%.1f", this.getZ()), String.format("%.1f", nd));
        }

        boolean moving = phase == 1 || phase == 3 || phase == 5;
        if (this.tickCount == 1) playNearPlayer(sl, SoundEvents.WARDEN_SONIC_BOOM, 0.6f);
        this.entityData.set(DATA_MOVING, moving);

        if (moving) {
            AABB box = this.getBoundingBox().inflate(MAX_RAYCAST_DISTANCE);
            for (Player player : this.level().getEntitiesOfClass(Player.class, box)) {
                if (player.isSpectator() || !player.isAlive()) continue;
                if (this.distanceTo(player) > MAX_RAYCAST_DISTANCE) continue;
                com.example.roomsmod.hiding.RusherHits.tryHit(this, "B-40", player, hasLineOfSight(player), DAMAGE);
            }
        }
    }

    private void playCue(ServerLevel sl) {
        playNearPlayer(sl, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f);
    }

    /** B-20 と同じ：プレイヤーから本体方向へ最大8ブロックの位置で鳴らし、遠くても届くようにする。 */
    private void playNearPlayer(ServerLevel sl, SoundEvent sound, float pitch) {
        A20Entity.playApproachCue(sl, this, sound, pitch); // 接近ループと同じ線形カーブ（以前は最低0.9固定で遠くでも大きかった）
    }

    private void spawnVisualParticles() {
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.1f, 0.95f, 0.9f), 2.0f);
        for (int i = 0; i < 8; i++) {
            this.level().addParticle(dust,
                this.getX() + (this.random.nextDouble() - 0.5) * 1.8,
                this.getY() + this.random.nextDouble() * 2.2,
                this.getZ() + (this.random.nextDouble() - 0.5) * 1.8, 0, 0.02, 0);
        }
    }

    private void updateForcedChunks(ServerLevel sl) {
        ChunkPos current = new ChunkPos(this.blockPosition());
        RusherChunks.move(sl, this, lastForcedChunk, current);
        lastForcedChunk = current;
    }

    /** 全ての除去理由（破棄・死亡・チャンク保存による退避・次元移動）をデバッグに出し、破棄時はチケットを外す。 */
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (this.level() instanceof ServerLevel sl) {
            RemovalReason r = this.getRemovalReason();
            RoomsDebug.log("B-40 removed from level reason={} saved={} pos=({}, {}, {})", r, r != null && r.shouldSave(),
                    String.format("%.1f", getX()), String.format("%.1f", getY()), String.format("%.1f", getZ()));
            if (r == null || r.shouldDestroy() || r == RemovalReason.CHANGED_DIMENSION) {
                if (lastForcedChunk != null) RusherChunks.force(sl, this, lastForcedChunk, false);
                lastForcedChunk = null;
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide()) RoomsDebug.log("B-40 removed reason={} phase={} waypoint={}/{}", reason, phase, waypointIndex, waypoints.size());
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("StartRoom", startRoom);
        tag.putInt("WaypointIndex", waypointIndex);
        tag.putInt("Phase", phase);
        tag.putInt("PhaseTicks", phaseTicks);
        tag.putInt("RoomsBack", roomsBack);
        tag.putInt("BackTarget", backTargetIndex);
        tag.putBoolean("HardSpawn", hard);
        tag.putInt("ExtraCycles", extraCycles);
        RoomsDebug.log("B-40 saved phase={} phaseTicks={} waypoint={}/{}", phase, phaseTicks, waypointIndex, waypoints.size());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        startRoom = tag.getInt("StartRoom");
        waypointIndex = tag.getInt("WaypointIndex");
        phase = tag.getInt("Phase");
        phaseTicks = tag.getInt("PhaseTicks");
        roomsBack = tag.contains("RoomsBack") ? tag.getInt("RoomsBack") : DEFAULT_ROOMS_BACK;
        backTargetIndex = tag.getInt("BackTarget");
        hard = tag.getBoolean("HardSpawn");
        extraCycles = tag.getInt("ExtraCycles");
        // 猶予は phaseTicks（保存される）で数えるので、ロードしても残り時間から再開する
        RoomsDebug.log("B-40 loaded phase={} phaseTicks={} waypoint={}", phase, phaseTicks, waypointIndex);
        waypointsBuilt = false;
    }

    private boolean hasLineOfSight(LivingEntity target) {
        Vec3 start = this.getEyePosition();
        Vec3 end = target.getEyePosition();
        BlockHitResult hit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return true;
        if (this.level().getBlockState(hit.getBlockPos()).is(Blocks.OAK_PLANKS)) return false;
        return start.distanceTo(hit.getLocation()) >= start.distanceTo(end) - 0.5;
    }
}
