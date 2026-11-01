package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * Every player's chips through the casino games: the turnover (all chips ever staked, which earns the VIP card) and
 * the current visit's result (won minus staked since the player last walked out, which draws the muggers), and the
 * day of their last duel with the boss.
 */
public final class PlayerLedger extends SavedData {
	public static final long VIP_TURNOVER = GambleConfig.get().vipFreeTurnover;

	private static final class Entry {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("turnover").forGetter(e -> e.turnover),
				Codec.LONG.fieldOf("visit").forGetter(e -> e.visit),
				Codec.LONG.optionalFieldOf("duel_day", -1L).forGetter(e -> e.duelDay)
		).apply(i, Entry::new));

		long turnover;
		long visit;
		long duelDay;

		Entry(long turnover, long visit, long duelDay) {
			this.turnover = turnover;
			this.visit = visit;
			this.duelDay = duelDay;
		}
	}

	private static final Codec<PlayerLedger> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC)
			.xmap(map -> new PlayerLedger(new HashMap<>(map)), ledger -> ledger.entries);
	public static final SavedDataType<PlayerLedger> TYPE = new SavedDataType<>(GambleCraft.id("player_ledger"), PlayerLedger::new, CODEC, null);

	private final Map<UUID, Entry> entries;

	private PlayerLedger() {
		this(new HashMap<>());
	}

	private PlayerLedger(Map<UUID, Entry> entries) {
		this.entries = entries;
	}

	public static PlayerLedger get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private Entry entry(UUID player) {
		return entries.computeIfAbsent(player, id -> new Entry(0, 0, -1));
	}

	/** Chips put on a game (negative: taken back before it was played). */
	public void staked(UUID player, long amount) {
		Entry e = entry(player);
		if (amount > 0) {
			e.turnover += amount;
		}
		e.visit -= amount;
		setDirty();
	}

	/** Chips a game paid out. */
	public void won(UUID player, long amount) {
		if (amount > 0) {
			entry(player).visit += amount;
			setDirty();
		}
	}

	public long turnover(UUID player) {
		Entry e = entries.get(player);
		return e == null ? 0 : e.turnover;
	}

	/** Won minus staked since the visit began. */
	public long visit(UUID player) {
		Entry e = entries.get(player);
		return e == null ? 0 : e.visit;
	}

	/** Ends the visit and returns its result. */
	public long closeVisit(UUID player) {
		Entry e = entries.get(player);
		if (e == null) {
			return 0;
		}
		long result = e.visit;
		e.visit = 0;
		setDirty();
		return result;
	}

	public long duelDay(UUID player) {
		Entry e = entries.get(player);
		return e == null ? -1 : e.duelDay;
	}

	public void setDuelDay(UUID player, long day) {
		entry(player).duelDay = day;
		setDirty();
	}
}
