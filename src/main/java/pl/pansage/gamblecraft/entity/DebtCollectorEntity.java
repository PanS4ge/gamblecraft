package pl.pansage.gamblecraft.entity;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.ItemCurrency;
import pl.pansage.gamblecraft.game.LoanData;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The loan shark's men, sent when a debt is overdue: suits and clubs. They hit hard, and every blow takes chips
 * (or emeralds, ten chips each) off the debt. Once it is paid they leave; killing one of them puts a tenth on it.
 */
public class DebtCollectorEntity extends ThugEntity {
	public DebtCollectorEntity(EntityType<? extends DebtCollectorEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	/** Suit and club. */
	public void dress() {
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.SUIT_JACKET));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.SUIT_TROUSERS));
		setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.SUIT_SHOES));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CLUB));
	}

	private List<DebtCollectorEntity> crew(ServerLevel level) {
		return level.getEntitiesOfClass(DebtCollectorEntity.class, new AABB(blockPosition()).inflate(48),
				c -> c.victim() != null && c.victim().equals(victim()));
	}

	@Override
	protected void onHit(ServerLevel level, ServerPlayer player) {
		LoanData loans = LoanData.get(level.getServer());
		long owed = loans.owed(player.getUUID());
		long want = Math.min(owed, Math.max(25, owed / 10));
		long got = 0;
		if (want > 0) {
			long chips = Math.min(want, ChipCurrency.INSTANCE.balance(player));
			if (chips > 0 && ChipCurrency.INSTANCE.withdraw(player, chips)) {
				got += chips;
			}
			ItemCurrency emeralds = new ItemCurrency(Items.EMERALD, 1);
			long gems = Math.min((want - got + 9) / 10, emeralds.balance(player));
			if (gems > 0 && emeralds.withdraw(player, gems)) {
				got += gems * 10;
			}
		}
		long left = loans.collect(player.getUUID(), got);
		if (got > 0) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.collector.took", Math.min(got, owed), left).withStyle(ChatFormatting.RED));
		}
		if (left <= 0) {
			player.sendSystemMessage(Component.translatable("gamblecraft.collector.settled").withStyle(ChatFormatting.GRAY));
			crew(level).forEach(ThugEntity::leave);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel level && victim() != null && source.getEntity() instanceof ServerPlayer player
				&& player.getUUID().equals(victim()) && !dead) {
			long owed = LoanData.get(level.getServer()).penalty(player.getUUID());
			if (owed > 0) {
				player.sendSystemMessage(Component.translatable("gamblecraft.collector.penalty", owed).withStyle(ChatFormatting.DARK_RED));
			}
		}
		super.die(source);
	}
}
