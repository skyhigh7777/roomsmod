package com.example.roomsmod.world;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 部屋・ロビーの生成（作り直し）中に、上書きで支えを失った感圧板・ランタン等がアイテムとして落ちるのを防ぐ。
 * 生成処理は1 tick 内で同期的に終わるので、その間に部屋ディメンションへ出現しようとした ItemEntity だけを取り消す
 * （プレイヤーが捨てたアイテムはこの窓の外なので消えない）。
 */
@EventBusSubscriber(modid = "roomsmod")
public final class DropGuard {
    private DropGuard() {}
    private static int depth = 0, suppressed = 0;

    public static void begin() { if (depth++ == 0) suppressed = 0; }

    public static void end(String what) {
        if (--depth == 0 && suppressed > 0) RoomsDebug.log("drop guard: suppressed {} item drops during {}", suppressed, what);
        if (depth < 0) depth = 0;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (depth <= 0 || e.loadedFromDisk() || !(e.getEntity() instanceof ItemEntity)) return;
        if (!e.getLevel().dimension().equals(RoomsCommands.ROOMS_DIMENSION)) return;
        e.setCanceled(true);
        suppressed++;
    }
}
