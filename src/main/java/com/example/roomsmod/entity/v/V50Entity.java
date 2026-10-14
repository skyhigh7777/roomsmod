package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** V-50：前方（生成済みの最新の部屋の出口）から経路を逆向きにゆっくり歩いて来る。隠れてやり過ごす。 */
public class V50Entity extends VPathWalker {
    /** プレイヤーの何部屋後ろまで歩いて消えるか。 */
    public static final int ROOMS_BEHIND = 2;
    public static final double SPEED = 0.2;      // ブロック/tick（歩き）
    public static final int GRACE_TICKS = 60;
    public static final double HIT_RANGE = 16.0;
    public static final float DAMAGE = 8.0f;

    public V50Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public String vName() { return "V-50"; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE_TICKS; }
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

    @Override
    protected void clientTick() {
        super.clientTick();
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SQUID_INK, getX(), getY() + 1.0 + random.nextDouble(), getZ(), 0, 0.02, 0);
    }
}
