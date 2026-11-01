package pl.pansage.gamblecraft.block.entity;

import java.util.Arrays;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.DiceBet;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/** Two dice thrown across the felt: bet on under seven, seven, over seven or a double. */
public class DiceTableBlockEntity extends ArcadeBlockEntity {
	public static final int ROLL_TICKS = 50;
	/** Ticks into the roll when the dice hit the felt (for the clatter). */
	public static final int[] BOUNCES = {9, 20, 29, 36, 41};
	private static final List<String> OPTIONS = Arrays.stream(DiceBet.values()).map(DiceBet::key).toList();

	private int a = 6;
	private int b = 1;
	/** Cosmetic: how this throw tumbles. */
	private long throwSeed;

	public DiceTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DICE_TABLE, pos, state);
	}

	@Override
	public List<String> options() {
		return OPTIONS;
	}

	@Override
	public double payout(String option) {
		return DiceBet.valueOf(option.toUpperCase(java.util.Locale.ROOT)).payout;
	}

	@Override
	protected int bettingTicks() {
		return 300;
	}

	@Override
	protected int playTicks() {
		return ROLL_TICKS;
	}

	@Override
	public String titleKey() {
		return "block.gamblecraft.dice_table";
	}

	@Override
	protected void startPlay(ServerLevel level) {
		ArcadeMath.DiceRoll roll = ArcadeMath.rollDice(new java.util.SplittableRandom(level.getRandom().nextLong()));
		a = roll.a();
		b = roll.b();
		throwSeed = level.getRandom().nextLong();
		level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.8F, 1.6F);
	}

	@Override
	protected void tick(ServerLevel level) {
		if (phase == Phase.PLAYING) {
			long t = level.getGameTime() - phaseStart;
			for (int bounce : BOUNCES) {
				if (t == bounce) {
					level.playSound(null, worldPosition, pl.pansage.gamblecraft.registry.ModSounds.DICE, SoundSource.BLOCKS, 0.8F - bounce / 100.0F, 1.0F + bounce / 120.0F);
				}
			}
		}
		super.tick(level);
	}

	public int a() {
		return a;
	}

	public int b() {
		return b;
	}

	public long throwSeed() {
		return throwSeed;
	}

	@Override
	protected long returnOf(Stake stake) {
		DiceBet bet = DiceBet.valueOf(stake.option().toUpperCase(java.util.Locale.ROOT));
		return bet.wins(a, b) ? ArcadeMath.payout(stake.amount(), bet.payout) : 0;
	}

	@Override
	public Component outcome() {
		return Component.translatable("gamblecraft.dice.rolled", a, b, a + b);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("a", a);
		output.putInt("b", b);
		output.putLong("throw", throwSeed);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		a = input.getIntOr("a", 6);
		b = input.getIntOr("b", 1);
		throwSeed = input.getLongOr("throw", 0);
	}
}
