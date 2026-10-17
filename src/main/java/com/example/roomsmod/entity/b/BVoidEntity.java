package com.example.roomsmod.entity.b;

import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** B-Void。 */
public class BVoidEntity extends BRoomEntity {
    public static final int TELEGRAPH = 60, PULL = 240;
    public static final double RANGE = 14, PULL_SPEED = 0.07, TOUCH = 1.4;
    public BVoidEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Void"; }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase % 20 == 1) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 0.5f);
        if (phase == 1) for (Player p : playersNear(48)) RoomsDebug.log(p, "B-Void がうなっているわ（予告 3秒）", "B-Void telegraph room={}", startRoom);
        if (phase <= TELEGRAPH) return;
        if (phase > TELEGRAPH + PULL) { leave("pull over"); return; }
        for (Player p : roomPlayers(level, RANGE, false)) {
            if (p.isCreative()) continue;
            Vec3 to = position().add(0, 1, 0).subtract(p.position());
            double d = to.length();
            if (d < TOUCH) {
                RoomsDebug.log(p, "B-Void に呑まれたわ", "B-Void touched player={}", p.getGameProfile().getName());
                hit(p, 40f);
                leave("touched " + p.getGameProfile().getName());
                return;
            }
            if (d <= RANGE) {
                Vec3 v = to.normalize().scale(PULL_SPEED);
                p.push(v.x, 0, v.z);
                p.hurtMarked = true;
                if (phase % 40 == 0) RoomsDebug.log("B-Void pulling player={} dist={}", p.getGameProfile().getName(), String.format("%.1f", d));
            }
        }
    }

    @Override protected void clientTick() {
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 4;
            level().addParticle(ParticleTypes.WHITE_ASH, getX() + Math.cos(a) * r, getY() + 1 + random.nextDouble(), getZ() + Math.sin(a) * r, -Math.cos(a) * 0.3, 0, -Math.sin(a) * 0.3);
        }
    }
}
