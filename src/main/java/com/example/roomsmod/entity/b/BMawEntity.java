package com.example.roomsmod.entity.b;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** B-Maw。 */
public class BMawEntity extends BRoomEntity {
    public static final int CYCLE = 80, OPEN = 24;
    private BlockPos door;
    public BMawEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-Maw"; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        door = rs.expectedExitDoor;
        if (door == null || rs.generatedDoors.contains(door)) { RoomsDebug.log("B-Maw spawn skipped: no unopened exit door ({})", door); return null; }
        startRoom = rs.roomPaths.size() - 1;
        setPos(door.getX() + 0.5, door.getY(), door.getZ() - 0.6);
        return door;
    }

    /** 扉が今「開いている（通れる）」か。 */
    public boolean mouthOpen() { return phase % CYCLE >= CYCLE - OPEN; }
    public boolean guards(BlockPos d) { return door != null && door.equals(d); }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        RoomsState rs = RoomsState.get(level);
        if (door == null || rs.generatedDoors.contains(door)) { leave("door opened"); return; }
        if (phase == 1) for (Player p : playersNear(48)) RoomsDebug.log(p, "B-Maw が扉に噛みついたわ", "B-Maw guards door {} cycle={} open={}", door, CYCLE, OPEN);
        int c = phase % CYCLE;
        if (c == CYCLE - OPEN) level.playSound(null, door, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 2f, 0.5f);
        else if (c == 0) level.playSound(null, door, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2f, 0.7f);
        else if (c % 10 == 0 && !mouthOpen()) level.playSound(null, door, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.6f, 1.6f);
        if (phase % 4 == 0) {
            DustParticleOptions d = new DustParticleOptions(mouthOpen() ? new Vector3f(0.2f, 0.9f, 0.3f) : new Vector3f(0.9f, 0.05f, 0.1f), 1.4f);
            level.sendParticles(d, door.getX() + 0.5, door.getY() + 1.0, door.getZ() - 0.1, 4, 0.4, 0.7, 0.05, 0);
        }
    }

    /** 閉じている間に扉を開けようとした：噛む（扉は開かない）。 */
    public void bite(ServerLevel level, Player p) {
        level.playSound(null, door, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 2f, 1.0f);
        RoomsDebug.log(p, "B-Maw に噛まれたわ", "B-Maw bite player={} phase={}", p.getGameProfile().getName(), phase % CYCLE);
        hit(p, 20f);
    }

    @Override protected String debugState() { return super.debugState() + " door=" + door + " open=" + mouthOpen(); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); }
}
