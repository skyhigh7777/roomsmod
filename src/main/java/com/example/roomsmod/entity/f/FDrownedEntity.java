package com.example.roomsmod.entity.f;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * F-100（溺れた人影）。前から来る：次の部屋（なければプレイヤーの部屋）の出口から、プレイヤーの部屋を通って ROOMS_BEHIND 部屋後ろの入口までゆっくり歩き、去る。
 * 当たりは RusherHits（HIT_RANGE 以内で見えているプレイヤー）。まわり AURA_RANGE 以内の隠れていないプレイヤーは水に足を取られる（鈍化）ので、
 * 横をすり抜けるのは難しい。隠れてやり過ごす。
 */
public class FDrownedEntity extends VPathWalker {
    public static final double SPEED = 0.17, HIT_RANGE = 4.0, AURA_RANGE = 6.0;
    public static final int GRACE = 60, ROOMS_BEHIND = 2, AURA_EVERY = 10, AURA_TICKS = 30;
    public static final float DAMAGE = 30f;
    private final Set<UUID> told = new HashSet<>();

    public FDrownedEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-100"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected int loopTicks() { return 30; }
    @Override protected boolean chainsOnFinish() { return false; }
    @Override protected SoundEvent approachSound() { return RoomsMod.F100_GURGLE.get(); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.max(playerRoom, Math.min(playerRoom + 1, rs.roomPaths.size() - 1)), to = Math.max(0, playerRoom - ROOMS_BEHIND);
        BlockPos s = placeAtStart(rs, from, to);
        RoomsDebug.log("{} walks rooms {}->{} playerRoom={} hard={} offSection={}", vName(), from, to, playerRoom, hardSpawn, offSection);
        return s;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (isRemoved() || grace < effectiveGraceTicks() || life % AURA_EVERY != 0) return;
        for (Player p : playersNearVirtual(AURA_RANGE)) {
            if (p.isSpectator() || HidingRules.isHiding(p)) continue;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AURA_TICKS, 1));
            if (told.add(p.getUUID())) {
                GKUtil.actionbar(java.util.List.of(p), Component.translatable("message.roomsmod.f100_drag"));
                RoomsDebug.log(p, vName() + ": 足を取られた（鈍化）", "{} aura slows player={}", vName(), p.getGameProfile().getName());
            }
        }
    }

    @Override protected void clientTick() {
        super.clientTick();
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.DRIPPING_WATER, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + 1.6, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
        if (tickCount % 10 == 0) level().addParticle(ParticleTypes.BUBBLE_POP, getX() + (random.nextDouble() - 0.5), getY() + 0.5, getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
    }
}
