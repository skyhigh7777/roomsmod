package com.example.roomsmod.entity.gk;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.event.RoomTracker;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/** S-185：校内放送。 */
public class GKPAEntity extends VEntity {
    public static final int TELEGRAPH = 40, COUNTDOWN = 20 * 25;
    public static final float DAMAGE = 20f;
    private int phase = 0;
    public GKPAEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "S-185"; }
    @Override protected int maxLifeTicks() { return GKEntity.MAX_LIFE_TICKS; }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> path = playerRoom < rs.roomPaths.size() ? rs.path(playerRoom) : List.of();
        if (path.size() < 2) return null;
        BlockPos s = path.get(1).above(2);
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        return s;
    }

    /** プレイヤーが指示された部屋（開始部屋の次）にいるか。 */
    private boolean safe(RoomsState rs, Player p) {
        int target = startRoom + 1; // 指示は「次の教室」＝開始部屋のちょうど1つ先だけ（さらに先や手前は不可）
        int i = RoomTracker.roomIndexAt(rs, p.blockPosition());
        if (i >= 0) return i == target;
        // 部屋の外（扉の敷居の上など）：次の教室の入口のすぐそばなら入ったとみなす
        if (target >= rs.roomPaths.size() || rs.path(target).isEmpty()) return false;
        return rs.path(target).get(0).distSqr(p.blockPosition()) <= 9;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        RoomsState rs = RoomsState.get(level);
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_PA_CHIME.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
            for (Player p : playersNear(64)) RoomsDebug.log(p, "S-185 の放送チャイム（" + COUNTDOWN / 20 + "秒）", "S-185 telegraph room={} countdown={}", startRoom, COUNTDOWN);
        }
        if (phase <= TELEGRAPH) return;
        int left = TELEGRAPH + COUNTDOWN - phase;
        if (left % 20 == 0)
            for (Player p : playersNear(64))
                if (p instanceof ServerPlayer sp)
                    com.example.roomsmod.util.RoomsHud.entity(sp, Component.translatable(safe(rs, p) ? "message.roomsmod.gk_pa_safe" : "message.roomsmod.gk_pa_countdown", left / 20));
        if (left > 0 && left <= 100 && left % 20 == 0)
            level.playSound(null, getX(), getY(), getZ(), RoomsMod.GK_PA_CHIME.get(), SoundSource.HOSTILE, 1.5f, 1.6f);
        if (left > 0) return;
        for (Player p : playersNear(96)) {
            if (p.isSpectator()) continue;
            boolean ok = safe(rs, p);
            RoomsDebug.log(p, "S-185: " + (ok ? "移動済み、セーフ" : "移動していない、攻撃"), "S-185 end player={} room={} safe={} creative={}", p.getGameProfile().getName(), RoomTracker.roomIndexAt(rs, p.blockPosition()), ok, p.isCreative());
            if (!ok) com.example.roomsmod.util.RoomsDamage.hurt(this, p, DAMAGE);
        }
        leave("countdown over");
    }

    @Override protected String debugState() { return "room=" + startRoom + " phase=" + phase + "/" + (TELEGRAPH + COUNTDOWN); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Phase", phase); }
    @Override public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); phase = t.getInt("Phase"); }
}
