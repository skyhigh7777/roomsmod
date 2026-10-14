package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.LockerManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/** V-25-T：捜索者（机の下だけ調べる）。V-25-T と V-25-L は同時に出ない（VSpawns.Kind の排他グループ "searcher"）。 */
public class V25TEntity extends VSearcherEntity {
    public V25TEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "V-25-T"; }
    @Override protected LockerManager.Spot spotType() { return LockerManager.Spot.TABLE; }
    @Override protected SoundEvent stepSound() { return RoomsMod.V25T_STEP.get(); }
    @Override protected SoundEvent checkSound() { return RoomsMod.V25T_CHECK.get(); }
}
