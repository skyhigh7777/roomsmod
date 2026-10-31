package com.example.roomsmod.client;

import com.example.roomsmod.RoomsMod;
import com.example.roomsmod.world.AbyssRooms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A（Abyss）の縦穴の中の落下速度の上限（クライアント側。プレイヤーの移動はクライアントが決めるため）。
 * 約 12 ブロック/秒、スニーク中はその半分。サーバーが AbyssPayload で縦穴の中かを知らせる。
 */
@EventBusSubscriber(modid = RoomsMod.MOD_ID, value = Dist.CLIENT)
public final class AbyssClient {
    private AbyssClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !AbyssRooms.clientActive || p.isSpectator() || p.getAbilities().flying) return;
        double cap = p.isShiftKeyDown() ? AbyssRooms.MAX_FALL_SNEAK : AbyssRooms.MAX_FALL;
        Vec3 v = p.getDeltaMovement();
        if (v.y < -cap) p.setDeltaMovement(v.x, -cap, v.z);
    }

    /**
     * サーバーが縦穴の 1 周（L ブロック上へのテレポート）に合わせて送ってくる：敵とパーティクルを補間なしで同じだけずらす
     * （プレイヤーのテレポートと同じフレームで、見た目の位置関係が変わらないように）。
     */
    public static void shift(com.example.roomsmod.world.AbyssSpace.ShiftPayload m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i = 0; i < m.ids().length; i++) {
            net.minecraft.world.entity.Entity e = mc.level.getEntity(m.ids()[i]);
            if (e == null) continue;
            double dy = m.dys()[i];
            double ty = e.lerpTargetY();
            boolean lerping = Math.abs(ty - e.getY()) > 1.0E-6 || Math.abs(e.lerpTargetX() - e.getX()) > 1.0E-6 || Math.abs(e.lerpTargetZ() - e.getZ()) > 1.0E-6;
            double tx = e.lerpTargetX(), tz = e.lerpTargetZ();
            float yr = e.lerpTargetYRot(), xr = e.lerpTargetXRot();
            e.setPos(e.getX(), e.getY() + dy, e.getZ());
            e.yo += dy; e.yOld += dy;
            if (lerping) e.lerpTo(tx, ty + dy, tz, yr, xr, 3);
        }
        if (m.particleDy() != 0) shiftParticles(mc, m);
    }

    private static java.lang.reflect.Field fParticles, fToAdd, fX, fY, fZ, fYo;
    private static boolean reflectFailed = false;

    @SuppressWarnings("unchecked")
    private static void shiftParticles(Minecraft mc, com.example.roomsmod.world.AbyssSpace.ShiftPayload m) {
        if (reflectFailed) return;
        try {
            if (fParticles == null) {
                Class<?> pe = net.minecraft.client.particle.ParticleEngine.class, pc = net.minecraft.client.particle.Particle.class;
                fParticles = pe.getDeclaredField("particles"); fParticles.setAccessible(true);
                fToAdd = pe.getDeclaredField("particlesToAdd"); fToAdd.setAccessible(true);
                fX = pc.getDeclaredField("x"); fX.setAccessible(true);
                fY = pc.getDeclaredField("y"); fY.setAccessible(true);
                fZ = pc.getDeclaredField("z"); fZ.setAccessible(true);
                fYo = pc.getDeclaredField("yo"); fYo.setAccessible(true);
            }
            java.util.List<net.minecraft.client.particle.Particle> all = new java.util.ArrayList<>();
            for (java.util.Queue<net.minecraft.client.particle.Particle> q : ((java.util.Map<?, java.util.Queue<net.minecraft.client.particle.Particle>>) fParticles.get(mc.particleEngine)).values()) all.addAll(q);
            all.addAll((java.util.Queue<net.minecraft.client.particle.Particle>) fToAdd.get(mc.particleEngine));
            double dy = m.particleDy();
            for (net.minecraft.client.particle.Particle p : all) {
                double x = fX.getDouble(p), y = fY.getDouble(p), z = fZ.getDouble(p);
                if (x < m.minX() || x >= m.maxX() || z < m.minZ() || z >= m.maxZ() || y < m.minY() || y >= m.maxY()) continue;
                p.setPos(x, y + dy, z);
                fYo.setDouble(p, fYo.getDouble(p) + dy);
            }
        } catch (ReflectiveOperationException | RuntimeException ex) {
            reflectFailed = true; // パーティクルは短命なので、ずらせなくても続ける
        }
    }

    @SubscribeEvent
    public static void onLogout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { AbyssRooms.clientActive = false; }
}
