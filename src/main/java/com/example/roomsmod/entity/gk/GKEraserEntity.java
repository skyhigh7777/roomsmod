package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.item.FlashlightHandler;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** GK-245：前方の部屋から逆向きに来る。 */
public class GKEraserEntity extends VPathWalker {
    public static final double FREEZE_RANGE = 14.0, FREEZE_DOT = 0.85;
    private int frozenTicks = 0;
    public GKEraserEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-245"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected double speed() { return 0.55; }
    @Override protected int graceTicks() { return 70; }
    @Override protected double hitRange() { return 10.0; }
    @Override protected float damage() { return 40f; }
    @Override protected SoundEvent approachSound() { return RoomsMod.GK_ERASER_SCRATCH.get(); }
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        int from = Math.min(rs.roomPaths.size() - 1, playerRoom + 1), to = Math.max(0, playerRoom - 3);
        if (from <= to) return null;
        return placeAtStart(rs, from, to);
    }
    @Override
    protected boolean holdTick(ServerLevel level) {
        if (super.holdTick(level)) return true;
        for (Player p : playersNear(FREEZE_RANGE)) {
            if (FlashlightHandler.heldOn(p).isEmpty() || !canSee(p)) continue;
            Vec3 to = getEyePosition().subtract(p.getEyePosition()).normalize();
            if (p.getViewVector(1f).dot(to) > FREEZE_DOT) {
                if (frozenTicks++ % 20 == 0) RoomsDebug.log(p, "GK-245: 光で止まっているわ", "GK-245 frozen by flashlight player={} frozenTicks={}", p.getGameProfile().getName(), frozenTicks);
                return true;
            }
        }
        return false;
    }
    @Override protected void clientTick() {
        super.clientTick();
        for (int i = 0; i < 3; i++) level().addParticle(ParticleTypes.ASH, getX() + random.nextDouble() - 0.5, getY() + random.nextDouble() * 2, getZ() + random.nextDouble() - 0.5, 0, 0, 0);
    }
    @Override protected String debugState() { return super.debugState() + " frozenTicks=" + frozenTicks; }
}
