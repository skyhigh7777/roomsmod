package com.example.roomsmod.event;

import com.example.roomsmod.command.RoomsCommands;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import com.example.roomsmod.world.section.SectionTheme;
import com.example.roomsmod.world.section.Sections;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * セクションの切り替わりの演出（プレイヤーごと・そのプレイヤーにだけ）。
 * <ul>
 *   <li>前のセクションの最後の部屋から次のセクションの最初の部屋へ進んだ：タイトル「V → B」、サブタイトル「Max.300」。</li>
 *   <li>ロビーから最初の部屋へ・/rooms to 等で飛んだ：タイトル「B」、サブタイトル「Max.300」。最後のセクションは Max.∞。</li>
 *   <li>音1つ（低いビーコン起動音）と短い暗い脈動（Darkness 2.5 秒）。</li>
 *   <li>同じ周回（RoomsState.epoch）で同じセクションは1回だけ（戻って入り直しても出ない）。ロビーでは出ない。</li>
 * </ul>
 */
public final class SectionTransition {
    private SectionTransition() {}

    private static final class Rec {
        int epoch = Integer.MIN_VALUE, last = -1;
        final Set<Integer> shown = new HashSet<>();
    }
    private static final Map<UUID, Rec> RECS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(RoomsCommands.ROOMS_DIMENSION) || sp.isSpectator() || sp.tickCount % 5 != 0) return;
        Rec rec = RECS.computeIfAbsent(sp.getUUID(), k -> new Rec());
        RoomsState rs = RoomsState.get(sl);
        if (rec.epoch != rs.epoch) { rec.epoch = rs.epoch; rec.last = -1; rec.shown.clear(); }
        int idx = RoomTracker.roomIndexAt(rs, sp.blockPosition());
        if (idx < 0 || idx == rec.last) return;
        int prev = rec.last;
        rec.last = idx;
        if (idx == 0) return; // ロビー
        SectionTheme cur = Sections.forRoom(rs, idx);
        int start = Sections.sectionStart(rs, idx);
        if (!rec.shown.add(start)) return;
        SectionTheme before = prev >= 1 ? Sections.forRoom(rs, prev) : null;
        // 前のセクションの最後の部屋からそのまま進んだ（A は縦穴の範囲が全部屋共通なので idx が先の部屋になることがある）
        boolean natural = before != null && before != cur && prev + 1 == Math.max(start, 1);
        if (!natural && before == cur) return;
        int max = Sections.maxLocalRoom(rs, idx);
        String maxText = max > 0 ? String.valueOf(max) : "∞";
        Component title = natural
                ? Component.translatable("roomsmod.section.title.transition", before.prefix(), cur.prefix())
                : Component.translatable("roomsmod.section.title.single", cur.prefix());
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("roomsmod.section.subtitle.max", maxText).withStyle(ChatFormatting.GRAY)));
        sp.connection.send(new ClientboundSetTitleTextPacket(title.copy().withStyle(ChatFormatting.DARK_RED)));
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 0.7f, 0.5f);
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, true, false, false)); // 一瞬の暗い脈動
        RoomsDebug.log(sp, "セクション演出: " + (natural ? before.prefix() + " → " : "") + cur.prefix() + " (Max." + maxText + ")",
                "section transition player={} prev={} now={} natural={}", sp.getGameProfile().getName(), prev, idx, natural);
    }
}
