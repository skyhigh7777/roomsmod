package com.example.roomsmod.entity.v;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/** V-10：民家の経路（階段込み）を後ろから駆け抜けるラッシャー。家が小さいので A-20 より遅く、近くから来る。 */
public class V10Entity extends VPathWalker {
    /** プレイヤーの何部屋後ろから来るか。 */
    public static final int ROOMS_BEHIND = 4;
    /** 何部屋先まで走って消えるか。 */
    public static final int ROOMS_AHEAD = 2;
    public static final double SPEED = 1.1;      // ブロック/tick（A-20=1.8）
    public static final int GRACE_TICKS = 70;    // 出現〜走り出し（音で気付く時間）
    public static final double HIT_RANGE = 14.0; // 小部屋なので A-20 の 22.5 より短い
    public static final float DAMAGE = 8.0f;

    public V10Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public String vName() { return "V-10"; }
    @Override protected double speed() { return SPEED; }
    @Override protected int graceTicks() { return GRACE_TICKS; }
    @Override protected double hitRange() { return HIT_RANGE; }
    @Override protected float damage() { return DAMAGE; }
    @Override protected SoundEvent approachSound() { return RoomsMod.V10_APPROACH.get(); }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        return placeAtStart(rs, Math.max(0, playerRoom - ROOMS_BEHIND), Math.min(rs.roomPaths.size() - 1, playerRoom + ROOMS_AHEAD));
    }

    @Override
    protected void clientTick() {
        super.clientTick();
        DustParticleOptions d = new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.1f), 1.8f);
        for (int i = 0; i < 6; i++)
            level().addParticle(d, getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5), 0, 0, 0);
    }
}
