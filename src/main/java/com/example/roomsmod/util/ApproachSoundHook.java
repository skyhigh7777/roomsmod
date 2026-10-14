package com.example.roomsmod.util;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

/**
 * Dist-safe bridge for client-only approach loop sounds.
 * Common code calls {@link #start}; the client installs the real implementation in ClientSetup.
 * On a dedicated server it stays a no-op, so no client classes are loaded.
 */
public final class ApproachSoundHook {
    @FunctionalInterface
    public interface Starter { void start(Entity entity, SoundEvent sound); }

    private static volatile Starter starter = (e, s) -> {};

    private ApproachSoundHook() {}

    public static void set(Starter s) { starter = s; }

    public static void start(Entity entity, SoundEvent sound) { starter.start(entity, sound); }
}
