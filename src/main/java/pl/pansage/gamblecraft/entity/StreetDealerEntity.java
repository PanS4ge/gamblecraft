package pl.pansage.gamblecraft.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.Bank;
import pl.pansage.gamblecraft.game.VillageEconomy;
import pl.pansage.gamblecraft.item.CardDeckItem;
import pl.pansage.gamblecraft.item.PlayingCardItem;
import pl.pansage.gamblecraft.network.TablePayloads.Accepted;
import pl.pansage.gamblecraft.network.TablePayloads.OpenDealer;

/**
 * "Krupier Wannabe": a village card sharp. He deals any card game on any block nearby and takes whatever
 * the village trades in as stakes, paying from a purse that refills every day.
 */
public class StreetDealerEntity extends CasinoNpc {
	private static final int ECONOMY_CACHE_TICKS = 20 * 60 * 10;

	private CardTableEntity table;
	private double purse;
	private long purseDay = -1;
	private Map<Item, Double> economy;
	private long economyTime = -ECONOMY_CACHE_TICKS;

	public StreetDealerEntity(EntityType<? extends StreetDealerEntity> type, Level level) {
		super(type, level);
		setItemSlot(EquipmentSlot.MAINHAND, CardDeckItem.full());
		setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new TendTableGoal());
		addIdleGoals();
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 6.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	/** What he does between games: a village dealer wanders round his stall and runs from trouble. */
	protected void addIdleGoals() {
		goalSelector.addGoal(1, new PanicGoal(this, 0.6));
		goalSelector.addGoal(5, new StayHomeGoal(12));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.35));
	}

	@Override
	protected boolean isFree() {
		return table() == null;
	}

	// ---------------------------------------------------------------- purse and prices

	/** Daily purse in emeralds: bigger villages give the dealer more to play with. */
	private void refreshPurse(ServerLevel level) {
		long day = level.getGameTime() / 24000L;
		if (day != purseDay) {
			purseDay = day;
			purse = Math.min(64, 16 + 2 * economy(level).size());
		}
	}

	public Map<Item, Double> economy(ServerLevel level) {
		if (economy == null || level.getGameTime() - economyTime > ECONOMY_CACHE_TICKS) {
			economy = VillageEconomy.scan(level, position());
			economyTime = level.getGameTime();
		}
		return economy;
	}

	public Bank bank() {
		return new Bank() {
			@Override
			public double available() {
				return Math.max(0, purse);
			}

			@Override
			public void settle(double houseNet) {
				purse += houseNet;
				if (houseNet < -0.5) {
					gesture(GESTURE_SULK);
				} else if (houseNet > 0.5) {
					gesture(GESTURE_CHEER);
				}
			}
		};
	}

	// ---------------------------------------------------------------- tables

	public CardTableEntity table() {
		return table != null && table.isAlive() ? table : null;
	}

	public void setTable(CardTableEntity table) {
		this.table = table;
		gesture(GESTURE_SHUFFLE);
	}

	void tableClosed(CardTableEntity closed) {
		if (table == closed) {
			table = null;
		}
	}

	/** Where the dealer stands: one block from the table centre on the dealer's side. */
	public Vec3 dealerSpot() {
		CardTableEntity t = table();
		if (t == null) {
			return null;
		}
		double rad = Math.toRadians(t.facing());
		return t.position().add(-Math.sin(rad) * 1.15, -1.0, Math.cos(rad) * 1.15);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !isAlive()) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (!AgeGate.require(serverPlayer)) {
			return InteractionResult.CONSUME;
		}
		openFor(serverPlayer);
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Joins the running table, or shows the game picker. */
	public void openFor(ServerPlayer serverPlayer) {
		if (table() != null) {
			table().join(serverPlayer);
			return;
		}
		ServerLevel level = serverPlayer.level();
		refreshPurse(level);
		if (purse < 1) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.dealer.broke"));
			gesture(GESTURE_SULK);
			return;
		}
		List<Accepted> accepted = new ArrayList<>();
		for (Map.Entry<Item, Double> entry : economy(level).entrySet()) {
			accepted.add(new Accepted(BuiltInRegistries.ITEM.getKey(entry.getKey()).toString(), entry.getValue()));
		}
		List<String> games = java.util.Arrays.stream(GameKind.values()).map(GameKind::id).toList();
		ServerPlayNetworking.send(serverPlayer, new OpenDealer(getId(), accepted, games, purse));
		getLookControl().setLookAt(serverPlayer);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		CardTableEntity t = table();
		if (t != null && source.getEntity() instanceof Player) {
			t.discard();
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (random.nextFloat() < 0.25F) {
			spawnAtLocation(level, CardDeckItem.full());
			return;
		}
		List<Card> deck = Card.fullDeck();
		for (int i = 0; i < 10; i++) {
			Card card = deck.remove(random.nextInt(deck.size()));
			spawnAtLocation(level, PlayingCardItem.stackOf(card));
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return table() != null ? null : super.getAmbientSound();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putDouble("gamblecraft_purse", purse);
		output.putLong("gamblecraft_purse_day", purseDay);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		purse = input.getDoubleOr("gamblecraft_purse", 0);
		purseDay = input.getLongOr("gamblecraft_purse_day", -1);
		setItemSlot(EquipmentSlot.MAINHAND, CardDeckItem.full());
	}

	/** Walks to the dealer's side of the active table and faces it. */
	private final class TendTableGoal extends Goal {
		TendTableGoal() {
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return table() != null;
		}

		@Override
		public void tick() {
			Vec3 spot = dealerSpot();
			if (spot == null) {
				return;
			}
			if (position().distanceToSqr(spot.x, getY(), spot.z) > 0.6) {
				getNavigation().moveTo(spot.x, spot.y, spot.z, 0.6);
			} else {
				getNavigation().stop();
			}
			getLookControl().setLookAt(table().getX(), table().getY(), table().getZ());
		}
	}
}
