package pl.pansage.gamblecraft.client.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import pl.pansage.gamblecraft.client.screen.CardGameScreen;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.entity.CasinoNpc;
import pl.pansage.gamblecraft.network.TablePayloads.TableState;

/**
 * Latest table snapshots on this client, plus per-card animation timestamps worked out by comparing
 * each snapshot with the previous one (a new card slides in, a face-down card turning face-up flips).
 */
public final class ClientTables {
	public static final int DEAL_TICKS = 7;
	public static final int FLIP_TICKS = 6;

	public static final class CardAnim {
		public int card;
		public long dealtAt;
		public long flippedAt = Long.MIN_VALUE;

		CardAnim(int card, long dealtAt) {
			this.card = card;
			this.dealtAt = dealtAt;
		}
	}

	/** A stake or card a player threw at the table. */
	public record Toss(int entityId, int seat, boolean card, net.minecraft.world.item.ItemStack stack, long startedAt) {
	}

	public static final class View {
		public TableState state;
		public final Map<String, List<CardAnim>> cards = new HashMap<>();
		public final List<Toss> tosses = new ArrayList<>();
		/** Client tick of the last win/lose for the viewer, for screen effects. */
		public long resultAt = Long.MIN_VALUE;
		public String result;
		public long resultAmount;
	}

	private static final Map<Integer, View> TABLES = new HashMap<>();

	private ClientTables() {
	}

	public static View get(int tableId) {
		return TABLES.get(tableId);
	}

	public static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0 : mc.level.getGameTime();
	}

	public static void accept(TableState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		View view = TABLES.computeIfAbsent(state.tableId(), id -> new View());
		TableState previous = view.state;
		long now = now();
		boolean dealt = false;
		boolean flipped = false;
		Map<String, List<CardAnim>> next = new HashMap<>();
		for (HandView hand : state.snapshot().hands()) {
			List<CardAnim> old = view.cards.getOrDefault(hand.id(), List.of());
			List<CardAnim> anims = new ArrayList<>();
			for (int i = 0; i < hand.cards().size(); i++) {
				int card = hand.cards().get(i);
				if (i < old.size()) {
					CardAnim anim = old.get(i);
					if (anim.card == -1 && card >= 0) {
						anim.flippedAt = now;
						flipped = true;
					} else if (anim.card >= 0 && card >= 0 && anim.card != card) {
						anim.dealtAt = now;
						dealt = true;
					}
					anim.card = card;
					anims.add(anim);
				} else {
					anims.add(new CardAnim(card, previous == null ? now - DEAL_TICKS : now));
					dealt |= previous != null;
				}
			}
			next.put(hand.id(), anims);
		}
		view.cards.clear();
		view.cards.putAll(next);
		view.state = state;

		Entity table = mc.level.getEntity(state.tableId());
		Entity dealer = mc.level.getEntity(state.dealerId());
		if (table != null) {
			if (dealt) {
				play(table, SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.5F + mc.level.getRandom().nextFloat() * 0.3F);
			}
			if (flipped) {
				play(table, SoundEvents.BOOK_PAGE_TURN, 0.6F, 2.0F);
			}
		}
		if (dealer instanceof CasinoNpc streetDealer) {
			if (dealt) {
				streetDealer.dealAnimation.start(streetDealer.tickCount);
			}
			if (previous != null && newLog(previous, state, "gamblecraft.log.shuffle")) {
				streetDealer.shuffleAnimation.start(streetDealer.tickCount);
				if (table != null) {
					play(table, SoundEvents.BOOK_PAGE_TURN, 0.9F, 0.9F);
				}
			}
		}
		celebrate(view, previous, state, table);

		if (state.open() && !(mc.gui.screen() instanceof CardGameScreen screen && screen.tableId() == state.tableId())) {
			mc.gui.setScreen(new CardGameScreen(state.tableId()));
		}
	}

	private static boolean newLog(TableState before, TableState after, String key) {
		List<Msg> a = before.snapshot().log();
		List<Msg> b = after.snapshot().log();
		if (b.isEmpty() || a.equals(b)) {
			return false;
		}
		return b.getLast().key().equals(key)
				|| before.snapshot().round() != after.snapshot().round() && b.stream().anyMatch(m -> m.key().equals(key));
	}

	/** Plays the win/lose effect once when the viewer's seat gets a result. */
	private static void celebrate(View view, TableState before, TableState after, Entity table) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		SeatView mine = mySeat(after);
		SeatView old = before == null ? null : mySeat(before);
		if (mine == null || mine.status() == null || old != null && Objects.equals(old.status(), mine.status())
				&& before.snapshot().round() == after.snapshot().round()) {
			return;
		}
		String status = mine.status();
		if (!List.of("win", "blackjack", "push", "lose").contains(status)) {
			return;
		}
		view.result = status;
		view.resultAt = now();
		view.resultAmount = mine.lastPayout();
		if (table == null) {
			return;
		}
		switch (status) {
			case "blackjack" -> play(table, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
			case "win" -> play(table, SoundEvents.PLAYER_LEVELUP, 0.7F, 1.2F);
			case "push" -> play(table, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F);
			default -> play(table, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F);
		}
		if (!status.equals("lose") && !status.equals("push")) {
			for (int i = 0; i < 16; i++) {
				mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
						table.getX() + (mc.level.getRandom().nextDouble() - 0.5), table.getY() + 0.2 + mc.level.getRandom().nextDouble() * 0.5,
						table.getZ() + (mc.level.getRandom().nextDouble() - 0.5), 0, 0.05, 0);
			}
		}
	}

	public static void toss(pl.pansage.gamblecraft.network.TablePayloads.TableToss toss) {
		View view = TABLES.get(toss.tableId());
		Minecraft mc = Minecraft.getInstance();
		if (view == null || mc.level == null) {
			return;
		}
		long now = now();
		view.tosses.removeIf(t -> now - t.startedAt() > 40);
		net.minecraft.world.item.ItemStack stack = ClientCurrency.stack(toss.item(), view.state == null ? 1 : view.state.minBet() * 5);
		view.tosses.add(new Toss(toss.entityId(), toss.seat(), toss.card() || stack.isEmpty(), stack, now));
		Entity thrower = mc.level.getEntity(toss.entityId());
		if (thrower != null) {
			play(thrower, toss.card() ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.ITEM_PICKUP, 0.5F, toss.card() ? 1.7F : 0.8F);
		}
	}

	public static SeatView mySeat(TableState state) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		for (SeatView seat : state.snapshot().seats()) {
			if (seat.player().equals(mc.player.getUUID())) {
				return seat;
			}
		}
		return null;
	}

	private static void play(Entity at, SoundEvent sound, float volume, float pitch) {
		Minecraft.getInstance().level.playLocalSound(at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	/** Forgets tables whose entity is gone. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			TABLES.clear();
			return;
		}
		TABLES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
	}
}
