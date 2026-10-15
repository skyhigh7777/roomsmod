package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** GK-Mirror：プレイヤーの影。DELAY tick 前のプレイヤーの位置をなぞって追う。触れると攻撃（隠れても止まらない）。 */
public class GKMirrorEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int TELEGRAPH = 60, CHASE = 20 * 30, DELAY = 50;
    public static final double TOUCH = 0.9;
    public static final float DAMAGE = 40f;
    private UUID target = null;
    private int phase = 0;
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    public GKMirrorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Mirror"; }
    @Override protected int maxLifeTicks() { return TELEGRAPH + CHASE + 20; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> path = playerRoom < rs.roomPaths.size() ? rs.roomPaths.get(playerRoom) : List.of();
        if (path.isEmpty()) return null;
        Player p = null; double best = 64;
        BlockPos e = path.get(0);
        for (Player q : level.players()) if (!q.isSpectator() && Math.sqrt(q.distanceToSqr(Vec3.atCenterOf(e))) < best) { best = Math.sqrt(q.distanceToSqr(Vec3.atCenterOf(e))); p = q; }
        if (p != null) { target = p.getUUID(); setPos(p.getX(), p.getY(), p.getZ()); }
        else setPos(e.getX() + 0.5, e.getY(), e.getZ() + 0.5);
        return blockPosition();
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        Player p = target == null ? null : level.getPlayerByUUID(target);
        if (phase % 20 == 1) level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_MIRROR_CHIME.get(), SoundSource.HOSTILE, 1.5f, phase <= TELEGRAPH ? 1.0f : 0.7f);
        if (p == null || p.isSpectator()) { if (phase > TELEGRAPH) leave("no target"); return; }
        if (phase == 1) RoomsDebug.log(p, "GK-Mirror: 影があなたを追うわ（止まらないで）", "GK-Mirror telegraph target={}", p.getGameProfile().getName());
        trail.addLast(p.position());
        while (trail.size() > DELAY) trail.removeFirst();
        if (phase % 20 == 0) GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.gk_mirror", Math.max(0, TELEGRAPH + CHASE - phase) / 20));
        if (phase <= TELEGRAPH) return;
        if (phase > TELEGRAPH + CHASE) { leave("chase over"); return; }
        if (trail.size() >= DELAY) { Vec3 v = trail.peekFirst(); faceTowards(v); setPos(v.x, v.y, v.z); }
        if (distanceTo(p) < TOUCH) {
            RoomsDebug.log(p, "GK-Mirror: 影に追いつかれたわ", "GK-Mirror touched player={} creative={}", p.getGameProfile().getName(), p.isCreative());
            if (!p.isCreative()) RoomsDamage.hurt(this, p, DAMAGE);
            leave("touched");
        }
    }

    @Override protected void clientTick() {
        level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
    }
    @Override protected String debugState() { return "phase=" + phase + " target=" + target + " trail=" + trail.size(); }
}
