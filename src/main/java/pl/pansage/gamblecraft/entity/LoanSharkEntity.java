package pl.pansage.gamblecraft.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ChipShop;
import pl.pansage.gamblecraft.game.LoanData;
import pl.pansage.gamblecraft.item.PromissoryNoteItem;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The loan shark in his back room: 100, 500 or 2000 chips on the spot, a quarter on top, three days to pay, an IOU
 * to remember it by. Pay him back here, all at once or bit by bit. Late, and his collectors come for it.
 */
public class LoanSharkEntity extends CasinoNpc implements ChipShop.Keeper {
	public LoanSharkEntity(EntityType<? extends LoanSharkEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new StayHomeGoal(1));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			ChipShop.open(serverPlayer, this);
			getLookControl().setLookAt(player);
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component shopTitle() {
		return Component.translatable("gamblecraft.loan.title");
	}

	@Override
	public List<ChipShop.Offer> offers(ServerPlayer player) {
		LoanData loans = LoanData.get(player.level().getServer());
		LoanData.Loan loan = loans.loan(player.getUUID());
		List<ChipShop.Offer> offers = new ArrayList<>();
		long due = player.level().getGameTime() + LoanData.TERM;
		for (long amount : LoanData.AMOUNTS) {
			offers.add(new ChipShop.Offer(ChipCurrency.INSTANCE.asStacks(amount).getFirst(), Component.translatable("gamblecraft.loan.offer", amount,
					LoanData.repayment(amount), PromissoryNoteItem.day(due)), loan == null));
		}
		if (loan != null) {
			offers.add(new ChipShop.Offer(new ItemStack(ModItems.PROMISSORY_NOTE), Component.translatable("gamblecraft.loan.repay", loan.owed(),
					PromissoryNoteItem.day(loan.due())), ChipCurrency.INSTANCE.balance(player) > 0));
		}
		return offers;
	}

	@Override
	public void choose(ServerPlayer player, int index) {
		LoanData loans = LoanData.get(player.level().getServer());
		if (index < LoanData.AMOUNTS.length) {
			long amount = LoanData.AMOUNTS[index];
			if (loans.lend(player, amount)) {
				LoanData.Loan loan = loans.loan(player.getUUID());
				ItemStack note = PromissoryNoteItem.of(loan.owed(), loan.due());
				player.getInventory().placeItemBackInInventory(note, net.minecraft.util.Prediction.SERVER_ONLY);
				player.sendSystemMessage(Component.translatable("gamblecraft.loan.lent", amount, loan.owed(), PromissoryNoteItem.day(loan.due()))
						.withStyle(ChatFormatting.GOLD));
				gesture(GESTURE_DEAL);
			}
			return;
		}
		long left = loans.repay(player);
		if (left <= 0) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(ModItems.PROMISSORY_NOTE)) {
					player.getInventory().setItem(i, ItemStack.EMPTY);
				}
			}
			player.sendSystemMessage(Component.translatable("gamblecraft.loan.paid").withStyle(ChatFormatting.GREEN));
			gesture(GESTURE_CHEER);
		} else {
			player.sendOverlayMessage(Component.translatable("gamblecraft.loan.part_paid", left));
		}
	}
}
