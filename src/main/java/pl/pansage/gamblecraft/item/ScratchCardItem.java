package pl.pansage.gamblecraft.item;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.registry.ModComponents;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * Village scratch card. It is issued by the village where you got it (bought, won or stolen): on its first tick in
 * an inventory it binds to the nearest village bell. Use it on a block to lay it there and scratch it; a winning card is paid out by the lottery
 * clerk of that same village only.
 */
public class ScratchCardItem extends Item {
	public enum Tier {
		BRONZE(1), SILVER(3), GOLD(8);

		/** Price in emeralds; prizes are multiples of it. */
		public final int price;

		Tier(int price) {
			this.price = price;
		}

		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Price of the casino version, in chips. */
		public int chips() {
			return price * ChipCurrency.PER_EMERALD;
		}
	}

	private final Tier tier;

	public ScratchCardItem(Tier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public Tier tier() {
		return tier;
	}

	public static ScratchData data(ItemStack stack) {
		return stack.get(ModComponents.SCRATCH);
	}

	/** A card already issued by the village of {@code bell}. */
	public static ItemStack issued(Item item, BlockPos bell, long seed) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.SCRATCH, new ScratchData(seed, bell.asLong(), VillageRegistry.nameIndex(bell), false, -1, false));
		return stack;
	}

	/** A card sold by a casino kiosk: its prize is paid in chips by the cashier. */
	public static ItemStack casino(Item item, long seed) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.SCRATCH, new ScratchData(seed, ScratchData.CASINO, ScratchData.CASINO_NAME, false, -1, false));
		return stack;
	}

	/** A card with a fixed seed that binds to whichever village it is first carried in. */
	public static ItemStack unbound(Item item, long seed) {
		ItemStack stack = new ItemStack(item);
		stack.set(ModComponents.SCRATCH, new ScratchData(seed, ScratchData.UNBOUND, -1, false, -1, false));
		return stack;
	}

	/** Binds an un-issued card to the village around {@code pos}. Returns false outside villages. */
	public static boolean tryBind(ItemStack stack, ServerLevel level, BlockPos pos) {
		ScratchData data = data(stack);
		if (data != null && data.bound()) {
			return true;
		}
		Optional<BlockPos> bell = VillageRegistry.nearestBell(level, pos);
		if (bell.isEmpty()) {
			return false;
		}
		long seed = data != null ? data.seed() : level.getRandom().nextLong();
		stack.set(ModComponents.SCRATCH, new ScratchData(seed, ScratchData.UNBOUND, -1, false, -1, false).bind(bell.get(), VillageRegistry.nameIndex(bell.get())));
		return true;
	}

	/** The card's outcome: fixed by its seed and the world's secret. */
	public static ScratchCards.Card outcome(ServerLevel level, ScratchData data) {
		return ScratchCards.generate(data.seed() ^ VillageRegistry.get(level.getServer()).secret());
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (entity instanceof Player && level.getGameTime() % 20 == 0) {
			ScratchData data = data(stack);
			if (data == null || !data.bound()) {
				tryBind(stack, level, entity.blockPosition());
			}
		}
	}

	/** Lays the card on the clicked block to scratch it there. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() == null) {
			return InteractionResult.PASS;
		}
		return scratchOn(context.getPlayer(), context.getHand(), context.getClickedPos());
	}

	/** Not looking at a block: lay it on the ground in front of the player. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return scratchOn(player, hand, player.blockPosition().relative(player.getDirection()).below());
	}

	private InteractionResult scratchOn(Player player, InteractionHand hand, BlockPos support) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!AgeGate.require(serverPlayer)) {
			return InteractionResult.FAIL;
		}
		ItemStack stack = player.getItemInHand(hand);
		ScratchData data = data(stack);
		if (data != null && data.scratched()) {
			serverPlayer.sendOverlayMessage(resultLine(stack, serverPlayer.blockPosition()));
			return InteractionResult.SUCCESS_SERVER;
		}
		ServerLevel level = serverPlayer.level();
		if (!tryBind(stack, level, player.blockPosition())) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.scratch.needs_village"));
			return InteractionResult.FAIL;
		}
		if (Double.isNaN(ScratchCardEntity.restingHeight(level, support))) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.scratch.needs_block"));
			return InteractionResult.FAIL;
		}
		ScratchCardEntity.place(serverPlayer, stack.split(1), support);
		return InteractionResult.SUCCESS_SERVER;
	}

	public static Component resultLine(ItemStack stack, BlockPos from) {
		ScratchData data = data(stack);
		Tier tier = ((ScratchCardItem) stack.getItem()).tier;
		if (data.claimed()) {
			return Component.translatable("gamblecraft.scratch.claimed");
		}
		if (data.multiplier() <= 0) {
			return Component.translatable("gamblecraft.scratch.no_win");
		}
		if (data.casino()) {
			return Component.translatable("gamblecraft.scratch.redeem_cashier", data.multiplier() * tier.chips());
		}
		return Component.translatable("gamblecraft.scratch.redeem_at", data.multiplier() * tier.price, VillageRegistry.name(data.name()),
				VillageRegistry.direction(from, data.bellPos()));
	}

	/** Scratches the card for good: the prize was fixed by its seed. Returns the multiplier (0 for no win). */
	public static int settle(ServerLevel level, ItemStack stack) {
		ScratchData data = data(stack);
		int multiplier = outcome(level, data).multiplier();
		stack.set(ModComponents.SCRATCH, data.scratch(multiplier));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(true), List.of(), List.of()));
		return multiplier;
	}

	/** Turns a redeemed or worthless card into a used one. */
	public static ItemStack used() {
		return new ItemStack(ModItems.USED_SCRATCH_CARD);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		ScratchData data = data(stack);
		if (data != null && data.casino()) {
			lines.accept(Component.translatable("gamblecraft.scratch.casino_card", tier.chips()).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (data.scratched()) {
				lines.accept(data.multiplier() > 0
						? Component.translatable("gamblecraft.scratch.prize_chips", data.multiplier() * tier.chips()).withStyle(ChatFormatting.GREEN)
						: Component.translatable("gamblecraft.scratch.no_win").withStyle(ChatFormatting.RED));
			} else {
				lines.accept(Component.translatable("gamblecraft.scratch.hint").withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}
		lines.accept(Component.translatable("gamblecraft.scratch.price", tier.price).withStyle(ChatFormatting.GRAY));
		if (data == null || !data.bound()) {
			lines.accept(Component.translatable("gamblecraft.scratch.unissued").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		BlockPos bell = data.bellPos();
		lines.accept(Component.translatable("gamblecraft.scratch.issued_in", VillageRegistry.name(data.name()), bell.getX(), bell.getZ())
				.withStyle(ChatFormatting.GOLD));
		if (!data.scratched()) {
			lines.accept(Component.translatable("gamblecraft.scratch.hint").withStyle(ChatFormatting.DARK_GRAY));
		} else if (data.claimed()) {
			lines.accept(Component.translatable("gamblecraft.scratch.claimed").withStyle(ChatFormatting.DARK_GRAY));
		} else if (data.multiplier() > 0) {
			lines.accept(Component.translatable("gamblecraft.scratch.prize", data.multiplier() * tier.price).withStyle(ChatFormatting.GREEN));
		} else {
			lines.accept(Component.translatable("gamblecraft.scratch.no_win").withStyle(ChatFormatting.RED));
		}
	}
}
