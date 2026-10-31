package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;

import java.util.List;

/** S-25：部屋にベルを出す。時間内に誰かが鳴らせば去る。鳴らさなければ部屋中に鐘の衝撃（隠れていれば無事）。 */
public class GKChimeEntity extends GKTimedEntity {
    public static final int TELEGRAPH = 40, TIME = 20 * 15;
    public static final float DAMAGE = 40f;
    private BlockPos bell = null;
    public GKChimeEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-25"; }

    @Override public boolean targetsOpenedRoom() { return true; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> s = GKUtil.freeSpots(level, rs, playerRoom, level.random, 1);
        if (s.isEmpty()) return null;
        bell = s.get(0);
        place(level, bell, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR));
        setPos(bell.getX() + 0.5, bell.getY() + 1, bell.getZ() + 0.5);
        RoomsDebug.log("S-25 bell at {}", bell);
        return bell;
    }

    public boolean owns(BlockPos p) { return bell != null && bell.equals(p); }

    public void rung(ServerLevel level, Player p) {
        RoomsDebug.log(p, "S-25: ベルを鳴らしたわ、去っていく", "S-25 rung by {} at phase={}", p.getGameProfile().getName(), phase);
        GKUtil.actionbar(GKUtil.players(level, this, startRoom, 24), Component.translatable("message.roomsmod.gk_chime_ok"));
        leave("bell rung");
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        List<Player> ps = GKUtil.players(level, this, startRoom, 24);
        if (phase % 40 == 1) level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_CHIME_APPROACH.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        if (phase == 1) for (Player p : ps) RoomsDebug.log(p, "S-25: ベルが現れたわ（" + TIME / 20 + "秒）", "S-25 telegraph room={} bell={}", startRoom, bell);
        if (phase <= TELEGRAPH) return;
        int left = TELEGRAPH + TIME - phase;
        if (left % 20 == 0) GKUtil.actionbar(ps, Component.translatable("message.roomsmod.gk_chime_ring", left / 20));
        if (left > 0) return;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 4.0f, 0.5f);
        for (Player p : ps) {
            boolean hid = HidingRules.isHiding(p);
            RoomsDebug.log(p, "S-25: 鐘の衝撃" + (hid ? "（隠れて無事）" : ""), "S-25 blast player={} hiding={} creative={}", p.getGameProfile().getName(), hid, p.isCreative());
            if (!hid) RoomsDamage.hurt(this, p, DAMAGE);
        }
        leave("blast");
    }

    @Override protected void clientTick() {
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.NOTE, getX(), getY() + 0.5, getZ(), random.nextDouble(), 0, 0);
    }
}
