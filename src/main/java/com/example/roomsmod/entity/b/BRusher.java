package com.example.roomsmod.entity.b;

import com.example.roomsmod.entity.gk.GKEntity;
import com.example.roomsmod.entity.v.VPathWalker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * B のラッシャー共通（B-20/B-40 系の「隠れてやり過ごす」ラッシャーの変種）。
 * 後ろの部屋から開けた部屋の先まで走り、設定に応じて折り返し（rebounds 回）・途中停止・2回目の通過をする。
 * 予告：出現時に折り返し回数＋1 回のチャイム、grace の間の接近音。当たりは RusherHits（隠れる・遮蔽で回避）。
 */
public abstract class BRusher extends VPathWalker {
    protected int rebounds = 0, passes = 0, pause = 0, chimes = 0;
    protected boolean stopped = false;
    protected BRusher(EntityType<? extends Mob> type, Level level) { super(type, level); }

    protected abstract int roomsBehind();
    protected int roomsAhead() { return 1; }
    /** 折り返す回数。 */
    protected int maxRebounds() { return 0; }
    /** 実際の折り返し回数（ハードで出現したら折り返す種類だけ +HardMode.EXTRA_TURNS）。 */
    protected final int rebounds() { int n = maxRebounds(); return n > 0 && hardSpawn ? n + com.example.roomsmod.world.HardMode.EXTRA_TURNS : n; }
    /** 終点で先頭に戻ってもう一度通る回数。 */
    protected int extraPasses() { return 0; }
    /** 折り返し・再通過の前の待ち。 */
    protected int turnPause() { return 40; }
    /** 経路のこの割合で一度止まる（負なら止まらない）。 */
    protected double midStopAt() { return -1; }
    protected int midStopTicks() { return 60; }
    protected abstract Vector3f color();
    protected SoundEvent turnSound() { return SoundEvents.BELL_BLOCK; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }
    @Override protected int graceTicks() { return 70; }
    @Override protected float damage() { return 40f; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        return placeAtStart(rs, Math.max(0, playerRoom - roomsBehind()), Math.min(rs.roomPaths.size() - 1, playerRoom + roomsAhead()));
    }

    @Override
    protected boolean holdTick(ServerLevel level) {
        // 予告：折り返し回数＋1 回のチャイム（10 tick 間隔）
        if (chimes <= rebounds() && tickCount % 10 == 1) {
            level.playSound(null, getX(), getY(), getZ(), turnSound(), SoundSource.HOSTILE, 3.0f, 0.6f);
            if (chimes == 0) RoomsDebug.announce(playersNear(160), vName() + " が来るわ（チャイム " + (rebounds() + 1) + " 回）", "{} telegraph rebounds={} passes={} rooms {}->{}", vName(), rebounds(), extraPasses() + 1, startRoom, endRoom);
            chimes++;
        }
        if (super.holdTick(level)) return true;
        if (pause > 0) { pause--; return true; }
        double f = midStopAt();
        if (f >= 0 && !stopped && pathSize() > 0 && waypointIndex >= (int) (pathSize() * f)) {
            stopped = true; pause = midStopTicks();
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 3.0f, 0.8f);
            RoomsDebug.log("{} stops mid-path at waypoint {}/{} for {} ticks", vName(), waypointIndex, pathSize(), pause);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onPathEnd(ServerLevel level) {
        if (rebounds < rebounds()) {
            rebounds++; pause = turnPause(); stopped = false;
            reversePath();
            level.playSound(null, getX(), getY(), getZ(), turnSound(), SoundSource.HOSTILE, 3.0f, 0.7f);
            RoomsDebug.log("{} rebound {}/{} pause={}", vName(), rebounds, rebounds(), pause);
            return true;
        }
        if (passes < extraPasses()) {
            passes++; pause = turnPause(); stopped = false;
            restartPath();
            level.playSound(null, getX(), getY(), getZ(), turnSound(), SoundSource.HOSTILE, 3.0f, 0.5f);
            RoomsDebug.log("{} second pass {}/{} pause={}", vName(), passes, extraPasses(), pause);
            return true;
        }
        return false;
    }

    @Override protected void clientTick() {
        super.clientTick();
        for (int i = 0; i < 3; i++) {
            DustParticleOptions d = new DustParticleOptions(i == 0 ? new Vector3f(0.1f, 0.9f, 1f) : color(), 1.6f);
            level().addParticle(d, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + random.nextDouble() * 2.2, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, -0.03, 0);
        }
    }

    @Override protected String debugState() { return super.debugState() + " rebounds=" + rebounds + "/" + rebounds() + " passes=" + passes + " pause=" + pause + " stopped=" + stopped; }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Rebounds", rebounds); t.putInt("Passes", passes); t.putInt("Pause", pause); t.putBoolean("Stopped", stopped); t.putInt("Chimes", chimes); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); rebounds = t.getInt("Rebounds"); passes = t.getInt("Passes"); pause = t.getInt("Pause"); stopped = t.getBoolean("Stopped"); chimes = t.getInt("Chimes"); }
}
