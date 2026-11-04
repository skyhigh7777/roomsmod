package com.example.roomsmod.entity.r;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.gk.GKStareEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * R-35（覗く人）。S-205（見つめない）と同じ判定：部屋の隅に青白い顔が現れ、息づかい（予告）のあと、見つめ続けると（視線が合った tick が LIMIT）攻撃。
 * 住宅では短め（WATCH 8 秒）・弱め。ホームは R。
 */
public class RPeeperEntity extends GKStareEntity {
    public RPeeperEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-35"; }
    @Override public String homeSection() { return "r"; }
    @Override protected int telegraphTicks() { return hardSpawn ? 30 : 45; }
    @Override protected int watchTicks() { return 160; }
    @Override protected int limit() { return hardSpawn ? 14 : 20; }
    @Override protected float damage() { return 20f; }
    @Override protected SoundEvent cue() { return RoomsMod.R35_BREATH.get(); }
    @Override protected int cueInterval() { return 50; }
    @Override protected String chatStart() { return "R-35 の息づかい（見つめない）"; }
}
