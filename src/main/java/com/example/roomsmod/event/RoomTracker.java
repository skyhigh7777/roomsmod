package com.example.roomsmod.event;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** デバッグON時、プレイヤーがいる部屋（A-000=ロビー）が変わったらログとチャットに出す。 */
public final class RoomTracker {
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private RoomTracker() {}

    /** pos を含む部屋の index（＝部屋番号）。どこにも無ければ -1。 */
    public static int roomIndexAt(RoomsState rs, net.minecraft.core.BlockPos pos) {
        for (int i = rs.generatedRooms.size() - 1; i >= 0; i--) {
            for (BoundingBox b : rs.generatedRooms.get(i)) if (b.isInside(pos)) return i;
        }
        return -1;
    }

    public static String format(int index) { return "A-" + String.format("%03d", index); }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!RoomsDebug.isEnabled() || !(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        if (!(sp.level() instanceof ServerLevel sl) || !sl.dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        RoomsState rs = RoomsState.get(sl);
        int idx = roomIndexAt(rs, sp.blockPosition());
        Integer last = LAST.put(sp.getUUID(), idx);
        if (last != null && last == idx) return;
        String now = idx < 0 ? "outside rooms" : format(idx);
        RoomsDebug.log(sp, "現在の部屋: " + now, "player {} room {} -> {} (generated up to {})",
                sp.getGameProfile().getName(), last == null || last < 0 ? "-" : format(last), now, format(rs.currentRoom));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { LAST.remove(event.getEntity().getUUID()); }
}
