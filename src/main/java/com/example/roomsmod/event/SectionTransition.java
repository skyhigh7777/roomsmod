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
 *   <li>前のセクションの最後の部屋から次のセクションの最初の部屋へ進んだ：「V-500 CLEAR / SECTION V — 500/500」→ 少し暗くなって
 *       「SECTION B / B-001 — MAX 300」。</li>
 *   <li>ロビーから最初の部屋へ・/rooms to 等で別のセクションへ飛んだ：「SECTION B / B-150 — MAX 300」だけ。</li>
 *   <li>同じ周回（RoomsState.epoch）で同じセクションは1回だけ（戻って入り直しても出ない）。ロビーでは出ない。</li>
 * </ul>
 */
public final class SectionTransition {
    private SectionTransition() {}

    /** CLEAR を出してから次のセクション名を出すまで（tick）。 */
    public static final int INTRO_DELAY = 60;

    private static final class Rec {
        int epoch = Integer.MIN_VALUE, last = -1, pendingRoom = -1;
        long pendingAt = 0;
        final Set<Integer> shown = new HashSet<>();
    }
    private static final Map<UUID, Rec> RECS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(RoomsCommands.ROOMS_DIMENSION) || sp.isSpectator()) return;
        Rec rec = RECS.computeIfAbsent(sp.getUUID(), k -> new Rec());
        RoomsState rs = RoomsState.get(sl);
        long now = sl.getGameTime();
        if (rec.pendingRoom >= 0 && now >= rec.pendingAt) {
            int r = rec.pendingRoom; rec.pendingRoom = -1;
            if (rs.epoch == rec.epoch && rs.hasRoom(r)) intro(sp, rs, r);
        }
        if (sp.tickCount % 5 != 0) return;
        if (rec.epoch != rs.epoch) { rec.epoch = rs.epoch; rec.last = -1; rec.shown.clear(); rec.pendingRoom = -1; }
        int idx = RoomTracker.roomIndexAt(rs, sp.blockPosition());
        if (idx < 0 || idx == rec.last) return;
        int prev = rec.last;
        rec.last = idx;
        if (idx == 0) return; // ロビー
        SectionTheme cur = Sections.forRoom(rs, idx);
        int start = Sections.sectionStart(rs, idx);
        if (!rec.shown.add(start)) return;
        SectionTheme before = prev >= 1 ? Sections.forRoom(rs, prev) : null;
        int firstNumbered = Math.max(start, 1);
        // 前のセクションの最後の部屋からそのまま進んだ（A は縦穴の範囲が全部屋共通なので idx が先の部屋になることがある）
        boolean natural = before != null && before != cur && prev + 1 == firstNumbered && idx >= firstNumbered;
        if (!natural && before == cur) return; // 同じセクションの中で番号が飛んだだけ（ここには来ないはず）
        if (natural) {
            int max = Sections.maxLocalRoom(rs, prev);
            clear(sp, before, max);
            rec.pendingRoom = idx;
            rec.pendingAt = now + INTRO_DELAY;
        } else intro(sp, rs, idx);
        RoomsDebug.log(sp, "セクション演出: " + (natural ? before.prefix() + " CLEAR → " : "") + "SECTION " + cur.prefix(),
                "section transition player={} prev={} now={} natural={}", sp.getGameProfile().getName(), prev, idx, natural);
    }

    private static void clear(ServerPlayer sp, SectionTheme t, int max) {
        String last = max > 0 ? t.formatRoom(max) : t.prefix();
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 15));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("roomsmod.section.clear.subtitle", t.prefix(), max, max).withStyle(ChatFormatting.GRAY)));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("roomsmod.section.clear.title", last).withStyle(ChatFormatting.WHITE)));
        sp.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 0.8f, 0.6f);
        // 一瞬暗くなって次のセクションへ（脈打つ暗さ、短め）
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, true, false, false));
    }

    private static void intro(ServerPlayer sp, RoomsState rs, int room) {
        SectionTheme t = Sections.forRoom(rs, room);
        int max = Sections.maxLocalRoom(rs, room);
        Component sub = max > 0
                ? Component.translatable("roomsmod.section.enter.subtitle", Sections.formatRoom(rs, room), max)
                : Component.translatable("roomsmod.section.enter.subtitle_endless", Sections.formatRoom(rs, room));
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(15, 50, 25));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub.copy().withStyle(ChatFormatting.GRAY)));
        sp.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("roomsmod.section.enter.title", t.prefix()).withStyle(ChatFormatting.DARK_RED)));
        sp.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 0.7f, 0.5f);
        sp.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.MASTER, 0.6f, 0.8f);
    }
}
