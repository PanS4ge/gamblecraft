package pl.pansage.gamblecraft.sportsbook.bet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import pl.pansage.gamblecraft.sportsbook.Sportsbook;
import pl.pansage.gamblecraft.sportsbook.feed.OddsModel;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;

/** The world's sportsbook: every coupon sold, and the team ratings behind the house's own prices. */
public final class BetBook extends SavedData {
	private static final Codec<OddsModel> MODEL_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("ratings").forGetter(OddsModel::ratings),
			Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("games").forGetter(OddsModel::games),
			Codec.STRING.listOf().fieldOf("counted").forGetter(m -> new ArrayList<>(m.counted()))
	).apply(i, (ratings, games, counted) -> new OddsModel(ratings, games, new LinkedHashSet<>(counted))));

	private static final Codec<BetBook> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Coupon.CODEC).fieldOf("coupons").forGetter(b -> b.coupons),
			MODEL_CODEC.optionalFieldOf("model", new OddsModel()).forGetter(b -> b.model)
	).apply(i, (coupons, model) -> new BetBook(new HashMap<>(coupons), model)));

	public static final SavedDataType<BetBook> TYPE = new SavedDataType<>(Sportsbook.id("bet_book"), BetBook::new, CODEC, null);
	/** Settled coupons are forgotten this long after they were bought, paid or not. */
	private static final long KEEP = 60L * 24 * 3600 * 1000;

	private final Map<UUID, Coupon> coupons;
	private final OddsModel model;

	private BetBook() {
		this(new HashMap<>(), new OddsModel());
	}

	private BetBook(Map<UUID, Coupon> coupons, OddsModel model) {
		this.coupons = coupons;
		this.model = model;
	}

	public static BetBook get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public OddsModel model() {
		return model;
	}

	public void add(Coupon coupon) {
		coupons.put(coupon.id, coupon);
		setDirty();
	}

	public Coupon get(UUID id) {
		return coupons.get(id);
	}

	public List<Coupon> owned(UUID player) {
		List<Coupon> out = new ArrayList<>();
		for (Coupon coupon : coupons.values()) {
			if (coupon.owner.equals(player)) {
				out.add(coupon);
			}
		}
		return out;
	}

	/** Games that open coupons still wait on, with the pick that refers to them. */
	public Map<String, Pick> waiting() {
		Map<String, Pick> out = new HashMap<>();
		for (Coupon coupon : coupons.values()) {
			if (coupon.state == Coupon.State.OPEN) {
				for (Pick pick : coupon.picks) {
					if (!pick.status.decided()) {
						out.putIfAbsent(pick.key, pick);
					}
				}
			}
		}
		return out;
	}

	public Set<String> referenced() {
		return new HashSet<>(waiting().keySet());
	}

	/** Settles what the feed now knows; returns the coupons that were decided just now. */
	public List<Coupon> settle(Function<String, SportEvent> events, long now, long maxPayout) {
		List<Coupon> decided = new ArrayList<>();
		boolean changed = coupons.values().removeIf(c -> c.state != Coupon.State.OPEN && now - c.placedAt > KEEP);
		for (Coupon coupon : coupons.values()) {
			Coupon.State before = coupon.state;
			if (coupon.update(events, now, maxPayout)) {
				changed = true;
				if (before == Coupon.State.OPEN && coupon.state != Coupon.State.OPEN) {
					decided.add(coupon);
				}
			}
		}
		if (changed) {
			setDirty();
		}
		return decided;
	}

	/** Marks a won or void coupon paid; false if it was paid already (a copied item) or is not payable. */
	public boolean claim(Coupon coupon) {
		if (coupon.claimed || (coupon.state != Coupon.State.WON && coupon.state != Coupon.State.VOID)) {
			return false;
		}
		coupon.claimed = true;
		coupon.version++;
		setDirty();
		return true;
	}

	public void modelChanged() {
		setDirty();
	}
}
