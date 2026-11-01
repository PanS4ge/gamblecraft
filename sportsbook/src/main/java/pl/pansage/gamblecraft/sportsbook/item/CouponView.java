package pl.pansage.gamblecraft.sportsbook.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;

/**
 * What a coupon item shows: a copy of the book's coupon taken when it last changed. The book stays the authority;
 * the copy only feeds the tooltip and the model, and its version tells the item when it is out of date.
 */
public record CouponView(UUID id, int version, long stake, boolean minerals, int state, long payout, boolean claimed, List<PickView> picks) {
	public record PickView(String league, String home, String away, long kickoff, int selection, double odds, int status, String score, String clock) {
		public static final Codec<PickView> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("league").forGetter(PickView::league),
				Codec.STRING.fieldOf("home").forGetter(PickView::home),
				Codec.STRING.fieldOf("away").forGetter(PickView::away),
				Codec.LONG.fieldOf("kickoff").forGetter(PickView::kickoff),
				Codec.INT.fieldOf("selection").forGetter(PickView::selection),
				Codec.DOUBLE.fieldOf("odds").forGetter(PickView::odds),
				Codec.INT.fieldOf("status").forGetter(PickView::status),
				Codec.STRING.optionalFieldOf("score", "").forGetter(PickView::score),
				Codec.STRING.optionalFieldOf("clock", "").forGetter(PickView::clock)
		).apply(i, PickView::new));
		public static final StreamCodec<ByteBuf, PickView> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, PickView::league,
				ByteBufCodecs.STRING_UTF8, PickView::home,
				ByteBufCodecs.STRING_UTF8, PickView::away,
				ByteBufCodecs.VAR_LONG, PickView::kickoff,
				ByteBufCodecs.VAR_INT, PickView::selection,
				ByteBufCodecs.DOUBLE, PickView::odds,
				ByteBufCodecs.VAR_INT, PickView::status,
				ByteBufCodecs.STRING_UTF8, PickView::score,
				ByteBufCodecs.STRING_UTF8, PickView::clock,
				PickView::new);
	}

	public static final Codec<CouponView> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(CouponView::id),
			Codec.INT.fieldOf("version").forGetter(CouponView::version),
			Codec.LONG.fieldOf("stake").forGetter(CouponView::stake),
			Codec.BOOL.fieldOf("minerals").forGetter(CouponView::minerals),
			Codec.INT.fieldOf("state").forGetter(CouponView::state),
			Codec.LONG.fieldOf("payout").forGetter(CouponView::payout),
			Codec.BOOL.fieldOf("claimed").forGetter(CouponView::claimed),
			PickView.CODEC.listOf().fieldOf("picks").forGetter(CouponView::picks)
	).apply(i, CouponView::new));
	public static final StreamCodec<ByteBuf, CouponView> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, CouponView::id,
			ByteBufCodecs.VAR_INT, CouponView::version,
			ByteBufCodecs.VAR_LONG, CouponView::stake,
			ByteBufCodecs.BOOL, CouponView::minerals,
			ByteBufCodecs.VAR_INT, CouponView::state,
			ByteBufCodecs.VAR_LONG, CouponView::payout,
			ByteBufCodecs.BOOL, CouponView::claimed,
			PickView.STREAM_CODEC.apply(ByteBufCodecs.list()), CouponView::picks,
			CouponView::new);

	public static CouponView of(Coupon coupon) {
		List<PickView> picks = new ArrayList<>();
		for (Pick pick : coupon.picks) {
			picks.add(new PickView(pick.league.id, pick.home, pick.away, pick.kickoff, pick.selection.ordinal(), pick.odds, pick.status.ordinal(),
					pick.score, pick.clock));
		}
		return new CouponView(coupon.id, coupon.version, coupon.stake, coupon.minerals, coupon.state.ordinal(), coupon.payout, coupon.claimed, picks);
	}

	public Coupon.State couponState() {
		return Coupon.State.of(state);
	}

	public double totalOdds() {
		double total = 1;
		for (PickView pick : picks) {
			total *= pick.odds();
		}
		return total;
	}

	/** What it pays if every pick still standing wins. */
	public long potential(long maxPayout) {
		double total = 1;
		for (PickView pick : picks) {
			total *= Pick.Status.of(pick.status()) == Pick.Status.VOID ? 1 : pick.odds();
		}
		return Math.min(maxPayout, (long) Math.floor(stake * total + 1e-6));
	}
}
