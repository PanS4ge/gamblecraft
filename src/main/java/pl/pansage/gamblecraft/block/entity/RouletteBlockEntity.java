package pl.pansage.gamblecraft.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.PayoutMailbox;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/**
 * The croupier of a roulette table, living in its wheel. Bets open with the first chip on the felt and close
 * {@link #BETTING_TICKS} later ("rien ne va plus"); the ball runs, lands, winners are paid and the felt is
 * cleared. Everything is synced to everyone around the table.
 */
public class RouletteBlockEntity extends BlockEntity {
	public enum Phase { IDLE, BETTING, SPINNING, RESULT }

	public static final int BETTING_TICKS = 400;
	public static final int SPIN_TICKS = 170;
	public static final int RESULT_TICKS = 100;
	public static final long MAX_STAKE = 5000;
	public static final int HISTORY = 10;

	/** Chips one player has on one bet. */
	public record PlacedBet(UUID player, String bet, long amount) {
		static final Codec<PlacedBet> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(PlacedBet::player),
				Codec.STRING.fieldOf("bet").forGetter(PlacedBet::bet),
				Codec.LONG.fieldOf("amount").forGetter(PlacedBet::amount)
		).apply(i, PlacedBet::new));
	}

	private Phase phase = Phase.IDLE;
	private long phaseStart;
	private int result = -1;
	private final List<PlacedBet> bets = new ArrayList<>();
	private final List<Integer> history = new ArrayList<>();
	private long assembledAt = -1000;

	public RouletteBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.ROULETTE, pos, state);
	}

	public Phase phase() {
		return phase;
	}

	public long phaseStart() {
		return phaseStart;
	}

	public int result() {
		return result;
	}

	public List<PlacedBet> bets() {
		return bets;
	}

	public List<Integer> history() {
		return history;
	}

	public long assembledAt() {
		return assembledAt;
	}

	public void assembled(long time) {
		assembledAt = time;
		sync();
	}

	/** Ticks left in the current phase. */
	public long ticksLeft(long now) {
		long length = switch (phase) {
			case BETTING -> BETTING_TICKS;
			case SPINNING -> SPIN_TICKS;
			case RESULT -> RESULT_TICKS;
			case IDLE -> 0;
		};
		return Math.max(0, phaseStart + length - now);
	}

	private long stakeOf(UUID player) {
		return bets.stream().filter(b -> b.player().equals(player)).mapToLong(PlacedBet::amount).sum();
	}

	/** One chip of {@code value} on {@code bet}; the caller has already taken the chip. Returns false (keep the chip) if bets are closed. */
	public boolean placeBet(ServerPlayer player, Roulette.Bet bet, int value) {
		if (!AgeGate.require(player)) {
			return false;
		}
		if (phase == Phase.SPINNING || phase == Phase.RESULT) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.closed"));
			return false;
		}
		if (stakeOf(player.getUUID()) + value > MAX_STAKE) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.max_stake", MAX_STAKE));
			return false;
		}
		if (phase == Phase.IDLE) {
			phase = Phase.BETTING;
			phaseStart = level.getGameTime();
			result = -1;
		}
		String id = bet.id();
		for (int i = 0; i < bets.size(); i++) {
			PlacedBet b = bets.get(i);
			if (b.player().equals(player.getUUID()) && b.bet().equals(id)) {
				bets.set(i, new PlacedBet(b.player(), id, b.amount() + value));
				placed(player, bet, value);
				return true;
			}
		}
		bets.add(new PlacedBet(player.getUUID(), id, value));
		placed(player, bet, value);
		return true;
	}

	private void placed(ServerPlayer player, Roulette.Bet bet, int value) {
		pl.pansage.gamblecraft.game.PlayerLedger.get(player.level().getServer()).staked(player.getUUID(), value);
		player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.placed", value, Component.translatable("gamblecraft.roulette.bet." + bet.key()),
				describe(bet), stakeOf(player.getUUID())));
		level.playSound(null, worldPosition, pl.pansage.gamblecraft.registry.ModSounds.CHIP_CLINK, SoundSource.BLOCKS, 0.7F, 1.0F);
		sync();
	}

	private static String describe(Roulette.Bet bet) {
		return switch (bet.key()) {
			case "straight", "split", "corner", "street", "six_line" -> String.join("-", bet.numbers().stream().sorted().map(String::valueOf).toList());
			default -> "";
		};
	}

	/** Takes all of the player's chips back off the felt while bets are open. */
	public void takeBack(ServerPlayer player) {
		if (phase != Phase.BETTING) {
			return;
		}
		long total = stakeOf(player.getUUID());
		if (total <= 0) {
			return;
		}
		bets.removeIf(b -> b.player().equals(player.getUUID()));
		ChipCurrency.INSTANCE.deposit(player, total);
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).staked(player.getUUID(), -total);
		player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.taken_back", total));
		if (bets.isEmpty()) {
			phase = Phase.IDLE;
		}
		sync();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RouletteBlockEntity table) {
		if (!(level instanceof ServerLevel serverLevel) || table.phase == Phase.IDLE || !state.getValue(RouletteWheelBlock.ASSEMBLED)) {
			return;
		}
		long now = level.getGameTime();
		if (table.ticksLeft(now) > 0) {
			if (table.phase == Phase.BETTING && table.ticksLeft(now) == 60) {
				table.announce(serverLevel, "gamblecraft.roulette.last_bets");
			}
			return;
		}
		switch (table.phase) {
			case BETTING -> {
				table.phase = Phase.SPINNING;
				table.phaseStart = now;
				table.result = Roulette.spin(new java.util.SplittableRandom(level.getRandom().nextLong()));
				table.announce(serverLevel, "gamblecraft.roulette.no_more_bets");
				level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.8F, 1.2F);
			}
			case SPINNING -> table.settle(serverLevel);
			case RESULT -> {
				table.phase = Phase.IDLE;
				table.bets.clear();
			}
			default -> {
			}
		}
		table.sync();
	}

	private void announce(ServerLevel level, String key) {
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12))) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	/** The ball has landed: pay every winning bet {@code amount x 36/size}; losing chips stay with the house. */
	private void settle(ServerLevel level) {
		phase = Phase.RESULT;
		phaseStart = level.getGameTime();
		history.add(0, result);
		while (history.size() > HISTORY) {
			history.remove(history.size() - 1);
		}
		Map<UUID, Long> won = new HashMap<>();
		for (PlacedBet placed : bets) {
			Roulette.Bet bet = Roulette.Bet.fromId(placed.bet());
			if (bet.wins(result)) {
				won.merge(placed.player(), placed.amount() * bet.payout(), Long::sum);
			} else {
				won.putIfAbsent(placed.player(), 0L);
			}
		}
		Component number = Component.translatable(result == 0 ? "gamblecraft.roulette.green" : Roulette.isRed(result) ? "gamblecraft.roulette.red" : "gamblecraft.roulette.black", result);
		long biggest = 0;
		for (Map.Entry<UUID, Long> entry : won.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			long amount = entry.getValue();
			amount += pl.pansage.gamblecraft.game.Luck.bonus(player, amount, level.getRandom());
			pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).won(entry.getKey(), amount);
			biggest = Math.max(biggest, amount);
			long staked = bets.stream().filter(b -> b.player().equals(entry.getKey())).mapToLong(PlacedBet::amount).sum();
			pl.pansage.gamblecraft.game.Celebrate.win(level, player, amount, staked, Vec3.atCenterOf(worldPosition).add(0, 0.6, 0), false);
			if (player != null) {
				if (amount > 0) {
					ChipCurrency.INSTANCE.deposit(player, amount);
				}
				player.sendOverlayMessage(Component.translatable(amount > 0 ? "gamblecraft.roulette.won" : "gamblecraft.roulette.lost", number, amount));
			} else if (amount > 0) {
				PayoutMailbox.get(level.getServer()).post(entry.getKey(), ChipCurrency.INSTANCE.asStacks(amount));
			}
		}
		Vec3 top = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);
		level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.9F, 1.0F);
		if (biggest > 0) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, top.x, top.y, top.z, 15, 1.2, 0.2, 1.2, 0.05);
		}
	}

	/** The table came apart: every chip on the felt goes back to whoever put it there. */
	public void refundAll() {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Map<UUID, Long> back = new HashMap<>();
		for (PlacedBet placed : bets) {
			back.merge(placed.player(), placed.amount(), Long::sum);
		}
		for (Map.Entry<UUID, Long> entry : back.entrySet()) {
			ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				ChipCurrency.INSTANCE.deposit(player, entry.getValue());
			} else {
				PayoutMailbox.get(serverLevel.getServer()).post(entry.getKey(), ChipCurrency.INSTANCE.asStacks(entry.getValue()));
			}
		}
		bets.clear();
		phase = Phase.IDLE;
		result = -1;
		sync();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		refundAll();
		super.preRemoveSideEffects(pos, state);
	}

	/** The direction from the wheel along the felt. */
	public Direction facing() {
		return getBlockState().getValue(RouletteWheelBlock.FACING);
	}

	private void sync() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("phase", phase.name());
		output.putLong("phase_start", phaseStart);
		output.putInt("result", result);
		output.putLong("assembled_at", assembledAt);
		output.store("bets", PlacedBet.CODEC.listOf(), List.copyOf(bets));
		output.putIntArray("history", history.stream().mapToInt(Integer::intValue).toArray());
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		try {
			phase = Phase.valueOf(input.getStringOr("phase", "IDLE"));
		} catch (IllegalArgumentException e) {
			phase = Phase.IDLE;
		}
		phaseStart = input.getLongOr("phase_start", 0);
		result = input.getIntOr("result", -1);
		assembledAt = input.getLongOr("assembled_at", -1000);
		bets.clear();
		bets.addAll(input.read("bets", PlacedBet.CODEC.listOf()).orElse(List.of()));
		history.clear();
		for (int n : input.getIntArray("history").orElse(new int[0])) {
			history.add(n);
		}
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
