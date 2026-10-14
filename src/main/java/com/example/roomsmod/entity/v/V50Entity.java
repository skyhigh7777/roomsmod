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
    /** 待機位置：最新の部屋の出口扉からさらに奥（扉の向こう、まだ生成されていない側）へ何ブロック下がるか。扉越しに見えないように。 */
    public static final double HIDE_BEHIND_DOOR = 4.0;
    /** 警告・ブザー中に潜んでいる部屋の扉（入口または出口）を開けたプレイヤーへのダメージ（隠れ・視線は無視）。 */
    public static final float OPEN_DOOR_DAMAGE = 40.0f;

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
        BlockPos door = placeAtStart(rs, ahead, Math.max(0, playerRoom - ROOMS_BEHIND));
        if (door == null) return null;
        // 出口扉の向こう側（部屋の最後の区間の延長線上）へ下がる。経路の先頭は出口扉なので、突進開始時にまず扉まで来る。
        java.util.List<BlockPos> p = VSpawns.path(rs, ahead);
        if (p.size() >= 2) {
            BlockPos prev = p.get(p.size() - 2);
            net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(door.getX() - prev.getX(), 0, door.getZ() - prev.getZ());
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize().scale(HIDE_BEHIND_DOOR);
                setPos(door.getX() + 0.5 + dir.x, door.getY(), door.getZ() + 0.5 + dir.z);
            }
        }
        RoomsDebug.log("V-50 hides behind exit door {} at ({}, {}, {})", door, f(getX()), f(getY()), f(getZ()));
        return blockPosition();
    }

    /** 潜んでいる部屋の扉（入口・出口）か。経路の最初／最後の経由点（床+1）と比べる。 */
    private boolean isWaitingDoor(RoomsState rs, BlockPos door) {
        java.util.List<BlockPos> p = VSpawns.path(rs, startRoom);
        if (p.isEmpty()) return false;
        for (BlockPos q : new BlockPos[]{p.get(0), p.get(p.size() - 1)})
            if (Math.abs(q.getX() - door.getX()) <= 1 && Math.abs(q.getZ() - door.getZ()) <= 1 && Math.abs(q.getY() - door.getY()) <= 1) return true;
        return false;
    }

    /** DoorEventHandler から：扉が開けられた。警告／ブザー中で、潜んでいる部屋の扉なら罰して即突進。 */
    public static void onDoorOpened(ServerLevel level, RoomsState rs, net.minecraft.world.entity.player.Player player, BlockPos door) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (!(e instanceof V50Entity v) || !v.isAlive() || v.phase >= 3 || !v.isWaitingDoor(rs, door)) continue;
            RoomsDebug.log(player, "V-50: 扉を開けるのが早すぎたわ",
                    "V-50 door opened early -> punish player={} door={} phase={} creative={}", player.getGameProfile().getName(), door, v.phase, player.isCreative());
            if (!player.isCreative() && !player.isSpectator()) player.hurt(v.damageSources().mobAttack(v), OPEN_DOOR_DAMAGE);
            v.burst(level);
        }
    }

    private void burst(ServerLevel level) {
        enter(3, "burst");
        notifyAll(level, RoomsMod.V50_EXPLODE.get());
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.V50_EXPLODE.get(), SoundSource.HOSTILE, 4.0f, 0.9f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        RoomsDebug.log("V-50 phase rush (speed {}, hits enabled)", RUSH_SPEED);
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
            if (++phaseTicks >= BUZZER_TICKS) burst(level);
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
