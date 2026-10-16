package com.example.roomsmod.entity.gk;

import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * プレイヤーの部屋に現れ、予告（TELEGRAPH）→判定（WATCH）→退場する GK エンティティの共通部分。
 * 判定中、violates(p) が true の tick を数え、LIMIT に達したら1回攻撃して退場。隠れているプレイヤーは対象外。
 */
public abstract class GKRoomWatcher extends VEntity {
    protected int phaseTick = 0;
    private final Map<UUID, Integer> strikes = new HashMap<>();

    protected GKRoomWatcher(EntityType<? extends Mob> type, Level level) { super(type, level); }

    protected abstract int telegraphTicks();
    protected abstract int watchTicks();
    protected abstract int limit();
    protected abstract float damage();
    protected abstract SoundEvent cue();
    protected abstract int cueInterval();
    protected abstract boolean violates(Player p);
    protected abstract String chatStart();
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

    protected boolean inMyRoom(ServerLevel level, Player p) {
        return RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition()) == startRoom || distanceTo(p) < 12;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phaseTick++;
        if (phaseTick % cueInterval() == 1) level.playSound(null, getX(), getY(), getZ(), cue(), SoundSource.HOSTILE, 1.6f, 1.0f);
        if (phaseTick == 1)
            for (Player p : playersNear(48)) RoomsDebug.log(p, chatStart(), "{} telegraph start room={}", vName(), startRoom);
        if (phaseTick <= telegraphTicks()) return;
        if (phaseTick == telegraphTicks() + 1) RoomsDebug.log("{} watch start ({} ticks)", vName(), watchTicks());
        if (phaseTick > telegraphTicks() + watchTicks()) { leave("watch over"); return; }
        for (Player p : playersNear(40)) {
            if (p.isSpectator() || !inMyRoom(level, p) || HidingRules.isHiding(p) || !violates(p)) continue;
            int n = strikes.merge(p.getUUID(), 1, Integer::sum);
            if (n == 1 || n % 5 == 0) RoomsDebug.log(p, vName() + ": 違反 " + n + "/" + limit(), "{} violation player={} count={}/{}", vName(), p.getGameProfile().getName(), n, limit());
            if (n >= limit()) {
                RoomsDebug.log(p, vName() + ": 攻撃したわ", "{} strike player={} creative={}", vName(), p.getGameProfile().getName(), p.isCreative());
                if (!p.isCreative()) com.example.roomsmod.util.RoomsDamage.hurt(this, p, damage());
                leave("struck " + p.getGameProfile().getName());
                return;
            }
        }
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phaseTick + "/" + (telegraphTicks() + watchTicks()) + " strikes=" + strikes.values(); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phaseTick); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phaseTick = t.getInt("Phase"); }
}
