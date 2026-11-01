package pl.pansage.gamblecraft.entity;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A mugger waiting outside for big winners. Half again as fast as a zombie (no outrunning them), dressed and armed
 * like the victim so the fight is even, after the night's winnings: every blow that lands takes a slice of them.
 * The gang leaves once it has the lot or the victim has nothing left; beaten, a mugger drops what it took.
 */
public class MuggerEntity extends ThugEntity {
	public static final double SPEED = 0.23 * pl.pansage.gamblecraft.game.GambleConfig.get().muggerSpeed;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** The winnings the gang is after. */
	private long target;

	public MuggerEntity(EntityType<? extends MuggerEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, SPEED)
				.add(Attributes.ATTACK_DAMAGE, 2.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	/** The same armour as the victim and the victim's best weapon (none of it ever drops). */
	public void equipLike(ServerPlayer player, long winnings) {
		this.target = winnings;
		for (EquipmentSlot slot : ARMOUR) {
			setItemSlot(slot, player.getItemBySlot(slot).copyWithCount(1));
		}
		setItemSlot(EquipmentSlot.MAINHAND, bestWeapon(player));
		ItemStack off = player.getItemBySlot(EquipmentSlot.OFFHAND);
		setItemSlot(EquipmentSlot.OFFHAND, off.is(Items.SHIELD) ? off.copyWithCount(1) : ItemStack.EMPTY);
	}

	private static ItemStack bestWeapon(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (held.has(DataComponents.WEAPON)) {
			return held.copyWithCount(1);
		}
		for (ItemStack stack : player.getInventory()) {
			if (stack.has(DataComponents.WEAPON)) {
				return stack.copyWithCount(1);
			}
		}
		return ItemStack.EMPTY;
	}

	public long target() {
		return target;
	}

	private List<MuggerEntity> gang(ServerLevel level) {
		return level.getEntitiesOfClass(MuggerEntity.class, new AABB(blockPosition()).inflate(48), m -> m.victim() != null && m.victim().equals(victim()));
	}

	@Override
	protected void onHit(ServerLevel level, ServerPlayer player) {
		List<MuggerEntity> gang = gang(level);
		long taken = gang.stream().mapToLong(ThugEntity::stolen).sum();
		long got = snatch(player, Math.min(Math.max(10, target * 15 / 100), target - taken));
		if (got > 0) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.mugger.snatched", got).withStyle(ChatFormatting.RED));
		}
		if (taken + got >= target || pl.pansage.gamblecraft.game.ChipCurrency.INSTANCE.balance(player) <= 0) {
			player.sendSystemMessage(Component.translatable("gamblecraft.mugger.gone").withStyle(ChatFormatting.GRAY));
			gang.forEach(ThugEntity::leave);
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putLong("gamblecraft_target", target);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		target = input.getLongOr("gamblecraft_target", 0);
	}
}
