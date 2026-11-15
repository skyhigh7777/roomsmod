package com.example.roomsmod.entity.v;

import com.example.roomsmod.entity.gk.GKRoomWatcher;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * V-160（案山子）。村の道のそばに立ち、視線が通っているあいだに動くと攻撃。家の陰に入ってから動けばよい。
 * V-100（窓の覗き中はしゃがむ／止まる）や B-30（常に動くな）とは違い、「見られているときだけ止まる」。
 */
public class VScarecrowEntity extends GKRoomWatcher {
    public static final int TELEGRAPH_TICKS = 35, WATCH_TICKS = 180, LIMIT = 20, HARD_LIMIT = 12;
    public static final float DAMAGE = 30f;
    public static final double MOVE_EPS = 0.06;

    public VScarecrowEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "V-160"; }
    @Override public String homeSection() { return "v"; }
    @Override protected int telegraphTicks() { return hardSpawn ? 24 : TELEGRAPH_TICKS; }
    @Override protected int watchTicks() { return WATCH_TICKS; }
    @Override protected int limit() { return hardSpawn ? HARD_LIMIT : LIMIT; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent cue() { return SoundEvents.VILLAGER_NO; }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "V-160: 案山子が見張っている"; }

    @Override
    protected boolean inMyRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    @Override
    protected boolean violates(Player p) {
        if (!canSee(p)) return false;
        Vec3 d = p.getDeltaMovement();
        return Math.sqrt(d.x * d.x + d.z * d.z) > MOVE_EPS || p.isSprinting() || d.y > 0.15;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (isRemoved()) return;
        if (phaseTick == telegraphTicks() + 1)
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.v160_warn"));
    }

    @Override protected void clientTick() {
        if (tickCount % 8 == 0) level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 1.8, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
    }
}
