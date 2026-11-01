package pl.pansage.gamblecraft.sportsbook.item;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.sportsbook.Bookmaker;
import pl.pansage.gamblecraft.sportsbook.Sportsbook;
import pl.pansage.gamblecraft.sportsbook.SportsbookConfig;
import pl.pansage.gamblecraft.sportsbook.bet.Coupon;
import pl.pansage.gamblecraft.sportsbook.bet.Pick;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.feed.Outcome;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/**
 * A bet coupon. Its description lists every pick with a status mark (a clock while waiting, a ring while the game
 * is on, a tick for a win, a cross for a loss, a dash for void) and updates itself while it is carried. When the
 * last pick comes in the coupon turns into a green-named pouch of winnings (or a red "lost" slip); using the pouch
 * pays the chips out.
 */
public class CouponItem extends Item {
	public static final FontDescription ICONS = new FontDescription.Resource(Sportsbook.id("icons"));
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

	public CouponItem(Properties properties) {
		super(properties);
	}

	/** The status mark: a glyph of the add-on's icon font (font/icons.json), drawn in its own colours. */
	public static MutableComponent icon(Pick.Status status) {
		return Component.literal(String.valueOf((char) (0xE000 + status.ordinal()))).withStyle(Style.EMPTY.withFont(ICONS).withColor(0xFFFFFF));
	}

	public static String odds(double odds) {
		return String.format(Locale.ROOT, "%.2f", odds);
	}

	@Override
	public Component getName(ItemStack stack) {
		CouponView view = stack.get(SbRegistry.COUPON);
		if (view == null) {
			return super.getName(stack);
		}
		return switch (view.couponState()) {
			case WON -> Component.translatable(getDescriptionId() + ".won").withStyle(ChatFormatting.GREEN);
			case LOST -> Component.translatable(getDescriptionId() + ".lost").withStyle(ChatFormatting.RED);
			case VOID -> Component.translatable(getDescriptionId() + ".void").withStyle(ChatFormatting.GRAY);
			case OPEN -> super.getName(stack);
		};
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		CouponView view = stack.get(SbRegistry.COUPON);
		if (view == null) {
			lines.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
			return;
		}
		String prefix = "gamblecraft_sportsbook.coupon.";
		lines.accept((view.picks().size() == 1 ? Component.translatable(prefix + "single")
				: Component.translatable(prefix + "accumulator", view.picks().size())).withStyle(ChatFormatting.GRAY));
		ZoneId zone = ZoneId.systemDefault();
		for (CouponView.PickView pick : view.picks()) {
			Pick.Status status = Pick.Status.of(pick.status());
			League league = League.byId(pick.league());
			MutableComponent head = Component.empty().append(icon(status)).append(" ")
					.append(league == null ? Component.literal(pick.league()) : Component.translatable(league.translationKey()))
					.withStyle(ChatFormatting.DARK_AQUA);
			head.append(Component.literal(" · " + TIME.format(Instant.ofEpochMilli(pick.kickoff()).atZone(zone))).withStyle(ChatFormatting.DARK_GRAY));
			if (!pick.score().isEmpty()) {
				head.append(Component.literal("  " + pick.score()).withStyle(ChatFormatting.WHITE));
			}
			if (status == Pick.Status.LIVE && !pick.clock().isEmpty()) {
				head.append(Component.literal(" " + pick.clock()).withStyle(ChatFormatting.YELLOW));
			}
			lines.accept(head);
			lines.accept(Component.literal("   " + pick.home() + " – " + pick.away()).withStyle(ChatFormatting.WHITE));
			lines.accept(Component.literal("   ").append(Component.translatable(prefix + "pick", selection(pick), odds(pick.odds())))
					.withStyle(status == Pick.Status.LOST ? ChatFormatting.DARK_RED : status == Pick.Status.WON ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		}
		lines.accept(Component.translatable(prefix + "total_odds", odds(view.totalOdds())).withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable(view.minerals() ? prefix + "stake_minerals" : prefix + "stake", view.stake()).withStyle(ChatFormatting.GRAY));
		switch (view.couponState()) {
			case OPEN -> {
				lines.accept(Component.translatable(prefix + "possible", view.potential(SportsbookConfig.get().maxPayout)).withStyle(ChatFormatting.GOLD));
				long settled = view.picks().stream().filter(p -> Pick.Status.of(p.status()).decided()).count();
				lines.accept(Component.translatable(prefix + "progress", settled, view.picks().size()).withStyle(ChatFormatting.DARK_GRAY));
			}
			case WON -> lines.accept(Component.translatable(view.claimed() ? prefix + "paid" : prefix + "won", view.payout()).withStyle(ChatFormatting.GREEN));
			case LOST -> lines.accept(Component.translatable(prefix + "lost").withStyle(ChatFormatting.RED));
			case VOID -> lines.accept(Component.translatable(view.claimed() ? prefix + "paid" : prefix + "void", view.payout()).withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component selection(CouponView.PickView pick) {
		Outcome outcome = Outcome.of(pick.selection());
		if (outcome == null) {
			return Component.literal("?");
		}
		return switch (outcome) {
			case HOME -> Component.literal("1 (" + pick.home() + ")");
			case DRAW -> Component.literal("X (").append(Component.translatable("gamblecraft_sportsbook.draw")).append(")");
			case AWAY -> Component.literal("2 (" + pick.away() + ")");
		};
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (level.getGameTime() % 20 == 7) {
			Bookmaker.refresh(stack, level.getServer());
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		CouponView view = stack.get(SbRegistry.COUPON);
		if (view == null) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			Bookmaker.claim(serverPlayer, stack);
		}
		return view.couponState() == Coupon.State.OPEN ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
	}
}
