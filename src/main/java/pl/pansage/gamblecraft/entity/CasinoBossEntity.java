package pl.pansage.gamblecraft.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.core.games.GameKind;
import pl.pansage.gamblecraft.core.games.HoldemGame;
import pl.pansage.gamblecraft.game.PlayerLedger;
import pl.pansage.gamblecraft.game.TablePlacement;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * The casino's boss, in the VIP room behind his two elite guards. Once a day he takes on a VIP card holder at
 * heads-up Hold'em: he sits down with {@link #BANK} chips for the whole match at blinds of 50/100. Break him and the
 * whole pot is yours, with the Golden Signet to prove it. Raise a hand to him and his guards (and the alarm) answer.
 */
public class CasinoBossEntity extends CasinoDealerEntity {
	public static final long BANK = pl.pansage.gamblecraft.game.GambleConfig.get().bossBank;
	public static final long SMALL_BLIND = pl.pansage.gamblecraft.game.GambleConfig.get().bossSmallBlind;

	public CasinoBossEntity(EntityType<? extends CasinoBossEntity> type, Level level) {
		super(type, level);
		setGame(GameKind.HOLDEM);
	}

	@Override
	public GameKind game() {
		return GameKind.HOLDEM;
	}

	public static long today(Level level) {
		return level.getGameTime() / 24000;
	}

	@Override
	public void openFor(ServerPlayer player) {
		if (table() != null) {
			if (table().session().game().seatOf(player.getUUID()) >= 0) {
				table().join(player);
			} else {
				player.sendOverlayMessage(Component.translatable("gamblecraft.boss.busy"));
			}
			return;
		}
		if (player.getInventory().countItem(ModItems.VIP_CARD) == 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.boss.vip_only"));
			gesture(GESTURE_SULK);
			return;
		}
		PlayerLedger ledger = PlayerLedger.get(player.level().getServer());
		long today = today(player.level());
		if (ledger.duelDay(player.getUUID()) == today) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.boss.tomorrow"));
			return;
		}
		if (felt() == null) {
			return;
		}
		if (TablePlacement.createDuelTable(player, this, felt(), SMALL_BLIND, BANK) != null) {
			ledger.setDuelDay(player.getUUID(), today);
			player.sendSystemMessage(Component.translatable("gamblecraft.boss.challenge", BANK, SMALL_BLIND, SMALL_BLIND * 2).withStyle(ChatFormatting.GOLD));
			gesture(GESTURE_SHUFFLE);
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount % 60 == 0) {
			// the cigar
			double yaw = Math.toRadians(getYHeadRot());
			level.sendParticles(ParticleTypes.SMOKE, getX() - Math.sin(yaw) * 0.45, getEyeY() - 0.2, getZ() + Math.cos(yaw) * 0.45, 2, 0.02, 0.05, 0.02, 0.005);
		}
		if (table() != null && tickCount % 10 == 0 && table().session().game() instanceof HoldemGame duel && duel.houseBroke()) {
			for (java.util.UUID id : duel.players()) {
				ServerPlayer winner = level.getServer().getPlayerList().getPlayer(id);
				if (winner != null) {
					crown(level, winner);
				}
			}
			table().discard();
		}
	}

	/** The boss is broke: the signet for the winner, and the room hears of it. */
	private void crown(ServerLevel level, ServerPlayer winner) {
		ItemStack signet = new ItemStack(ModItems.GOLDEN_SIGNET);
		winner.getInventory().placeItemBackInInventory(signet, net.minecraft.util.Prediction.SERVER_ONLY);
		Component news = Component.translatable("gamblecraft.boss.beaten", winner.getDisplayName()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(blockPosition()).inflate(48))) {
			player.sendSystemMessage(news);
		}
		pl.pansage.gamblecraft.game.Celebrate.win(level, winner, BANK, BANK, winner.position(), true);
		gesture(GESTURE_SULK);
	}

	/** Hit the boss and every guard near him turns on you. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof LivingEntity attacker) {
			for (CasinoGuardEntity guard : level.getEntitiesOfClass(CasinoGuardEntity.class, new AABB(blockPosition()).inflate(16))) {
				guard.setTarget(attacker);
			}
		}
		return super.hurtServer(level, source, amount);
	}
}
