package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.item.FlashlightHandler;
import com.example.roomsmod.item.FlashlightItem;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** GK-010：点いている懐中電灯に飛んで来て、触れると電池を吸い尽くす（装填中＋予備1個）。光を消すと見失う。 */
public class GKMothEntity extends com.example.roomsmod.entity.v.VEntity {
    public static final int TELEGRAPH = 60, LOST_TICKS = 100;
    public static final double SPEED = 0.16, RANGE = 24, TOUCH = 1.3;
    public static final float DAMAGE = 4f;
    private int phase = 0, lost = 0;
    public GKMothEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "GK-010"; }
    @Override protected int maxLifeTicks() { return 20 * 60; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y + 1.5, at.z);
        return BlockPos.containing(at);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase % 20 == 1) level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_MOTH_FLUTTER.get(), SoundSource.HOSTILE, 1.6f, 1.0f);
        if (phase == 1) for (Player p : playersNear(48)) RoomsDebug.log(p, "GK-010 の羽音", "GK-010 telegraph room={}", startRoom);
        if (phase <= TELEGRAPH) return;
        Player target = null; double best = RANGE;
        for (Player p : playersNear(RANGE))
            if (!FlashlightHandler.heldOn(p).isEmpty() && distanceTo(p) < best) { best = distanceTo(p); target = p; }
        if (target == null) {
            if (++lost >= LOST_TICKS) leave("lost the light");
            return;
        }
        lost = 0;
        Vec3 goal = target.getEyePosition().subtract(0, 0.4, 0), d = goal.subtract(position());
        faceTowards(goal);
        if (d.length() <= TOUCH) { steal(level, target); return; }
        Vec3 n = position().add(d.normalize().scale(SPEED));
        setPos(n.x, n.y, n.z);
    }

    private void steal(ServerLevel level, Player p) {
        ItemStack fl = FlashlightHandler.heldOn(p);
        int before = FlashlightItem.data(fl).charge();
        FlashlightItem.setData(fl, FlashlightItem.data(fl).withCharge(0).withOn(false));
        int spare = p.getInventory().clearOrCountMatchingItems(s -> s.is(RoomsMod.BATTERY.get()), 1, p.inventoryMenu.getCraftSlots());
        level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_MOTH_FLUTTER.get(), SoundSource.HOSTILE, 2.5f, 0.5f);
        RoomsDebug.log(p, "GK-010: 電池を吸われたわ（予備 -" + spare + "）", "GK-010 stole charge={} spareBatteries={} player={}", before, spare, p.getGameProfile().getName());
        if (!p.isCreative()) RoomsDamage.hurt(this, p, DAMAGE);
        leave("stole battery");
    }

    @Override protected void clientTick() {
        if (tickCount % 2 == 0) level().addParticle(ParticleTypes.WHITE_ASH, getX() + random.nextDouble() - 0.5, getY() + random.nextDouble(), getZ() + random.nextDouble() - 0.5, 0, 0, 0);
    }
    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " lost=" + lost; }
}
