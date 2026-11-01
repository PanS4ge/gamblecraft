package pl.pansage.gamblecraft.sportsbook.bet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.SportEvent;

/**
 * A bought coupon: a single bet or an accumulator, settled pick by pick as the results come in. Any lost pick loses
 * the coupon at once; a void pick (postponed, called off, no winner) counts at odds 1; a coupon with every pick void
 * pays the stake back. The book keeps the coupon, the item only shows it, so a coupon left in a chest still settles.
 */
public final class Coupon {
	public enum State {
		OPEN,
		WON,
		LOST,
		VOID;

		public static State of(int ordinal) {
			return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OPEN;
		}
	}

	/** A game the feeds have lost track of is void this long after its start. */
	public static final long VOID_AFTER = 3L * 24 * 3600 * 1000;

	public static final Codec<Coupon> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(c -> c.id),
			UUIDUtil.CODEC.fieldOf("owner").forGetter(c -> c.owner),
			Codec.LONG.fieldOf("placed").forGetter(c -> c.placedAt),
			Codec.LONG.fieldOf("stake").forGetter(c -> c.stake),
			Codec.BOOL.optionalFieldOf("minerals", false).forGetter(c -> c.minerals),
			Pick.CODEC.listOf().fieldOf("picks").forGetter(c -> c.picks),
			Codec.INT.fieldOf("state").forGetter(c -> c.state.ordinal()),
			Codec.LONG.optionalFieldOf("payout", 0L).forGetter(c -> c.payout),
			Codec.BOOL.optionalFieldOf("claimed", false).forGetter(c -> c.claimed),
			Codec.INT.optionalFieldOf("version", 0).forGetter(c -> c.version)
	).apply(i, (id, owner, placed, stake, minerals, picks, state, payout, claimed, version) -> new Coupon(id, owner, placed, stake, minerals,
			new ArrayList<>(picks), State.of(state), payout, claimed, version)));

	public final UUID id;
	public final UUID owner;
	public final long placedAt;
	public final long stake;
	public final boolean minerals;
	public final List<Pick> picks;
	public State state;
	public long payout;
	public boolean claimed;
	/** Bumped on every change, so items know when to redraw. */
	public int version;

	public Coupon(UUID id, UUID owner, long placedAt, long stake, boolean minerals, List<Pick> picks, State state, long payout, boolean claimed,
			int version) {
		this.id = id;
		this.owner = owner;
		this.placedAt = placedAt;
		this.stake = stake;
		this.minerals = minerals;
		this.picks = picks;
		this.state = state;
		this.payout = payout;
		this.claimed = claimed;
		this.version = version;
	}

	public double totalOdds() {
		double total = 1;
		for (Pick pick : picks) {
			total *= pick.odds;
		}
		return total;
	}

	/** What the coupon pays if every pick still standing wins. */
	public long potential(long maxPayout) {
		double total = 1;
		for (Pick pick : picks) {
			total *= pick.status == Pick.Status.VOID ? 1 : pick.odds;
		}
		return Math.min(maxPayout, (long) Math.floor(stake * total + 1e-6));
	}

	/** Brings the picks up to date from the feed; true if anything changed. */
	public boolean update(Function<String, SportEvent> events, long now, long maxPayout) {
		if (state != State.OPEN) {
			return false;
		}
		boolean changed = false;
		for (Pick pick : picks) {
			if (pick.status.decided()) {
				continue;
			}
			Pick.Status before = pick.status;
			String score = pick.score;
			String clock = pick.clock;
			SportEvent event = events.apply(pick.key);
			if (event == null) {
				if (now > pick.kickoff + VOID_AFTER) {
					pick.status = Pick.Status.VOID;
				}
			} else {
				switch (event.state()) {
					case SCHEDULED -> {
						if (now > Math.max(pick.kickoff, event.kickoff()) + VOID_AFTER) {
							pick.status = Pick.Status.VOID;
						}
					}
					case LIVE -> {
						pick.status = Pick.Status.LIVE;
						pick.score = event.score();
						pick.clock = event.clock();
					}
					case FINISHED -> {
						pick.status = event.result() == null ? Pick.Status.VOID : event.result() == pick.selection ? Pick.Status.WON : Pick.Status.LOST;
						pick.score = event.score();
						pick.clock = "";
					}
					case POSTPONED, CANCELLED -> {
						pick.status = Pick.Status.VOID;
						pick.clock = "";
					}
				}
				if (event.state() == EventState.LIVE && now > pick.kickoff + VOID_AFTER) {
					pick.status = Pick.Status.VOID;
				}
			}
			changed |= pick.status != before || !pick.score.equals(score) || !pick.clock.equals(clock);
		}
		State next = decide();
		if (next != state) {
			state = next;
			payout = switch (next) {
				case WON -> potential(maxPayout);
				case VOID -> stake;
				default -> 0;
			};
			changed = true;
		}
		if (changed) {
			version++;
		}
		return changed;
	}

	private State decide() {
		boolean allDecided = true;
		boolean allVoid = true;
		for (Pick pick : picks) {
			if (pick.status == Pick.Status.LOST) {
				return State.LOST;
			}
			allDecided &= pick.status.decided();
			allVoid &= pick.status == Pick.Status.VOID;
		}
		return !allDecided ? State.OPEN : allVoid ? State.VOID : State.WON;
	}

	public int decided() {
		int n = 0;
		for (Pick pick : picks) {
			if (pick.status.decided()) {
				n++;
			}
		}
		return n;
	}
}
