package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * Progressive jackpots: one pot per casino (every slot machine inside it feeds the same pot), one for machines
 * standing anywhere else. Each spin adds {@link #CUT} of the stake; the pot restarts at {@link #SEED} once won.
 * Kept in hundredths of a chip so small stakes still count.
 */
public final class JackpotData extends SavedData {
	public static final long SEED = 1000;
	public static final double CUT = 0.01;
	private static final Codec<JackpotData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
			.xmap(map -> new JackpotData(new HashMap<>(map)), data -> data.pots);
	public static final SavedDataType<JackpotData> TYPE = new SavedDataType<>(GambleCraft.id("jackpots"), JackpotData::new, CODEC, null);

	private final Map<String, Long> pots;

	private JackpotData() {
		this(new HashMap<>());
	}

	private JackpotData(Map<String, Long> pots) {
		this.pots = pots;
	}

	public static JackpotData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** The pot a machine at {@code pos} plays for. */
	public static String keyAt(ServerLevel level, BlockPos pos) {
		StructureStart casino = CasinoRegion.at(level, pos);
		return casino == null ? "street" : "casino_" + casino.getChunkPos().pack();
	}

	public long chips(String key) {
		return pots.getOrDefault(key, SEED * 100) / 100;
	}

	public void feed(String key, long stake) {
		pots.merge(key, SEED * 100 + Math.round(stake * CUT * 100), (old, add) -> old + Math.round(stake * CUT * 100));
		setDirty();
	}

	/** Empties the pot into the winner's hands and starts it again from the seed. */
	public long win(String key) {
		long won = chips(key);
		pots.put(key, SEED * 100);
		setDirty();
		return won;
	}
}
