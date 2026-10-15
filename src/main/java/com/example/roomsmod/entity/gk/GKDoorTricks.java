package com.example.roomsmod.entity.gk;

import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 扉を右クリックしたときの GK の扉フック（DoorEventHandler から、まだ閉じている扉で呼ぶ）。true なら扉を開けさせない。 */
public final class GKDoorTricks {
    private GKDoorTricks() {}
    public static boolean onDoorClick(ServerLevel level, RoomsState rs, Player player, BlockPos door) {
        for (Entity e : level.getAllEntities()) {
            if (!e.isAlive()) continue;
            if (e instanceof GKDetentionEntity d && d.guards(door)) { d.punish(level, player); return true; }
            if (e instanceof GKSubstituteEntity s && s.isFake(door)) { s.trigger(level, player); return true; }
        }
        return false;
    }
}
