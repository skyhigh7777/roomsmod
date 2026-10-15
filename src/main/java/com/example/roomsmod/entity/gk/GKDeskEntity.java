package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VSearcherEntity;
import com.example.roomsmod.hiding.LockerManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** GK-Desk。排他グループ "searcher"（GK-Rollcall・V-25-T/L と同時に出ない）。 */
public class GKDeskEntity extends VSearcherEntity {
    public GKDeskEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Desk"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected LockerManager.Spot spotType() { return LockerManager.Spot.TABLE; }
    @Override protected int checkTicks() { return 30; }
    @Override protected double walkSpeed() { return 0.12; }
    @Override protected SoundEvent stepSound() { return RoomsMod.GK_ROLLCALL_STEP.get(); }
    @Override protected SoundEvent checkSound() { return RoomsMod.GK_DESK_CHECK.get(); }
}
