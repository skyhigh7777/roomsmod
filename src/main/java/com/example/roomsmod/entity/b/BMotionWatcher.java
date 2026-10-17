package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKRoomWatcher;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** B のウォッチャー共通：プレイヤーの前 tick からの移動量（サーバー側）と、グリッチ色のパーティクル。 */
public abstract class BMotionWatcher extends GKRoomWatcher {
    private final Map<UUID, Vec3> last = new HashMap<>();
    protected BMotionWatcher(EntityType<? extends Mob> type, Level level) { super(type, level); }

    /** 前回の呼び出しからの移動（x/z と y）。初回は 0。 */
    protected Vec3 moved(Player p) {
        Vec3 prev = last.put(p.getUUID(), p.position());
        return prev == null ? Vec3.ZERO : p.position().subtract(prev);
    }

    protected abstract Vector3f glitchColor();

    @Override protected void clientTick() {
        if (random.nextInt(3) != 0) return;
        DustParticleOptions d = new DustParticleOptions(random.nextBoolean() ? glitchColor() : new Vector3f(0.1f, 0.9f, 0.95f), 1.0f);
        level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 1.2, getY() + random.nextDouble() * 2.4, getZ() + (random.nextDouble() - 0.5) * 1.2, 0, 0, 0);
    }
}
