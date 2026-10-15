package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/** GK-Detention：次の出口扉を施錠し、部屋のどこかに「通行証」を落とす。拾えば解錠（時間切れでも解錠）。 */
public class GKDetentionEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int LOCK_TICKS = 20 * 80;
    public static final String TAG = "roomsmod_gk_pass";
    private BlockPos door = null;
    private UUID passItem = null;
    public GKDetentionEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-Detention"; }
    @Override protected int maxLifeTicks() { return LOCK_TICKS; }

    static boolean isPass(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.contains(TAG);
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = rs.roomPaths.size() - 1;
        door = rs.expectedExitDoor;
        if (door == null || rs.generatedDoors.contains(door)) { RoomsDebug.log("GK-Detention spawn skipped: no unopened exit door ({})", door); return null; }
        List<BlockPos> s = GKUtil.freeSpots(level, rs, startRoom, level.random, 1);
        if (s.isEmpty()) return null;
        ItemStack pass = new ItemStack(Items.PAPER);
        pass.set(DataComponents.CUSTOM_NAME, Component.translatable("item.roomsmod.gk_hall_pass"));
        CompoundTag tag = new CompoundTag(); tag.putBoolean(TAG, true);
        pass.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        BlockPos at = s.get(0);
        ItemEntity it = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, pass, 0, 0, 0);
        it.setUnlimitedLifetime(); it.setGlowingTag(true);
        level.addFreshEntity(it);
        passItem = it.getUUID();
        BlockPos stand = door.north();
        setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        RoomsDebug.log("GK-Detention locked {} pass at {}", door, at);
        return stand;
    }

    public boolean guards(BlockPos p) { return door != null && door.equals(p); }

    public void punish(ServerLevel level, Player p) {
        level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
        GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.gk_detention_locked"));
        RoomsDebug.log(p, "GK-Detention: 施錠中（通行証を探して）", "GK-Detention blocked door {} player={}", door, p.getGameProfile().getName());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (life % 40 == 1 && door != null) level.playSound(null, door, RoomsMod.GK_DETENTION_CHAIN.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
        for (Player p : level.players()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++)
                if (isPass(inv.getItem(i))) {
                    inv.setItem(i, ItemStack.EMPTY);
                    level.playSound(null, door, net.minecraft.sounds.SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.5f, 1.2f);
                    GKUtil.actionbar(List.of(p), Component.translatable("message.roomsmod.gk_detention_open"));
                    RoomsDebug.log(p, "GK-Detention: 通行証で解錠したわ", "GK-Detention unlocked by pass player={}", p.getGameProfile().getName());
                    leave("pass used");
                    return;
                }
        }
    }

    @Override protected void leave(String why) {
        if (passItem != null && level() instanceof ServerLevel l) { Entity e = l.getEntity(passItem); if (e != null) e.discard(); }
        super.leave(why);
    }
    @Override protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.CRIT, getX(), getY() + 1 + random.nextDouble(), getZ(), 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " door=" + door; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (door != null) t.put("Door", NbtUtils.writeBlockPos(door)); if (passItem != null) t.putUUID("Pass", passItem); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); door = NbtUtils.readBlockPos(t, "Door").orElse(null); if (t.hasUUID("Pass")) passItem = t.getUUID("Pass"); }
}
