package com.example.roomsmod.entity.h;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * H-230（感染）。H-30 と同じく 1 つ後ろの部屋から最新の部屋までゆっくり歩き、通った床を TRAIL_TICKS の間汚染する。
 * 汚染の上に立っているプレイヤーは TICK_EVERY tick ごとに DAMAGE と弱体化。隠れていても床に立っていれば当たる（通った跡を避ける）。
 * 本体の当たり（RusherHits）は使わない。経路の終点でも跡が消えるまで残る。
 */
public class HInfectionEntity extends VPathWalker {
    public static final double SPEED = 0.13;
    public static final int GRACE = 60, ROOMS_BEHIND = 1, TRAIL_TICKS = 80, TICK_EVERY = 15, WEAK_TICKS = 60;
    public static final float DAMAGE = 8f;
    private final Map<BlockPos, Integer> trail = new LinkedHashMap<>();
    private boolean windingDown = false;
    private final Map<java.util.UUID, Integer> lastHurt = new LinkedHashMap<>();

    public HInfectionEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "H-230"; }
    @Override public String homeSection() { return "h"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return 0.1; } // 本体の当たりは使わない（汚染だけ）
    @Override protected float damage() { return 0f; }
    @Override protected int loopTicks() { return 30; }
    @Override protected boolean chainsOnFinish() { return false; }
    @Override protected SoundEvent approachSound() { return SoundEvents.BOTTLE_FILL; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, rs.roomPaths.size() - 1);
        RoomsDebug.log("{} start room={} playerRoom={} hard={} offSection={}", vName(), from, playerRoom, hardSpawn, offSection);
        return s;
    }

    private BlockPos cell() {
        return new BlockPos((int) Math.floor(getX()), (int) Math.floor(getY()), (int) Math.floor(getZ()));
    }

    private void tickTrail(ServerLevel level, boolean stamp) {
        if (stamp && !windingDown) trail.put(cell(), TRAIL_TICKS);
        for (Iterator<Map.Entry<BlockPos, Integer>> it = trail.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Integer> e = it.next();
            int left = e.getValue() - 1;
            if (left <= 0) it.remove();
            else e.setValue(left);
            if (left > 0 && left % 4 == 0)
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, e.getKey().getX() + 0.5, e.getKey().getY() + 0.15, e.getKey().getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0);
        }
        if (life % 20 == 1 && !trail.isEmpty())
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.BOTTLE_FILL, SoundSource.HOSTILE, 0.7f, 0.6f);
        for (Player p : playersNear(48)) {
            if (p.isSpectator()) continue;
            BlockPos at = new BlockPos((int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
            if (!trail.containsKey(at) && !trail.containsKey(at.below())) continue;
            int since = lastHurt.getOrDefault(p.getUUID(), -TICK_EVERY);
            if (life - since < TICK_EVERY) continue;
            lastHurt.put(p.getUUID(), life);
            RoomsDebug.log(p, vName() + ": 汚染の床に乗っている", "{} contaminate player={} pos={}", vName(), p.getGameProfile().getName(), at);
            if (RoomsDamage.hurt(this, p, DAMAGE))
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0));
            p.displayClientMessage(Component.translatable("message.roomsmod.h230_infect"), true);
        }
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        if (super.holdTick(level)) return true;
        if (windingDown) {
            tickTrail(level, false);
            if (trail.isEmpty()) leave("trail gone");
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPathEnd(ServerLevel level) {
        windingDown = true;
        tickTrail(level, false);
        RoomsDebug.log("{} path end, winding down trail={}", vName(), trail.size());
        return true;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (!isRemoved() && !windingDown) tickTrail(level, true);
    }

    @Override protected void clientTick() {
        DustParticleOptions d = new DustParticleOptions(new Vector3f(0.35f, 0.7f, 0.3f), 1.2f);
        if (tickCount % 3 == 0)
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 0.7, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 0.7, 0, 0.02, 0);
        if (tickCount % 6 == 0) level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.8, getZ(), 0, 0.02, 0);
    }

    @Override protected String debugState() { return super.debugState() + " trail=" + trail.size() + (windingDown ? " winding" : ""); }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putBoolean("Winding", windingDown);
        ListTag list = new ListTag();
        for (var e : trail.entrySet()) {
            CompoundTag n = new CompoundTag();
            n.putInt("x", e.getKey().getX()); n.putInt("y", e.getKey().getY()); n.putInt("z", e.getKey().getZ());
            n.putInt("t", e.getValue());
            list.add(n);
        }
        t.put("Trail", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        windingDown = t.getBoolean("Winding");
        trail.clear();
        for (var n0 : t.getList("Trail", 10)) {
            CompoundTag n = (CompoundTag) n0;
            trail.put(new BlockPos(n.getInt("x"), n.getInt("y"), n.getInt("z")), n.getInt("t"));
        }
    }
}
