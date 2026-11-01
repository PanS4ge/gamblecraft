package pl.pansage.gamblecraft.menu;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.item.CardDeckItem;
import pl.pansage.gamblecraft.item.PlayingCardItem;
import pl.pansage.gamblecraft.registry.ModItems;
import pl.pansage.gamblecraft.registry.ModMenus;

/**
 * Chest-like view of a deck in the player's hand. Only cards fit; every change is written straight back
 * to the deck item, and the deck's own slot is locked while the menu is open so it cannot be moved or duplicated.
 */
public class DeckMenu extends AbstractContainerMenu {
	public static final int ROWS = 6;
	public static final int DECK_SLOTS = ROWS * 9;
	public static final int BUTTON_SHUFFLE = 0;

	private final InteractionHand hand;
	private final Player player;
	private final DeckContainer container;
	private final ItemStack deck;

	/** Client side: contents arrive through normal slot sync. */
	public DeckMenu(int containerId, Inventory inventory, InteractionHand hand) {
		this(containerId, inventory, hand, ItemStack.EMPTY);
	}

	private DeckMenu(int containerId, Inventory inventory, InteractionHand hand, ItemStack deck) {
		super(ModMenus.DECK, containerId);
		this.hand = hand;
		this.player = inventory.player;
		this.deck = deck;
		this.container = new DeckContainer();
		if (!deck.isEmpty()) {
			List<Integer> ids = CardDeckItem.cards(deck);
			for (int i = 0; i < ids.size() && i < DECK_SLOTS; i++) {
				container.setItemSilently(i, PlayingCardItem.stackOf(Card.of(ids.get(i))));
			}
		}
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new CardSlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		int lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1;
		int invTop = 18 + ROWS * 18 + 13;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invTop + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			addSlot(col == lockedSlot ? new LockedSlot(inventory, col, 8 + col * 18, invTop + 58)
					: new Slot(inventory, col, 8 + col * 18, invTop + 58));
		}
	}

	public InteractionHand hand() {
		return hand;
	}

	private void save() {
		if (deck.isEmpty()) {
			return;
		}
		List<Integer> ids = new ArrayList<>();
		for (int i = 0; i < DECK_SLOTS; i++) {
			Card card = PlayingCardItem.cardOf(container.getItem(i));
			if (card != null) {
				for (int n = 0; n < container.getItem(i).getCount(); n++) {
					ids.add(card.id());
				}
			}
		}
		CardDeckItem.setCards(deck, ids);
	}

	@Override
	public boolean stillValid(Player player) {
		return deck.isEmpty() || player.getItemInHand(hand) == deck;
	}

	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (id != BUTTON_SHUFFLE || deck.isEmpty()) {
			return false;
		}
		List<ItemStack> cards = new ArrayList<>();
		for (int i = 0; i < DECK_SLOTS; i++) {
			if (!container.getItem(i).isEmpty()) {
				cards.add(container.getItem(i));
			}
		}
		for (int i = cards.size() - 1; i > 0; i--) {
			int j = player.getRandom().nextInt(i + 1);
			ItemStack tmp = cards.get(i);
			cards.set(i, cards.get(j));
			cards.set(j, tmp);
		}
		for (int i = 0; i < DECK_SLOTS; i++) {
			container.setItemSilently(i, i < cards.size() ? cards.get(i) : ItemStack.EMPTY);
		}
		save();
		broadcastChanges();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		if (index < DECK_SLOTS) {
			if (!moveItemStackTo(stack, DECK_SLOTS, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (!stack.is(ModItems.CARD) || !moveItemStackTo(stack, 0, DECK_SLOTS, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return original;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		save();
	}

	/** Container that writes back to the deck item whenever it changes. */
	private final class DeckContainer extends SimpleContainer {
		private boolean silent;

		DeckContainer() {
			super(DECK_SLOTS);
		}

		void setItemSilently(int slot, ItemStack stack) {
			silent = true;
			setItem(slot, stack);
			silent = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!silent) {
				save();
			}
		}
	}

	private static final class CardSlot extends Slot {
		CardSlot(SimpleContainer container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return stack.is(ModItems.CARD) && PlayingCardItem.cardOf(stack) != null;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	/** The hotbar slot holding the open deck. */
	private static final class LockedSlot extends Slot {
		LockedSlot(Inventory inventory, int index, int x, int y) {
			super(inventory, index, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	public record Provider(InteractionHand hand) implements ExtendedMenuProvider<InteractionHand> {
		@Override
		public Component getDisplayName() {
			return Component.translatable("item.gamblecraft.card_deck");
		}

		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			return new DeckMenu(containerId, inventory, hand, player.getItemInHand(hand));
		}

		@Override
		public InteractionHand getScreenOpeningData(ServerPlayer player) {
			return hand;
		}
	}
}
