package com.example.roomsmod.entity.placeholder;

import com.example.roomsmod.entity.v.VEntity;
import com.example.roomsmod.util.RoomsDebug;
import com.example.roomsmod.world.RoomsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

/**
 * PLACEHOLDER 区画のテスト用エンティティ（PLACEHOLDER-32767 / -65535 / -2147483648）。仕様は未定。
 * 今は出現した部屋の真ん中に立っているだけ：AI なし・ダメージなし・音なし。自然出現はしない（/rooms spawn のみ）。
 * 寿命は他の V 系と同じ VEntity.MAX_LIFE_TICKS（2分）で消える。見た目は単色の正方形（ClientSetup）。
 */
public class PlaceholderEntity extends VEntity {
    private final String name;

    public PlaceholderEntity(EntityType<? extends Mob> type, Level level, String name) {
        super(type, level);
        this.name = name;
    }

    @Override public String vName() { return name; }

    /** プレイヤーのいる部屋の真ん中（床の上）。部屋の範囲がなければ経路の先頭。 */
    @Override
    protected BlockPos prepare(ServerLevel level, RoomsState rs, int playerRoom) {
        BlockPos at = null;
        if (playerRoom >= 0 && playerRoom < rs.generatedRooms.size() && !rs.generatedRooms.get(playerRoom).isEmpty()) {
            BoundingBox b = rs.generatedRooms.get(playerRoom).get(0);
            at = new BlockPos((b.minX() + b.maxX()) / 2, b.minY() + 1, (b.minZ() + b.maxZ()) / 2);
        } else {
            List<BlockPos> p = playerRoom >= 0 && playerRoom < rs.roomPaths.size() ? rs.roomPaths.get(playerRoom) : List.of();
            if (!p.isEmpty()) at = p.get(0);
        }
        if (at == null) return null;
        setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        RoomsDebug.log("{} placed at {} (room {})", name, at, playerRoom);
        return at;
    }

    /** 何もしない（立っているだけ）。 */
    @Override protected void serverTick(ServerLevel level) {}
}
