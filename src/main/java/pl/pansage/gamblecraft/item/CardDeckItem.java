package pl.pansage.gamblecraft.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.menu.DeckMenu;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * A deck of cards: holds up to 52 cards like a bundle. Use opens a chest-like view of the cards,
 * sneak-use draws one random card, and cards can be clicked onto the deck in the inventory.
 */
public class CardDeckItem extends Item {
	public static final int CAPACITY = 52;

	public CardDeckItem(Properties properties) {
		super(properties);
	}

	public static List<Integer> cards(ItemStack deck) {
		return deck.getOrDefault(ModComponents.DECK_CARDS, List.of());
	}

	public static void setCards(ItemStack deck, List<Integer> cards) {
		deck.set(ModComponents.DECK_CARDS, List.copyOf(cards));
		float fill = cards.size() / (float) CAPACITY;
		deck.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(fill), List.of(), List.of(), List.of()));
	}

	public static ItemStack full() {
		ItemStack deck = new ItemStack(ModItems.CARD_DECK);
		List<Integer> ids = new ArrayList<>();
		for (int id = 0; id < Card.DECK_SIZE; id++) {
			ids.add(id);
		}
		setCards(deck, ids);
		return deck;
	}

	public static ItemStack empty() {
		ItemStack deck = new ItemStack(ModItems.CARD_DECK);
		setCards(deck, List.of());
		return deck;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack deck = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ServerPlayer serverPlayer = (ServerPlayer) player;
		if (player.isShiftKeyDown()) {
			drawOne(serverPlayer, deck);
		} else {
			serverPlayer.openMenu(new DeckMenu.Provider(hand));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void drawOne(ServerPlayer player, ItemStack deck) {
		List<Integer> cards = new ArrayList<>(cards(deck));
		ServerLevel level = player.level();
		if (cards.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.deck.empty"));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUNDLE_INSERT_FAIL, SoundSource.PLAYERS, 0.8F, 1.0F);
			return;
		}
		Card card = Card.of(cards.remove(player.getRandom().nextInt(cards.size())));
		setCards(deck, cards);
		ItemStack drawn = PlayingCardItem.stackOf(card);
		ItemStackTemplate particle = ItemStackTemplate.fromNonEmptyStack(drawn);
		if (!player.getInventory().add(drawn)) {
			player.drop(drawn, false, Prediction.SERVER_ONLY);
		}
		player.sendOverlayMessage(Component.translatable("gamblecraft.deck.drew", PlayingCardItem.cardName(card)));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.3F + level.getRandom().nextFloat() * 0.3F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, particle),player.getX(), player.getEyeY() - 0.3, player.getZ(), 6, 0.2, 0.1, 0.2, 0.05);
	}

	/** Clicking a card onto the deck (or the deck onto a card) puts the card in, like a bundle. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack deck, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.SECONDARY || !other.is(ModItems.CARD) || !slot.allowModification(player)) {
			return false;
		}
		return insert(deck, other, player);
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack deck, Slot slot, ClickAction action, Player player) {
		if (action != ClickAction.SECONDARY) {
			return false;
		}
		ItemStack other = slot.getItem();
		if (!other.is(ModItems.CARD) || !slot.allowModification(player)) {
			return false;
		}
		return insert(deck, other, player);
	}

	private static boolean insert(ItemStack deck, ItemStack cardStack, Player player) {
		Card card = PlayingCardItem.cardOf(cardStack);
		List<Integer> cards = new ArrayList<>(cards(deck));
		if (card == null || cards.size() >= CAPACITY) {
			player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 0.8F, 1.0F);
			return true;
		}
		cards.add(card.id());
		cardStack.shrink(1);
		setCards(deck, cards);
		player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.6F);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		int count = cards(stack).size();
		MutableComponent line = Component.translatable("gamblecraft.deck.count", count, CAPACITY);
		lines.accept(line.withStyle(net.minecraft.ChatFormatting.GRAY));
		lines.accept(Component.translatable("gamblecraft.deck.hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
	}
}
