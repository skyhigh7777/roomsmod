package com.example.roomsmod.entity.v;

import com.example.roomsmod.block.HidingTableBlock;
import com.example.roomsmod.block.LockerBlock;
import com.example.roomsmod.hiding.LockerManager;
import com.example.roomsmod.hiding.RusherHits;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 捜索者（V-25-T / V-25-L 共通）。プレイヤーの部屋の入口から入り、その種類の隠れ場所を近い順に1つずつ調べ、
 * 次の部屋も同様に調べてから出口で消える。調べた場所にプレイヤーが隠れていれば捕まる（隠れていても守られない）。
 * 隠れていない・見えているプレイヤーは RusherHits の通常ルールで被弾（遮蔽物の陰なら spared by cover）。
 * 足音を鳴らしながら歩くので、聞いて「別の種類の隠れ場所へ移る／逃げる」を選べる。
 */
public abstract class VSearcherEntity extends VEntity {
    public static final double WALK_SPEED = 0.14;   // ブロック/tick
    public static final int ENTER_GRACE_TICKS = 60; // 出現（扉の軋み）〜歩き出し
    public static final int CHECK_TICKS = 40;       // 1か所を調べる時間（開始時に軋み音、終了時に判定）
    public static final int STEP_INTERVAL = 9;      // 足音の間隔
    public static final int ROOMS_TO_SEARCH = 2;    // プレイヤーの部屋＋次の部屋
    public static final double OPEN_RANGE = 10.0;   // 見えている（隠れていない）プレイヤーへの攻撃距離
    public static final float OPEN_DAMAGE = 8.0f;
    public static final float CATCH_DAMAGE = 40.0f; // 隠れ場所で見つかった：ほぼ即死

    /** 歩く目標列：{x,y,z,isSpot(0/1)}。部屋ごとに作り直す。 */
    private final List<int[]> targets = new ArrayList<>();
    private int targetIdx = 0, roomOffset = 0, checking = -1, grace = 0, lastStep = 0;

    protected VSearcherEntity(EntityType<? extends Mob> type, Level level) { super(type, level); }

    protected abstract LockerManager.Spot spotType();
    // ---- 強化版（V25-BUFFED）が上書きする調整値・フック ----
    protected double walkSpeed() { return WALK_SPEED; }
    protected int checkTicks() { return CHECK_TICKS; }
    protected int enterGraceTicks() { return ENTER_GRACE_TICKS; }
    protected int stepInterval() { return STEP_INTERVAL; }
    /** 出現時に入口で軋み音を鳴らすか。 */
    protected boolean entrySound() { return true; }
    /** 今調べている部屋 index。 */
    protected int currentRoom() { return startRoom + roomOffset; }
    /** 部屋を調べ終えた後、次の部屋 nextRoom も調べるか。既定は ROOMS_TO_SEARCH 部屋まで。 */
    protected boolean continueSearch(ServerLevel level, RoomsState rs, int nextRoom) {
        return roomOffset < ROOMS_TO_SEARCH && nextRoom < rs.roomPaths.size();
    }
    /** 捜索の起点を room に移し、その部屋の入口に立つ（経路は作り直し）。 */
    protected boolean relocateTo(RoomsState rs, int room) {
        List<BlockPos> p = VSpawns.path(rs, room);
        if (p.isEmpty()) return false;
        startRoom = room; roomOffset = 0; targets.clear(); targetIdx = 0; checking = -1;
        BlockPos s = p.get(0);
        // 遠くへ移るので、先に移動先のチャンクを確保・同期読み込み（未読込セクションへ入って保存行きになるのを防ぐ）
        if (level() instanceof ServerLevel sl) com.example.roomsmod.entity.RusherChunks.prepareSpawn(sl, this, new net.minecraft.world.level.ChunkPos(s));
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        return true;
    }
    /** 隠れていないプレイヤーへの攻撃。既定は10tickごと、OPEN_RANGE 内、視線あり。 */
    protected void openHits(ServerLevel level) {
        if (life % 10 == 0)
            for (Player p : playersNear(OPEN_RANGE)) if (!LockerManager.isInLocker(p) && !LockerManager.isUnderTable(p))
                RusherHits.tryHit(this, vName(), p, canSee(p), OPEN_DAMAGE);
    }
    /** 捜索開始前の保留フェーズ（true の間は捜索も当たり判定もしない）。 */
    protected boolean holdTick(ServerLevel level) { return false; }
    protected abstract SoundEvent stepSound();
    protected abstract SoundEvent checkSound();

    /** この位置のブロックが調べる対象の隠れ場所（ロッカーは下半分）か。 */
    private boolean isSpot(BlockState s) {
        if (spotType() == LockerManager.Spot.TABLE) return s.getBlock() instanceof HidingTableBlock;
        return s.getBlock() instanceof LockerBlock && s.getValue(LockerBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        startRoom = playerRoom;
        List<BlockPos> p = VSpawns.path(rs, playerRoom);
        if (p.isEmpty()) return null;
        int spots = countSpots(level, rs);
        if (spots == 0 && retargetWhenNoSpots()) spots = countSpots(level, rs);
        if (spots == 0) { RoomsDebug.log("{} spawn skipped: no {} spots in rooms {}..{}", vName(), spotType(), startRoom, startRoom + ROOMS_TO_SEARCH - 1); return null; }
        BlockPos s = p.get(0);
        setPos(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        if (entrySound()) level.playSound(null, s, checkSound(), SoundSource.HOSTILE, 2.0f, 0.7f); // 入って来る合図
        return s;
    }

    /** 調べる部屋（プレイヤーの部屋＋次の部屋）にある対象の隠れ場所の数。 */
    protected int countSpots(ServerLevel level, RoomsState rs) {
        int n = 0;
        for (int i = startRoom; i < Math.min(startRoom + ROOMS_TO_SEARCH, rs.generatedRooms.size()); i++)
            for (BoundingBox b : rs.generatedRooms.get(i))
                for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
                    if (isSpot(level.getBlockState(q))) n++;
        return n;
    }

    /** 対象が0個のとき、別の種類へ切り替えられるなら切り替えて true。 */
    protected boolean retargetWhenNoSpots() { return false; }

    private void planRoom(ServerLevel level, RoomsState rs) {
        targets.clear(); targetIdx = 0;
        int room = startRoom + roomOffset;
        List<BlockPos> path = VSpawns.path(rs, room);
        if (path.isEmpty()) return;
        List<BlockPos> spots = new ArrayList<>();
        for (BoundingBox b : rs.generatedRooms.get(room))
            for (BlockPos q : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()))
                if (isSpot(level.getBlockState(q))) spots.add(q.immutable());
        BlockPos first = path.get(0);
        targets.add(new int[]{first.getX(), first.getY(), first.getZ(), 0});
        // 近い順（貪欲）に巡回
        BlockPos cur = first;
        while (!spots.isEmpty()) {
            BlockPos c0 = cur;
            BlockPos n = spots.stream().min(java.util.Comparator.comparingDouble(q -> q.distSqr(c0))).get();
            spots.remove(n);
            targets.add(new int[]{n.getX(), n.getY(), n.getZ(), 1});
            cur = n;
        }
        BlockPos exit = path.get(path.size() - 1);
        targets.add(new int[]{exit.getX(), exit.getY(), exit.getZ(), 0});
        RoomsDebug.log("{} searching room {} spots={} targets={}", vName(), room, targets.size() - 2, targets.size());
    }

    @Override
    protected void serverTick(ServerLevel level) {
        RoomsState rs = RoomsState.get(level);
        if (holdTick(level)) return;
        if (grace < enterGraceTicks()) { grace++; if (grace >= enterGraceTicks()) planRoom(level, rs); return; }
        if (targets.isEmpty()) planRoom(level, rs); // ロード直後
        openHits(level);

        if (checking >= 0) {
            if (++checking >= checkTicks()) { finishCheck(level); checking = -1; targetIdx++; }
            return;
        }
        if (targetIdx >= targets.size()) {
            roomOffset++;
            if (!continueSearch(level, rs, startRoom + roomOffset)) { leave("searched " + roomOffset + " room(s)"); return; }
            planRoom(level, rs);
            if (targets.isEmpty()) { leave("no path in next room"); return; }
        }
        int[] t = targets.get(targetIdx);
        Vec3 goal = new Vec3(t[0] + 0.5, t[1], t[2] + 0.5);
        Vec3 d = goal.subtract(position());
        faceTowards(goal);
        if (d.length() <= walkSpeed()) {
            setPos(goal.x, goal.y, goal.z);
            if (t[3] == 1) {
                checking = 0;
                level.playSound(null, getX(), getY(), getZ(), checkSound(), SoundSource.HOSTILE, 1.5f, 1.0f);
                RoomsDebug.log("{} checking {} at {}", vName(), spotType(), new BlockPos(t[0], t[1], t[2]));
            } else targetIdx++;
        } else {
            Vec3 n = position().add(d.normalize().scale(walkSpeed()));
            setPos(n.x, n.y, n.z);
            if (life - lastStep >= stepInterval()) {
                lastStep = life;
                level.playSound(null, getX(), getY(), getZ(), stepSound(), SoundSource.HOSTILE, 1.6f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }

    private void finishCheck(ServerLevel level) {
        int[] t = targets.get(targetIdx);
        BlockPos spot = new BlockPos(t[0], t[1], t[2]);
        boolean found = false;
        for (Player p : level.players()) {
            if (!spot.equals(LockerManager.getSpotPos(p)) || LockerManager.getSpot(p) != spotType()) continue;
            found = true;
            RoomsDebug.log(p, vName() + ": 隠れ場所で見つかったわ（" + spotType() + "）",
                    "{} caught player={} spot={} at {} creative={}", vName(), p.getGameProfile().getName(), spotType(), spot, p.isCreative());
            com.example.roomsmod.util.RoomsDamage.hurt(this, p, CATCH_DAMAGE);
        }
        if (!found) RoomsDebug.log("{} checked {} at {}: empty", vName(), spotType(), spot);
    }

    @Override
    protected String debugState() {
        return "room=" + (startRoom + roomOffset) + " target=" + (targets.isEmpty() ? "(planned after grace)" : targetIdx + "/" + targets.size()) + (checking >= 0 ? " checking=" + checking : "") + " grace=" + grace;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RoomOffset", roomOffset); tag.putInt("Grace", grace);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        roomOffset = tag.getInt("RoomOffset"); grace = tag.getInt("Grace");
        targets.clear(); checking = -1; // ロード後は部屋の調べ直し
    }
}
