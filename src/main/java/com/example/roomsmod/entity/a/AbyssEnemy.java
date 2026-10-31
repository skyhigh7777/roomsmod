package com.example.roomsmod.entity.a;

import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSpace;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A（Abyss）専用の敵の共通部分。位置は縦穴の仮想座標（AbyssSpace）で持つので、プレイヤーが 1 周しても位置関係はそのまま。
 * 狙うプレイヤー（出現の基準）を 1 人覚える。縦穴がない・プレイヤーが縦穴にいないときは出現しない。
 */
public abstract class AbyssEnemy extends VEntity {
    protected UUID targetId = null;

    protected AbyssEnemy(EntityType<? extends Mob> type, Level level) { super(type, level); }

    @Override public boolean isAbyssNative() { return true; }
    @Override public String homeSection() { return "a"; }

    /** 縦穴の中心線の x, z。 */
    protected static double cx(AbyssRooms.Shaft s) { return s.ox + AbyssRooms.CX + 0.5; }
    protected static double cz(AbyssRooms.Shaft s) { return s.oz + AbyssRooms.CZ + 0.5; }

    /** 縦穴の内側（x, z）に収める。margin は壁からの余白。 */
    protected static Vec3 clampInner(AbyssRooms.Shaft s, Vec3 v, double margin) {
        double r = AbyssRooms.IN + 0.5 - margin;
        return new Vec3(Math.max(cx(s) - r, Math.min(cx(s) + r, v.x)), v.y, Math.max(cz(s) - r, Math.min(cz(s) + r, v.z)));
    }

    /** 縦穴の中にいるプレイヤー（観戦者除く）。 */
    protected static boolean inShaft(RoomsState rs, Player p) {
        return rs.abyss != null && p != null && !p.isSpectator() && p.isAlive() && rs.abyss.contains(p.position());
    }

    /** 出現の基準のプレイヤー（いなければ縦穴の中のだれか）。 */
    protected static Player pickTarget(ServerLevel level, RoomsState rs, Player player) {
        if (inShaft(rs, player)) return player;
        for (Player p : level.players()) if (inShaft(rs, p)) return p;
        return null;
    }

    /** 覚えている狙いのプレイヤー（縦穴にいなければ null）。 */
    protected Player target(ServerLevel level, RoomsState rs) {
        if (targetId == null) return null;
        Player p = level.getPlayerByUUID(targetId);
        return inShaft(rs, p) ? p : null;
    }

    protected float hardDamage(float normal, float hard) { return hardSpawn ? hard : normal; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (targetId != null) tag.putUUID("AbyssTarget", targetId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        targetId = tag.hasUUID("AbyssTarget") ? tag.getUUID("AbyssTarget") : null;
    }

    /** VSpawns.spawn から：縦穴がない・プレイヤーがいなければ出さない。 */
    protected static boolean ready(RoomsState rs) { return rs.abyss != null; }

    protected static Vec3 pv(RoomsState rs, Player p) { return AbyssSpace.playerVirtual(rs, p); }
}
