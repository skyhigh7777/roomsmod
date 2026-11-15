package com.example.roomsmod.entity.r;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.gk.GKUtil;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * R-70（来客）。R-5 の逆：玄関がノックし、クローゼットが少し開く。隠れずに通路に立つか、開いた扉を自分で閉める。
 * 隠れたままだと上がり込んでロッカーを調べ、中にいると引き出して攻撃。R-5 とは同時に出ない（r_door）。
 */
public class RVisitorEntity extends VEntity {
    public static final int KNOCK_TICKS = 50, HARD_KNOCK_TICKS = 35, SEARCH_TICKS = 80;
    public static final float DAMAGE = 30f;
    private int phase = 0;
    private BlockPos door;
    private boolean searching = false;

    public RVisitorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "R-70"; }
    @Override public String homeSection() { return "r"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int knockTicks() { return hardSpawn ? HARD_KNOCK_TICKS : KNOCK_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        door = RIntercomEntity.findDoor(level, rs, playerRoom);
        if (door != null) {
            setPos(door.getX() + 0.5, door.getY(), door.getZ() + 0.5);
            return door;
        }
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    private void setDoor(ServerLevel level, boolean open) {
        if (door == null) return;
        BlockState s = level.getBlockState(door);
        if (!(s.getBlock() instanceof DoorBlock db) || s.getValue(DoorBlock.OPEN) == open) return;
        db.setOpen(null, level, s, door, open);
    }

    private boolean doorClosed(ServerLevel level) {
        if (door == null) return false;
        BlockState s = level.getBlockState(door);
        return s.getBlock() instanceof DoorBlock && !s.getValue(DoorBlock.OPEN);
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            setDoor(level, true);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.HOSTILE, 1.4f, 0.8f);
        }
        if (phase == 1 || phase == 16 || phase == 31)
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.HOSTILE, 2.0f, 0.6f);
        if (phase == 1) {
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.r70_knock"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "R-70: 来客のノック", "{} knock room={} door={}", vName(), startRoom, door);
        }
        if (!searching && phase < knockTicks()) return;
        if (!searching && phase == knockTicks()) {
            boolean hid = false, visible = false;
            for (Player p : playersNear(48)) {
                if (p.isSpectator() || !inRoom(level, p)) continue;
                if (LockerManager.isInLocker(p) || LockerManager.isUnderTable(p)) hid = true;
                else visible = true;
            }
            boolean closed = doorClosed(level);
            if (hid) {
                searching = true;
                GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.r70_enter"));
                RoomsDebug.log("{} searching (hid={} closed={})", vName(), hid, closed);
            } else {
                RoomsDebug.log("{} left politely visible={} closed={}", vName(), visible, closed);
                setDoor(level, false);
                GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.r70_safe"));
                leave((visible || closed) ? "stayed visible" : "no one home");
            }
            return;
        }
        if (!searching) return;
        // 捜索：この部屋のロッカーに入っている人を引き出す
        if (phase % 15 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.HOSTILE, 1.0f, 0.5f);
        for (Player p : playersNear(48)) {
            if (p.isSpectator() || !inRoom(level, p) || !LockerManager.isInLocker(p)) continue;
            RoomsDebug.log(p, "R-70: ロッカーを調べて見つけた", "{} catch player={}", vName(), p.getGameProfile().getName());
            if (p instanceof ServerPlayer sp) LockerManager.exit(sp, vName());
            RoomsDamage.hurt(this, p, DAMAGE);
            setDoor(level, false);
            leave("caught " + p.getGameProfile().getName());
            return;
        }
        if (phase >= knockTicks() + SEARCH_TICKS) {
            setDoor(level, false);
            leave("search miss");
        }
    }

    @Override protected void leave(String why) {
        if (level() instanceof ServerLevel sl) setDoor(sl, false);
        super.leave(why);
    }

    @Override protected void clientTick() {
        if (tickCount % 8 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 0, 0.01, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " search=" + searching + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase); t.putBoolean("Search", searching);
        if (door != null) t.put("Door", NbtUtils.writeBlockPos(door));
    }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = t.getInt("Phase"); searching = t.getBoolean("Search");
        door = NbtUtils.readBlockPos(t, "Door").orElse(null);
    }
}
