package com.example.roomsmod.client;

import com.example.roomsmod.entity.A60Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Approach loop sound that follows its entity every tick. Loudness is driven entirely by
 * A60Entity.approachVolume (t^0.75 curve, 0 at >=120 blocks, full at <=4), so attenuation is NONE
 * (OpenAL rolloff off; the source position is still used for left/right panning).
 */
public class ApproachSoundInstance extends AbstractTickableSoundInstance {
    private final Entity entity;

    public ApproachSoundInstance(Entity entity, SoundEvent sound) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = false;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
        this.volume = computeVolume();
    }

    /** Engine-accepted range for NONE attenuation is 0..1, so the curve is normalized to that. */
    private float computeVolume() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return 0f;
        float v = A60Entity.approachVolume(entity.distanceTo(p)) / A60Entity.APPROACH_MAX_VOLUME;
        return Mth.clamp(v, 0f, 1f);
    }

    public float initialVolume() { return this.volume; }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        if (entity.isRemoved()) {
            this.stop();
            return;
        }
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
        this.volume = computeVolume();
    }

    /** Client entry point used via ApproachSoundHook. */
    public static void start(Entity entity, SoundEvent sound) {
        ApproachSoundInstance inst = new ApproachSoundInstance(entity, sound);
        if (inst.initialVolume() <= A60Entity.MIN_APPROACH_VOLUME / A60Entity.APPROACH_MAX_VOLUME) return;
        Minecraft.getInstance().getSoundManager().play(inst);
    }
}
