package pl.pansage.gamblecraft.block.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.game.PayoutMailbox;

/**
 * A shared-round casino game (wheel of fortune, dice, crash): the first chip opens a betting window, then the game
 * plays out for everyone, winners are paid and the next round can start. Bets and the round's state are synced to
 * every player near the machine, who all watch the same thing.
 */
public abstract class ArcadeBlockEntity extends BlockEntity {
	public enum Phase { IDLE, BETTING, PLAYING, RESULT }

	public static final long MAX_STAKE = 2000;

	/** Chips one player has on one option; {@code cashedOut} is the crash multiplier x100 they left at (0 = still in). */
	public record Stake(UUID player, String option, long amount, int cashedOut) {
		static final Codec<Stake> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("player").forGetter(Stake::player),
				Codec.STRING.fieldOf("option").forGetter(Stake::option),
				Codec.LONG.fieldOf("amount").forGetter(Stake::amount),
				Codec.INT.optionalFieldOf("cashed_out", 0).forGetter(Stake::cashedOut)
		).apply(i, Stake::new));
	}

	protected Phase phase = Phase.IDLE;
	protected long phaseStart;
	protected final List<Stake> stakes = new ArrayList<>();

	protected ArcadeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** The options players can bet on, in display order. */
	public abstract List<String> options();

	/** Total returned per chip on {@code option} when it wins, for the betting screen. */
	public abstract double payout(String option);

	protected abstract int bettingTicks();

	/** Length of the playing phase; crash overrides {@link #playOver} instead. */
	protected abstract int playTicks();

	protected int resultTicks() {
		return 80;
	}

	/** Decides the outcome as the betting closes. */
	protected abstract void startPlay(ServerLevel level);

	/** What a stake returns once the play is over (0 if it lost). */
	protected abstract long returnOf(Stake stake);

	/** One line naming the outcome, for chat and the screen. */
	public abstract Component outcome();

	/** Translation key of the game's name. */
	public abstract String titleKey();

	protected boolean playOver(long now) {
		return now - phaseStart >= playTicks();
	}

	public Phase phase() {
		return phase;
	}

	public long phaseStart() {
		return phaseStart;
	}

	public List<Stake> stakes() {
		return stakes;
	}

	public long secondsLeft(long now) {
		long length = switch (phase) {
			case BETTING -> bettingTicks();
			case PLAYING -> playTicks();
			case RESULT -> resultTicks();
			case IDLE -> 0;
		};
		return Math.max(0, (phaseStart + length - now + 19) / 20);
	}

	public long stakeOf(UUID player) {
		return stakes.stream().filter(s -> s.player().equals(player)).mapToLong(Stake::amount).sum();
	}

	public long totalOn(String option) {
		return stakes.stream().filter(s -> s.option().equals(option)).mapToLong(Stake::amount).sum();
	}

	/** Takes {@code amount} chips from the player and puts them on {@code option}. */
	public void bet(ServerPlayer player, String option, int amount) {
		if (!AgeGate.require(player) || !options().contains(option) || amount <= 0) {
			return;
		}
		if (phase == Phase.PLAYING || phase == Phase.RESULT) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.arcade.closed"));
			return;
		}
		if (stakeOf(player.getUUID()) + amount > MAX_STAKE) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.max_stake", MAX_STAKE));
			return;
		}
		if (!ChipCurrency.INSTANCE.withdraw(player, amount)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.slots.no_chips", amount));
			return;
		}
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).staked(player.getUUID(), amount);
		if (phase == Phase.IDLE) {
			phase = Phase.BETTING;
			phaseStart = level.getGameTime();
		}
		boolean merged = false;
		for (int i = 0; i < stakes.size() && !merged; i++) {
			Stake s = stakes.get(i);
			if (s.player().equals(player.getUUID()) && s.option().equals(option)) {
				stakes.set(i, new Stake(s.player(), option, s.amount() + amount, 0));
				merged = true;
			}
		}
		if (!merged) {
			stakes.add(new Stake(player.getUUID(), option, amount, 0));
		}
		level.playSound(null, worldPosition, pl.pansage.gamblecraft.registry.ModSounds.CHIP_CLINK, SoundSource.BLOCKS, 0.7F, 1.0F);
		sync();
	}

	/** Gives back all the player's chips while betting is open. */
	public void takeBack(ServerPlayer player) {
		if (phase != Phase.BETTING) {
			return;
		}
		long total = stakeOf(player.getUUID());
		if (total > 0) {
			stakes.removeIf(s -> s.player().equals(player.getUUID()));
			ChipCurrency.INSTANCE.deposit(player, total);
			pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).staked(player.getUUID(), -total);
			player.sendOverlayMessage(Component.translatable("gamblecraft.roulette.taken_back", total));
			if (stakes.isEmpty()) {
				phase = Phase.IDLE;
			}
			sync();
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ArcadeBlockEntity game) {
		if (level instanceof ServerLevel serverLevel && game.phase != Phase.IDLE) {
			game.tick(serverLevel);
		}
	}

	protected void tick(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case BETTING -> {
				if (now - phaseStart >= bettingTicks()) {
					phase = Phase.PLAYING;
					phaseStart = now;
					startPlay(level);
					announce(level, Component.translatable("gamblecraft.roulette.no_more_bets"));
					sync();
				}
			}
			case PLAYING -> {
				if (playOver(now)) {
					settle(level);
				}
			}
			case RESULT -> {
				if (now - phaseStart >= resultTicks()) {
					phase = Phase.IDLE;
					stakes.clear();
					sync();
				}
			}
			default -> {
			}
		}
	}

	/** Pays every stake what it returns; the house keeps the rest. */
	protected void settle(ServerLevel level) {
		phase = Phase.RESULT;
		phaseStart = level.getGameTime();
		Map<UUID, Long> won = new HashMap<>();
		for (Stake stake : stakes) {
			won.merge(stake.player(), returnOf(stake), Long::sum);
		}
		long biggest = 0;
		for (Map.Entry<UUID, Long> entry : won.entrySet()) {
			pay(level, entry.getKey(), entry.getValue());
			biggest = Math.max(biggest, entry.getValue());
		}
		if (biggest <= 0) {
			level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		sync();
	}

	protected void pay(ServerLevel level, UUID id, long amount) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
		amount += pl.pansage.gamblecraft.game.Luck.bonus(player, amount, level.getRandom());
		pl.pansage.gamblecraft.game.PlayerLedger.get(level.getServer()).won(id, amount);
		pl.pansage.gamblecraft.game.Celebrate.win(level, player, amount, stakeOf(id), net.minecraft.world.phys.Vec3.atCenterOf(worldPosition), false);
		if (player != null) {
			if (amount > 0) {
				ChipCurrency.INSTANCE.deposit(player, amount);
			}
			player.sendOverlayMessage(Component.translatable(amount > 0 ? "gamblecraft.arcade.won" : "gamblecraft.arcade.lost", outcome(), amount));
		} else if (amount > 0) {
			PayoutMailbox.get(level.getServer()).post(id, ChipCurrency.INSTANCE.asStacks(amount));
		}
	}

	protected void announce(ServerLevel level, Component message) {
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(worldPosition).inflate(12))) {
			player.sendOverlayMessage(message);
		}
	}

	/** Gives every chip on the machine back (it was broken mid-round). */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel serverLevel && phase != Phase.RESULT) {
			Map<UUID, Long> back = new HashMap<>();
			for (Stake stake : stakes) {
				back.merge(stake.player(), stake.amount(), Long::sum);
			}
			back.forEach((id, amount) -> {
				ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(id);
				if (player != null) {
					ChipCurrency.INSTANCE.deposit(player, amount);
				} else {
					PayoutMailbox.get(serverLevel.getServer()).post(id, ChipCurrency.INSTANCE.asStacks(amount));
				}
			});
		}
		super.preRemoveSideEffects(pos, state);
	}

	protected void sync() {
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
		output.store("stakes", Stake.CODEC.listOf(), List.copyOf(stakes));
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
		stakes.clear();
		stakes.addAll(input.read("stakes", Stake.CODEC.listOf()).orElse(List.of()));
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
