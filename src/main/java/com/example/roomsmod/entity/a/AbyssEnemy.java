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

    // ---- 横穴の取り合い ----

    /** この敵が潜んでいて危ない横穴の部屋（A-90）。なければ -1。 */
    public int dangerAlcove() { return -1; }
    /** この敵が「ここへ隠れて」と予約した横穴の部屋（A-50 / A-130）。なければ -1。ほかの敵（A-90）はここに潜まない。 */
    public int reservedAlcove() { return -1; }

    /** 生きている敵が潜んでいる（危ない）横穴の部屋。 */
    public static java.util.Set<Integer> dangerAlcoves(ServerLevel level) {
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities())
            if (e instanceof AbyssEnemy a && a.isAlive() && a.dangerAlcove() >= 0) out.add(a.dangerAlcove());
        return out;
    }

    /** 危ない横穴と予約された横穴の部屋（新しく潜む敵が避ける）。 */
    public static java.util.Set<Integer> claimedOrReserved(ServerLevel level) {
        java.util.Set<Integer> out = dangerAlcoves(level);
        for (net.minecraft.world.entity.Entity e : level.getAllEntities())
            if (e instanceof AbyssEnemy a && a.isAlive() && a.reservedAlcove() >= 0) out.add(a.reservedAlcove());
        return out;
    }

    /** 種類 cls の A の敵が生きているか。 */
    public static boolean anyAlive(ServerLevel level, Class<?>... cls) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities())
            if (e instanceof AbyssEnemy && com.example.roomsmod.entity.v.VEntity.isActive(e)) for (Class<?> c : cls) if (c.isInstance(e)) return true;
        return false;
    }

    /**
     * 一緒にいると避け方がぶつかる A の敵の種類（新しい敵だけが並べる）。判定は両向き：自分の表にある敵が生きている、
     * または生きている敵の表に自分が入っていれば出ない（古い敵の prepareFor も conflictAlive を見る）。
     */
    protected Class<?>[] incompatible() { return new Class<?>[0]; }

    /** 一緒にいてはいけない A の敵が生きているか（両向き）。 */
    protected boolean conflictAlive(ServerLevel level) {
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (!(e instanceof AbyssEnemy a) || a == this || !com.example.roomsmod.entity.v.VEntity.isActive(e)) continue;
            for (Class<?> c : incompatible()) if (c.isInstance(a)) return true;
            for (Class<?> c : a.incompatible()) if (c.isInstance(this)) return true;
        }
        return false;
    }

    /** 仮想座標 v の、プレイヤー t の近くでの実際の位置（t の実際の位置と仮想の位置の差で写す。パーティクル用）。 */
    protected static Vec3 nearPhysical(RoomsState rs, Player t, Vec3 v) { return t.position().add(v.subtract(pv(rs, t))); }

    /**
     * 部屋 from 以降 maxRooms 部屋以内で、床が仮想の y = belowY 以下にある、危なくない（本物の）横穴の部屋。なければ -1。
     * 「予告から十分に時間のある横穴」を選ぶのに使う。
     */
    protected static int freeAlcoveBelow(ServerLevel level, RoomsState rs, int from, int maxRooms, double belowY) {
        AbyssRooms.Shaft s = rs.abyss;
        if (s == null) return -1;
        java.util.Set<Integer> bad = dangerAlcoves(level);
        for (int r = Math.max(from, s.entryRoom); r <= from + maxRooms; r++) {
            if (!AbyssSpace.isAbyssRoom(rs, r) || bad.contains(r)) continue;
            AbyssRooms.AlcoveSpot a = AbyssRooms.alcoveSpot(level, s, r);
            if (a != null && a.floorY() <= belowY) return r;
        }
        return -1;
    }

    /** プレイヤーが横穴の中（体の中心）か、ロッカーに隠れているか。 */
    protected static boolean sheltered(RoomsState rs, Player p, double inflate) {
        return com.example.roomsmod.hiding.HidingRules.isHiding(p) || (rs.abyss != null && AbyssRooms.centerInAlcove(rs.abyss, p, inflate));
    }

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
