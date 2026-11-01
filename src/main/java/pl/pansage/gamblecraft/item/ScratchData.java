package pl.pansage.gamblecraft.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A scratch card's state. {@code bell} is the issuing village's bell ({@link #UNBOUND} until the card is issued);
 * {@code multiplier} is only known once the card has been scratched (-1 before).
 */
public record ScratchData(long seed, long bell, int name, boolean scratched, int multiplier, boolean claimed) {
	public static final long UNBOUND = Long.MIN_VALUE;
	/** {@code bell} of a card sold by a casino kiosk: paid in chips by the cashier, not by village clerks. */
	public static final long CASINO = Long.MIN_VALUE + 1;
	public static final int CASINO_NAME = -2;

	public static final Codec<ScratchData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("seed").forGetter(ScratchData::seed),
			Codec.LONG.optionalFieldOf("bell", UNBOUND).forGetter(ScratchData::bell),
			Codec.INT.optionalFieldOf("name", -1).forGetter(ScratchData::name),
			Codec.BOOL.optionalFieldOf("scratched", false).forGetter(ScratchData::scratched),
			Codec.INT.optionalFieldOf("multiplier", -1).forGetter(ScratchData::multiplier),
			Codec.BOOL.optionalFieldOf("claimed", false).forGetter(ScratchData::claimed)
	).apply(i, ScratchData::new));

	public static final StreamCodec<ByteBuf, ScratchData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, ScratchData::seed,
			ByteBufCodecs.VAR_LONG, ScratchData::bell,
			ByteBufCodecs.VAR_INT, ScratchData::name,
			ByteBufCodecs.BOOL, ScratchData::scratched,
			ByteBufCodecs.VAR_INT, ScratchData::multiplier,
			ByteBufCodecs.BOOL, ScratchData::claimed,
			ScratchData::new);

	public boolean bound() {
		return bell != UNBOUND;
	}

	public boolean casino() {
		return bell == CASINO;
	}

	public BlockPos bellPos() {
		return BlockPos.of(bell);
	}

	public ScratchData bind(BlockPos bellPos, int nameIndex) {
		return new ScratchData(seed, bellPos.asLong(), nameIndex, scratched, multiplier, claimed);
	}

	public ScratchData scratch(int result) {
		return new ScratchData(seed, bell, name, true, result, claimed);
	}

	public ScratchData claim() {
		return new ScratchData(seed, bell, name, scratched, multiplier, true);
	}
}
