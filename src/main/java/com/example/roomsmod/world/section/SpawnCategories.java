package com.example.roomsmod.world.section;

import com.example.roomsmod.entity.A20Entity;
import com.example.roomsmod.entity.A40Entity;
import com.example.roomsmod.entity.v.VEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 抽選で出るエンティティの分類（要求が矛盾する組み合わせを同時に出さないため）。
 * 通常：分類が違う同士（分類なしは除く）は同時に出さない。
 * ハード：KEEP_MOVING は他のどのエンティティとも同時に出さない（それ以外の組み合わせは可）。
 * 手動の /rooms spawn は対象外（DoorEventHandler.rollRushers だけで使う）。
 */
public final class SpawnCategories {
    private SpawnCategories() {}

    public enum Category { KEEP_MOVING, SEARCHER, HIDE_REQUIRED }

    private static final Map<String, Category> BY_NAME = new LinkedHashMap<>();
    static {
        BY_NAME.put("S-270", Category.KEEP_MOVING);
        for (String n : new String[]{"V-25-T", "V-25-L", "V25-BUFFED", "S-165"}) BY_NAME.put(n, Category.SEARCHER);
        for (String n : new String[]{"B-20", "B-40", "B-15", "B-50", "B-90", "B-130", "B-180", "B-230", "B-260", "B-290",
                "V-10", "V-50", "S-225", "S-245", "S-300",
                "U-20", "U-60", "U-120", "U-200", "U-300", "A-25"}) BY_NAME.put(n, Category.HIDE_REQUIRED);
    }

    /** 分類（なしは null）。 */
    public static Category of(String name) { return BY_NAME.get(name); }

    public static Map<String, Category> all() { return java.util.Collections.unmodifiableMap(BY_NAME); }

    /** 抽選対象のエンティティ名（B-20/B-40 と V/GK/B の VEntity）。それ以外は null。 */
    public static String nameOf(Entity e) {
        if (e instanceof A20Entity) return "B-20";
        if (e instanceof A40Entity) return "B-40";
        if (e instanceof VEntity v) return v.vName();
        return null;
    }

    /** 生存中の抽選対象エンティティの名前（重複なし、見つけた順）。 */
    public static java.util.Set<String> aliveNames(ServerLevel level) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Entity e : level.getAllEntities()) {
            if (!VEntity.isActive(e)) continue;
            String n = nameOf(e);
            if (n != null) out.add(n);
        }
        return out;
    }

    /**
     * name を出すと present（生存中＋この抽選で出す予定）と矛盾するなら、その説明（例 "KEEP_MOVING vs HIDE_REQUIRED, alive=S-270"）。矛盾しなければ null。
     * present の値は "alive" か "roll"。
     */
    public static String conflict(String name, Map<String, String> present, boolean hard) {
        Category c = of(name);
        for (var e : present.entrySet()) {
            String other = e.getKey();
            if (other.equals(name)) continue;
            Category oc = of(other);
            boolean bad;
            if (hard) bad = c == Category.KEEP_MOVING || oc == Category.KEEP_MOVING;
            else bad = c != null && oc != null && c != oc;
            if (bad) return label(c) + " vs " + label(oc) + ", " + e.getValue() + "=" + other;
        }
        return null;
    }

    private static String label(Category c) { return c == null ? "UNCATEGORIZED" : c.name(); }
}
