package com.example.roomsmod.entity.b;

import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.SafeSpot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** B-110。 */
public class BErrorEntity extends BRoomEntity {
    public static final int WINDOWS = 3, WINDOW_TICKS = 140;
    private int closed = 0;
    public BErrorEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "B-110"; }
    @Override public boolean isPickable() { return true; }

    @Override
    protected void serverTick(ServerLevel level) {
        phase++;
        if (phase == 1) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 2f, 0.5f);
            RoomsDebug.announce(playersNear(48), "B-110 のウィンドウ（" + (closed + 1) + "/" + WINDOWS + "、" + WINDOW_TICKS / 20 + "秒）", "B-110 window {}/{} room={} at {}", closed + 1, WINDOWS, startRoom, blockPosition());
        }
        if (phase % 20 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.5f, 1.8f);
        if (phase >= WINDOW_TICKS) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 2f, 1.4f);
            for (Player p : roomPlayers(level, 40, true)) { RoomsDebug.log(p, "B-110 が叫んだわ", "B-110 timeout player={} closed={}", p.getGameProfile().getName(), closed); hit(p, 20f); }
            leave("timeout");
        }
    }

    /** 右クリックでウィンドウを閉じる（サーバー）。テストからも呼ぶ。 */
    public void close(ServerLevel level, Player p) {
        closed++;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
        RoomsDebug.log(p, "B-110 を閉じたわ（" + closed + "/" + WINDOWS + "）", "B-110 closed by {} {}/{}", p.getGameProfile().getName(), closed, WINDOWS);
        if (closed >= WINDOWS) { leave("all windows closed"); return; }
        Vec3 v = SafeSpot.findRandom(level, RoomsState.get(level), startRoom, level.random);
        if (v != null) setPos(v.x, v.y, v.z);
        phase = 0;
    }

    @Override
    protected InteractionResult mobInteract(Player p, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && level() instanceof ServerLevel sl && isAlive()) close(sl, p);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override protected String debugState() { return super.debugState() + " closed=" + closed; }
    @Override public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Closed", closed); }
    @Override public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) { super.readAdditionalSaveData(t); closed = t.getInt("Closed"); }
}
