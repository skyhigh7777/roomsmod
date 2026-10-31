package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** S-125：通って来た扉（開いたまま）の向こうでノックする。時間内に扉を閉めれば去る。閉めないと入って来る（隠れていれば無事）。 */
public class GK07Entity extends com.example.roomsmod.entity.v.VEntity {
    public static final int TIME = 20 * 10;
    public static final float DAMAGE = 40f;
    private BlockPos door = null;
    public GK07Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-125"; }
    @Override protected int maxLifeTicks() { return TIME + 20; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        if (playerRoom < 1) return null;
        List<BlockPos> prev = rs.path(playerRoom - 1);
        if (prev.isEmpty()) return null;
        BlockPos d = prev.get(prev.size() - 1);
        BlockState s = level.getBlockState(d);
        if (!(s.getBlock() instanceof DoorBlock) || !s.getValue(DoorBlock.OPEN)) { RoomsDebug.log("S-125 spawn skipped: door {} not open", d); return null; }
        startRoom = playerRoom; door = d;
        BlockPos stand = d.north();
        setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return stand;
    }

    /** この扉を見張っているか（クライアントでも同期済みの位置で判定する）。 */
    public boolean guards(BlockPos p) { return door != null ? door.equals(p) : blockPosition().south().equals(p); }

    public void closed(ServerLevel level, Player p) {
        BlockState s = level.getBlockState(door);
        if (s.getBlock() instanceof DoorBlock db) {
            level.setBlock(door, s.setValue(DoorBlock.OPEN, false), 10);
            level.playSound(null, door, db.type().doorClose(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        RoomsDebug.log(p, "S-125: 扉を閉めたわ", "S-125 door closed by {} at life={}", p.getGameProfile().getName(), life);
        leave("door closed");
    }

    @Override
    protected void serverTick(ServerLevel level) {
        List<Player> ps = GKUtil.players(level, this, startRoom, 4);
        if (life % 20 == 1) level.playSound(null, door, RoomsMod.GK_07_KNOCK.get(), SoundSource.HOSTILE, 2.5f, 1.0f);
        if (life == 1) for (Player p : ps) RoomsDebug.log(p, "S-125: 後ろの扉でノック（" + TIME / 20 + "秒）", "S-125 telegraph door={} room={}", door, startRoom);
        if (life % 20 == 0) GKUtil.actionbar(ps, Component.translatable("message.roomsmod.gk07_close", (TIME - life) / 20));
        if (life < TIME) return;
        level.playSound(null, door, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 2.0f, 0.8f);
        for (Player p : ps) {
            boolean hid = HidingRules.isHiding(p);
            RoomsDebug.log(p, "S-125: 入って来た" + (hid ? "（隠れて無事）" : ""), "S-125 entered player={} hiding={}", p.getGameProfile().getName(), hid);
            if (!hid) RoomsDamage.hurt(this, p, DAMAGE);
        }
        leave("entered");
    }

    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 1.8, getZ(), 0, 0.01, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); }
}
