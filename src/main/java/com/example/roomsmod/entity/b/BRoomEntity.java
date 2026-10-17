package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 開けた部屋の安全な床に現れる B エンティティの共通部分（phase カウンタ、部屋のプレイヤー）。 */
public abstract class BRoomEntity extends VEntity {
    protected int phase = 0;
    protected BRoomEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    /** この部屋（または近く）にいるプレイヤー。hidden=false なら隠れている人を除く。 */
    protected List<Player> roomPlayers(ServerLevel level, double r, boolean includeHidden) {
        List<Player> out = new ArrayList<>();
        for (Player p : GKUtil.players(level, this, startRoom, r)) if (includeHidden || !HidingRules.isHiding(p)) out.add(p);
        return out;
    }

    protected void hit(Player p, float dmg) { if (!p.isCreative()) com.example.roomsmod.util.RoomsDamage.hurt(this, p, dmg); }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
