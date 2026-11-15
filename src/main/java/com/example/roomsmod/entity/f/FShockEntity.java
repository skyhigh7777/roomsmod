package com.example.roomsmod.entity.f;

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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * F-70（漏電）。開けた部屋で予告のあと、水を伝う放電が数回走る。
 * 水に浸かったままだと感電（ダメージ＋鈍化）。ロッカー、テーブルの上・下、水のない足場なら安全。
 */
public class FShockEntity extends VEntity {
    public static final int TELEGRAPH = 40, HARD_TELEGRAPH = 28, PULSES = 4, PULSE_EVERY = 30;
    public static final float DAMAGE = 12f;
    private int phase = 0, pulses = 0;

    public FShockEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "F-70"; }
    @Override public String homeSection() { return "f"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override public boolean targetsOpenedRoom() { return true; }
    private int telegraph() { return hardSpawn ? HARD_TELEGRAPH : TELEGRAPH; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        Vec3 at = SafeSpot.findRandom(level, rs, playerRoom, level.random);
        if (at == null) return null;
        setPos(at.x, at.y, at.z);
        return BlockPos.containing(at);
    }

    private boolean inRoom(ServerLevel level, Player p) {
        int r = RoomTracker.roomIndexAt(RoomsState.get(level), p.blockPosition());
        return r == startRoom || r == startRoom - 1 || distanceTo(p) < 16;
    }

    /** 足元が水（水浸しのハーフも含む）。ロッカー・テーブルなら乾いているとみなす。 */
    static boolean inWater(Player p) {
        if (LockerManager.isInLocker(p) || LockerManager.isUnderTable(p)) return false;
        BlockPos feet = p.blockPosition(), below = feet.below();
        return wet(p.level().getBlockState(feet)) || wet(p.level().getBlockState(below));
    }

    private static boolean wet(BlockState s) {
        if (s.getFluidState().is(FluidTags.WATER)) return true;
        return s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)
                || (s.getBlock() instanceof SlabBlock && s.hasProperty(SlabBlock.TYPE) && s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED));
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.8f, 1.6f);
            GKUtil.actionbar(playersNear(40), Component.translatable("message.roomsmod.f70_warn"));
            for (Player p : playersNear(48)) RoomsDebug.log(p, "F-70: 漏電の予告", "{} telegraph room={}", vName(), startRoom);
        }
        if (phase < telegraph()) return;
        int due = telegraph() + pulses * PULSE_EVERY;
        if (pulses < PULSES && phase >= due) {
            pulses++;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.HOSTILE, 2.0f, 0.5f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.3, getZ(), 20, 1.5, 0.4, 1.5, 0.05);
            int hit = 0;
            for (Player p : playersNear(48)) {
                if (p.isSpectator() || !inRoom(level, p) || !inWater(p)) continue;
                RoomsDebug.log(p, "F-70: 感電", "{} shock player={}", vName(), p.getGameProfile().getName());
                if (RoomsDamage.hurt(this, p, DAMAGE))
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.5, p.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
                hit++;
            }
            RoomsDebug.log("{} pulse {}/{} hits={}", vName(), pulses, PULSES, hit);
        }
        if (pulses >= PULSES && phase >= telegraph() + PULSES * PULSE_EVERY) leave("pulses done");
    }

    @Override protected void clientTick() {
        if (tickCount % 4 == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5), getY() + 0.4, getZ() + (random.nextDouble() - 0.5), 0, 0.02, 0);
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + " pulses=" + pulses; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); t.putInt("Pulses", pulses); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); pulses = t.getInt("Pulses"); }
}
