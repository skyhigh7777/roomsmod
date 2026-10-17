package com.example.roomsmod.client;

import com.example.roomsmod.entity.v.V100Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** V-100 の見張り中ループ音。エンティティに追従し、消える・覗き終わる（窓の選び直し含む）と止まる。 */
public class WatchSoundInstance extends AbstractTickableSoundInstance {
    /** 線形減衰：volume×16 ブロックまで聞こえる（2.0 → 32 ブロック）。 */
    public static final float VOLUME = 2.0f;
    private final V100Entity entity;

    public WatchSoundInstance(V100Entity entity, SoundEvent sound) {
        super(sound, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.relative = false;
        this.x = entity.getX(); this.y = entity.getY() + 0.5; this.z = entity.getZ();
    }

    @Override
    public void tick() {
        if (entity.isRemoved() || !entity.isPeeking()) { entity.watchSoundActive = false; stop(); return; }
        this.x = entity.getX(); this.y = entity.getY() + 0.5; this.z = entity.getZ();
    }

    public static void start(V100Entity e, SoundEvent s) {
        e.watchSoundActive = true;
        Minecraft.getInstance().getSoundManager().play(new WatchSoundInstance(e, s));
    }
}
