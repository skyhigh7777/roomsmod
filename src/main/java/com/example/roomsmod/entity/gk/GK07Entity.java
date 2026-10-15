package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** GK-07：入口に立つ。 */
public class GK07Entity extends GKRoomWatcher {
    public GK07Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-07"; }
    @Override protected int telegraphTicks() { return 40; }
    @Override protected int watchTicks() { return 140; }
    @Override protected int limit() { return 50; }
    @Override protected float damage() { return 10f; }
    @Override protected SoundEvent cue() { return RoomsMod.GK_07_KNOCK.get(); }
    @Override protected int cueInterval() { return 40; }
    @Override protected String chatStart() { return "GK-07 のノック（入口、予告 2秒）"; }
    @Override protected boolean violates(Player p) { return canSee(p); }
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> path = playerRoom < rs.roomPaths.size() ? rs.roomPaths.get(playerRoom) : List.of();
        if (path.size() < 2) return null;
        BlockPos s = path.get(1); // 入口のすぐ内側
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        return s;
    }
    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 1.8, getZ(), 0, 0.01, 0);
    }
}
