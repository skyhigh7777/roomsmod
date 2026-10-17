package com.example.roomsmod.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 懐中電灯のスタックに保存する状態：装填中の電池の残り tick と ON/OFF。 */
public record FlashlightData(int charge, boolean on) {
    public static final FlashlightData EMPTY = new FlashlightData(0, false);
    public static final Codec<FlashlightData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("charge").forGetter(FlashlightData::charge),
            Codec.BOOL.fieldOf("on").forGetter(FlashlightData::on)).apply(i, FlashlightData::new));
    public static final StreamCodec<ByteBuf, FlashlightData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlashlightData::charge, ByteBufCodecs.BOOL, FlashlightData::on, FlashlightData::new);

    public FlashlightData withCharge(int c) { return new FlashlightData(c, on); }
    public FlashlightData withOn(boolean o) { return new FlashlightData(charge, o); }
}
