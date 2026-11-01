package pl.pansage.gamblecraft.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.PlinkoRisk;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.PayoutMailbox;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/**
 * A plinko board: every use drops a ball, whose path through twelve rows of pegs the server draws at once; each
 * ball is paid when it reaches its bin. Several balls can be on the board; everyone around sees them fall.
 */
public class PlinkoBlockEntity extends BlockEntity {
	public static final int[] BETS = {1, 5, 25, 100};
	/** Ticks a ball takes from one row of pegs to the next. */
	public static final int STEP = 4;
	public static final int LAND = (ArcadeMath.PLINKO_ROWS + 1) * STEP;
	private static final int LINGER = 40;
	private static final int MAX_BALLS = 6;

	/** One ball: who dropped it and when, the bounces (bit i set = right at row i), the bin, the stake and risk. */
	public record Ball(UUID player, long start, int path, int bin, int bet, String risk, boolean paid) {
		static final Codec<Ball> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Ball::player),
				Codec.LONG.fieldOf("start").forGetter(Ball::start),
				Codec.INT.fieldOf("path").forGetter(Ball::path),
				Codec.INT.fieldOf("bin").forGetter(Ball::bin),
				Codec.INT.fieldOf("bet").forGetter(Ball::bet),
				Codec.STRING.fieldOf("risk").forGetter(Ball::risk),
				Codec.BOOL.fieldOf("paid").forGetter(Ball::paid)
		).apply(i, Ball::new));

		public boolean right(int row) {
			return (path >> row & 1) != 0;
		}

		public PlinkoRisk riskLevel() {
			return PlinkoRisk.valueOf(risk.toUpperCase(java.util.Locale.ROOT));
		}
	}

	private int bet = BETS[1];
	private PlinkoRisk risk = PlinkoRisk.MEDIUM;
	private final List<Ball> balls = new ArrayList<>();

	public PlinkoBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PLINKO, pos, state);
	}

	public int bet() {
		return bet;
	}

	public PlinkoRisk risk() {
		return risk;
	}

	public List<Ball> balls() {
		return balls;
	}

	public void cycleBet(ServerPlayer player) {
		int i = 0;
		while (i < BETS.length && BETS[i] != bet) {
			i++;
		}
		bet = BETS[(i + 1) % BETS.length];
		player.sendOverlayMessage(Component.translatable("gamblecraft.slots.bet", bet));
		sync();
	}

	public void cycleRisk(ServerPlayer player) {
		risk = PlinkoRisk.values()[(risk.ordinal() + 1) % PlinkoRisk.values().length];
		player.sendOverlayMessage(Component.translatable("gamblecraft.plinko.risk", Component.translatable("gamblecraft.plinko.risk." + risk.key())));
		sync();
	}

	/** Drops a ball for the current bet and risk. */
	public void drop(ServerPlayer player) {
		if (!AgeGate.require(player)) {
			return;
		}
		if (balls.stream().filter(b -> !b.paid()).count() >= MAX_BALLS) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.plinko.full"));
			return;
		}
		if (!ChipCurrency.INSTANCE.withdraw(player, bet)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.slots.no_chips", bet));
			return;
		}
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).staked(player.getUUID(), bet);
		ArcadeMath.PlinkoDrop drop = ArcadeMath.dropPlinko(risk, new java.util.SplittableRandom(level.getRandom().nextLong()));
		int path = 0;
		for (int i = 0; i < drop.rights().length; i++) {
			if (drop.rights()[i]) {
				path |= 1 << i;
			}
		}
		balls.add(new Ball(player.getUUID(), level.getGameTime(), path, drop.bin(), bet, risk.key(), false));
		level.playSound(null, worldPosition.above(), pl.pansage.gamblecraft.registry.ModSounds.CHIP_CLINK, SoundSource.BLOCKS, 0.6F, 1.1F);
		sync();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, PlinkoBlockEntity board) {
		if (!(level instanceof ServerLevel serverLevel) || board.balls.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		boolean changed = false;
		for (int i = 0; i < board.balls.size(); i++) {
			Ball ball = board.balls.get(i);
			long t = now - ball.start();
			if (!ball.paid() && t > 0 && t % STEP == 0 && t < LAND) {
				level.playSound(null, pos.above(), pl.pansage.gamblecraft.registry.ModSounds.PLINKO_PEG, SoundSource.BLOCKS, 0.45F, 0.8F + (t / STEP) * 0.05F);
			}
			if (!ball.paid() && t >= LAND) {
				board.balls.set(i, new Ball(ball.player(), ball.start(), ball.path(), ball.bin(), ball.bet(), ball.risk(), true));
				board.pay(serverLevel, ball);
				changed = true;
			}
		}
		if (board.balls.removeIf(b -> b.paid() && now - b.start() > LAND + LINGER)) {
			changed = true;
		}
		if (changed) {
			board.sync();
		}
	}

	private void pay(ServerLevel level, Ball ball) {
		double multiplier = ball.riskLevel().bins[ball.bin()];
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(ball.player());
		long won = ArcadeMath.payout(ball.bet(), multiplier);
		won += pl.pansage.gamblecraft.game.Luck.bonus(player, won, level.getRandom());
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).won(ball.player(), won);
		if (won > 0) {
			if (player != null) {
				ChipCurrency.INSTANCE.deposit(player, won);
			} else {
				PayoutMailbox.get(level.getServer()).post(ball.player(), ChipCurrency.INSTANCE.asStacks(won));
			}
		}
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.plinko.landed", String.valueOf(multiplier), won));
		}
		if (won > 0) {
			pl.pansage.gamblecraft.game.Celebrate.win(level, player, won, ball.bet(), net.minecraft.world.phys.Vec3.atCenterOf(worldPosition.above()), false);
		} else {
			level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.5F, 0.7F);
		}
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// balls still falling are paid as if they had landed
		if (level instanceof ServerLevel serverLevel) {
			for (Ball ball : balls) {
				if (!ball.paid()) {
					pay(serverLevel, ball);
				}
			}
			balls.clear();
		}
		super.preRemoveSideEffects(pos, state);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("bet", bet);
		output.putString("risk", risk.key());
		output.store("balls", Ball.CODEC.listOf(), List.copyOf(balls));
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		bet = input.getIntOr("bet", BETS[1]);
		try {
			risk = PlinkoRisk.valueOf(input.getStringOr("risk", "medium").toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			risk = PlinkoRisk.MEDIUM;
		}
		balls.clear();
		balls.addAll(input.read("balls", Ball.CODEC.listOf()).orElse(List.of()));
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
