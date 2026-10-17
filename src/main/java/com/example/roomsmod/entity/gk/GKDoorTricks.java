package com.example.roomsmod.entity.gk;

import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 扉を右クリックしたときの GK の扉フック（DoorEventHandler から、まだ閉じている扉で呼ぶ）。true なら扉を開けさせない。 */
public final class GKDoorTricks {
    private GKDoorTricks() {}
    /** クライアントとサーバーの両方：この開いた扉を GK-125 が見張っているか（閉められる例外）。 */
    public static boolean closableOpenDoor(net.minecraft.world.level.Level level, BlockPos door) {
        return !level.getEntitiesOfClass(GK07Entity.class, new net.minecraft.world.phys.AABB(door).inflate(3), g -> g.isAlive() && g.guards(door)).isEmpty();
    }

    /** ベルを鳴らした。 */
    public static void onBell(ServerLevel level, Player player, BlockPos pos) {
        for (Entity e : level.getAllEntities()) if (e instanceof GKChimeEntity c && c.isAlive() && c.owns(pos)) { c.rung(level, player); return; }
    }

    /** ロッカーに入ろうとした。true なら入れない（GK-165 に捕まった）。 */
    public static boolean onLockerEnter(ServerLevel level, Player player, BlockPos base) {
        for (Entity e : level.getAllEntities()) if (e instanceof GKMimicEntity m && m.isAlive() && m.owns(base)) { m.caught(level, player); return true; }
        return false;
    }

    public static boolean onDoorClick(ServerLevel level, RoomsState rs, Player player, BlockPos door) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof GK07Entity g && g.isAlive() && g.guards(door) && level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) { g.closed(level, player); return true; }
            if (!e.isAlive()) continue;
            if (e instanceof GKDetentionEntity d && d.guards(door)) { d.punish(level, player); return true; }
            if (e instanceof GKSubstituteEntity s && s.isFake(door)) { s.trigger(level, player); return true; }
            if (e instanceof com.example.roomsmod.entity.b.BMawEntity m && m.guards(door) && !m.mouthOpen()) { m.bite(level, player); return true; }
        }
        return false;
    }
}
