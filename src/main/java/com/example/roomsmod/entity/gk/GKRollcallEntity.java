package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VSearcherEntity;
import com.example.roomsmod.hiding.LockerManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** GK-Rollcall。排他グループ "searcher"（V-25-T/L と同じ：隠れ場所を調べる者は同時に1体）。 */
public class GKRollcallEntity extends VSearcherEntity {
    public GKRollcallEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Rollcall"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected LockerManager.Spot spotType() { return LockerManager.Spot.LOCKER; }
    @Override protected double walkSpeed() { return 0.16; }
    @Override protected SoundEvent stepSound() { return RoomsMod.GK_ROLLCALL_STEP.get(); }
    @Override protected SoundEvent checkSound() { return RoomsMod.GK_ROLLCALL_CHECK.get(); }
}
