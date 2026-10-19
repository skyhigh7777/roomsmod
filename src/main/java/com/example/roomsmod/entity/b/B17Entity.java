package com.example.roomsmod.entity.b;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** B-290。 */
public class B17Entity extends BRoomEntity {
    public static final int TELEGRAPH = 40, FUSE = 240;
    private UUID target;
    public B17Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-290"; }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        Player t = target == null ? null : level.getPlayerByUUID(target);
        if (t == null) {
            Player best = null;
            for (Player p : playersNear(40)) if ((best == null || distanceTo(p) < distanceTo(best))) best = p;
            if (best == null) { if (phase > TELEGRAPH * 2) leave("no target"); return; }
            target = best.getUUID(); t = best;
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.5f, 0.5f);
            RoomsDebug.log(t, "B-290 に取り憑かれたわ（" + FUSE / 20 + "秒）", "B-290 attached player={}", t.getGameProfile().getName());
        }
        setPos(t.getX(), t.getY() + 2.3, t.getZ());
        if (phase <= TELEGRAPH) return;
        int left = TELEGRAPH + FUSE - phase;
        if (left % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.HOSTILE, 1.5f, left < 60 ? 2.0f : 1.0f);
        if (HidingRules.isHiding(t)) {
            RoomsDebug.log(t, "B-290 が離れたわ", "B-290 defused by hiding player={} left={}", t.getGameProfile().getName(), left);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.2f, 0.8f);
            leave("defused");
            return;
        }
        if (left <= 0) {
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() - 1.5, getZ(), 3, 0.5, 0.5, 0.5, 0);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
            RoomsDebug.log(t, "B-290 が弾けたわ", "B-290 burst player={}", t.getGameProfile().getName());
            hit(t, 40f);
            leave("burst");
        }
    }

    @Override protected String debugState() { return super.debugState() + " target=" + target; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); if (target != null) t.putUUID("Target", target); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); target = t.hasUUID("Target") ? t.getUUID("Target") : null; }
}
