package com.example.roomsmod.entity.a;

import com.example.roomsmod.hiding.HidingRules;
import com.example.roomsmod.util.RoomsDamage;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.AbyssRooms;
import com.example.roomsmod.world.AbyssSqueeze;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A-1100「壁の手」：縦穴の内側の壁から手が出てくる。姿はない（手は粒）。ブロックは置かない（片付け不要）。
 * <ul>
 *   <li>くり返し {@value #ROUNDS} 回（ハード {@value #ROUNDS_HARD}）：壁 1 面（ハードは半分の確率で隣り合う 2 面）を選び、予告
 *       （その壁沿いの帯にだけ黒い粒と引っかく音。長さは帯の外へ空中スニークで動く時間の {@value AbyssSqueeze#MARGIN} 倍（ハード {@value AbyssSqueeze#MARGIN_HARD} 倍）、最低
 *       {@value #MIN_WARN}（ハード {@value #MIN_WARN_HARD}）tick）→ つかむ {@value #GRAB} tick（ハード {@value #GRAB_HARD}）。</li>
 *   <li>つかむ間、その壁から {@value #BAND} ブロックの帯に体が {@value #TOLERANCE} より深く入っていればダメージ {@value #DAMAGE}（ハード {@value #DAMAGE_HARD}）、1 回のつかみで 1 人 1 回。
 *       横穴（体の中心）・ロッカーの中は当たらない。反対側（と真ん中）は必ず空いている。</li>
 *   <li>縦穴を狭める・横へ避ける・寄る・重力・床の敵（A-50 / A-130 / A-180 / A-200 / A-250 / A-450 / A-600 / A-800）、ほかの深い A の敵（A-1050 / A-1111 / A-1180）とは同時に出ない。</li>
 * </ul>
 */
public class A1100Entity extends AbyssEnemy {
    public static final double BAND = 1.5, TOLERANCE = 0.1;
    public static final int ROUNDS = 3, ROUNDS_HARD = 4, GRAB = 30, GRAB_HARD = 36, MIN_WARN = 40, MIN_WARN_HARD = 34, REST = 10;
    public static final float DAMAGE = 4f, DAMAGE_HARD = 6f;
    /** 壁の向き（ビット）：0 = 北（-z）、1 = 東（+x）、2 = 南（+z）、3 = 西（-x）。 */
    private int sides = 0, round = 0, stateTicks = 0, warn = MIN_WARN;
    private boolean grabbing = false, resting = false;
    private final Set<UUID> hitThisGrab = new HashSet<>();

    public A1100Entity(EntityType<? extends Mob> type, Level level) { super(type, level); }
    @Override public String vName() { return "A-1100"; }
    @Override protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) { return null; }
    @Override protected Class<?>[] incompatible() {
        return new Class<?>[]{A50Entity.class, A130Entity.class, A180Entity.class, A200Entity.class, A250Entity.class, A450Entity.class, A600Entity.class, A800Entity.class,
                A1050Entity.class, A1111Entity.class, A1180Entity.class};
    }

    @Override
    public BlockPos prepareFor(ServerLevel level, RoomsState rs, Player player, int playerRoom) {
        if (!ready(rs) || AbyssRooms.squeezed(rs)) return null;
        if (conflictAlive(level) || anyAlive(level, A1100Entity.class)) { RoomsDebug.log("A-1100 prepare skipped: conflicting A enemy alive"); return null; }
        Player t = pickTarget(level, rs, player);
        if (t == null || AbyssRooms.isHeld(t)) return null;
        targetId = t.getUUID();
        setVirtualPos(pv(rs, t));
        warn = Math.max(hardSpawn ? MIN_WARN_HARD : MIN_WARN,
                (int) Math.ceil(AbyssSqueeze.sneakTicks(BAND + 0.35 - TOLERANCE) * (hardSpawn ? AbyssSqueeze.MARGIN_HARD : AbyssSqueeze.MARGIN)));
        pickSides();
        RoomsDebug.log("A-1100 prepare target={} rounds={} warn={}", t.getGameProfile().getName(), hardSpawn ? ROUNDS_HARD : ROUNDS, warn);
        return blockPosition();
    }

    private void pickSides() {
        int a = random.nextInt(4);
        sides = 1 << a;
        if (hardSpawn && random.nextBoolean()) sides |= 1 << ((a + (random.nextBoolean() ? 1 : 3)) % 4); // 隣り合う 2 面（向かい合う 2 面は選ばない）
    }

    private static String sideName(int bits) {
        StringBuilder b = new StringBuilder();
        String[] n = {"北", "東", "南", "西"};
        for (int i = 0; i < 4; i++) if ((bits & (1 << i)) != 0) b.append(b.isEmpty() ? "" : "・").append(n[i]);
        return b.toString();
    }

    /** プレイヤーの体が、つかむ壁の帯に tol より深く入っているか（実際の座標、縦穴の中心から）。 */
    private boolean inBand(RoomsState rs, Player p, double tol) {
        double hw = p.getBbWidth() / 2, x = p.getX() - cx(rs.abyss), z = p.getZ() - cz(rs.abyss);
        double edge = AbyssRooms.IN + 0.5 - BAND; // 壁の内側の面から BAND の線（中心からの距離）
        if ((sides & 1) != 0 && -(z - hw) - edge > tol) return true;
        if ((sides & 2) != 0 && (x + hw) - edge > tol) return true;
        if ((sides & 4) != 0 && (z + hw) - edge > tol) return true;
        if ((sides & 8) != 0 && -(x - hw) - edge > tol) return true;
        return false;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (rs.abyss == null) { leave("no shaft"); return; }
        Player t = target(level, rs);
        if (t == null) { leave("target gone"); return; }
        setVirtualPos(pv(rs, t)); // プレイヤーと一緒（姿なし）
        stateTicks++;
        if (resting) {
            if (stateTicks >= REST) { resting = false; stateTicks = 0; pickSides(); }
            return;
        }
        if (!grabbing) {
            if (stateTicks == 1) {
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.6f, 0.5f);
                t.displayClientMessage(Component.literal(sideName(sides) + "の壁から手が…離れろ").withStyle(ChatFormatting.GOLD), true);
                if (round == 0) RoomsDebug.announce(java.util.List.of(t), "A-1100：粒の出ている壁から離れる（" + sideName(sides) + "）", "A-1100 telegraph sides={} warn={}", sideName(sides), warn);
            }
            if (stateTicks % 8 == 0) level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 1.0f, 0.4f);
            particles(level, rs, t, ParticleTypes.SQUID_INK, 2);
            if (stateTicks >= warn) {
                grabbing = true; stateTicks = 0; hitThisGrab.clear();
                level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.0f, 0.6f);
            }
            return;
        }
        particles(level, rs, t, ParticleTypes.CRIMSON_SPORE, 3);
        for (Player q : level.players()) {
            if (!inShaft(rs, q) || hitThisGrab.contains(q.getUUID()) || HidingRules.isHiding(q) || sheltered(rs, q, 0.2) || !inBand(rs, q, TOLERANCE)) continue;
            hitThisGrab.add(q.getUUID());
            boolean hit = RoomsDamage.hurt(this, q, hardDamage(DAMAGE, DAMAGE_HARD));
            level.playSound(null, q.getX(), q.getY(), q.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.5f, 0.6f);
            RoomsDebug.log("A-1100 grabbed {} sides={} applied={}", q.getGameProfile().getName(), sideName(sides), hit);
        }
        if (stateTicks >= (hardSpawn ? GRAB_HARD : GRAB)) {
            grabbing = false; stateTicks = 0;
            if (++round >= (hardSpawn ? ROUNDS_HARD : ROUNDS)) { leave("done"); return; }
            resting = true;
        }
    }

    /** 選んだ壁沿いの帯にだけ粒（プレイヤーの上下数ブロック、本人だけに送る）。 */
    private void particles(ServerLevel level, RoomsState rs, Player t, net.minecraft.core.particles.SimpleParticleType type, int perSide) {
        if (!(t instanceof ServerPlayer sp)) return;
        double cx = cx(rs.abyss), cz = cz(rs.abyss), w = AbyssRooms.IN + 0.5;
        for (int i = 0; i < 4; i++) {
            if ((sides & (1 << i)) == 0) continue;
            for (int k = 0; k < perSide; k++) {
                double along = (random.nextDouble() * 2 - 1) * w, depth = w - random.nextDouble() * BAND, y = t.getY() + (random.nextDouble() - 0.35) * 6;
                double x = switch (i) { case 1 -> cx + depth; case 3 -> cx - depth; default -> cx + along; };
                double z = switch (i) { case 0 -> cz - depth; case 2 -> cz + depth; default -> cz + along; };
                level.sendParticles(sp, type, true, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    @Override protected void clientTick() {}
    @Override protected String debugState() { return "sides=" + sideName(sides) + " round=" + round + " grab=" + grabbing + " t=" + stateTicks + " warn=" + warn + (hardSpawn ? " hard" : ""); }
    @Override public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putInt("Sides", sides); t.putInt("Round", round); t.putInt("Warn", warn); }
    @Override public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        sides = t.getInt("Sides"); round = t.getInt("Round"); warn = t.contains("Warn") ? t.getInt("Warn") : MIN_WARN;
        if (sides == 0) sides = 1;
        grabbing = false; resting = false; stateTicks = 0; // 読み込み後は予告からやり直す
    }
}
