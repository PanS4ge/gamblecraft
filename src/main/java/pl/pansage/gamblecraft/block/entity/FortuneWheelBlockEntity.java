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
import pl.pansage.gamblecraft.core.casino.ArcadeMath.WheelSymbol;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/** The wheel of fortune: bet on a symbol, the big wheel spins down past the clapper and pays the one it stops on. */
public class FortuneWheelBlockEntity extends ArcadeBlockEntity {
	public static final int SPIN_TICKS = 140;
	private static final int TURNS = 4;
	private static final List<String> OPTIONS = Arrays.stream(WheelSymbol.values()).map(WheelSymbol::key).toList();

	/** The segment under the clapper when the wheel last stopped, and the one it is heading for now. */
	private int from;
	private int segment;

	public FortuneWheelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.FORTUNE_WHEEL, pos, state);
	}

	@Override
	public List<String> options() {
		return OPTIONS;
	}

	@Override
	public double payout(String option) {
		return WheelSymbol.valueOf(option.toUpperCase(java.util.Locale.ROOT)).payout;
	}

	@Override
	protected int bettingTicks() {
		return 300;
	}

	@Override
	protected int playTicks() {
		return SPIN_TICKS;
	}

	@Override
	public String titleKey() {
		return "block.gamblecraft.fortune_wheel";
	}

	@Override
	protected void startPlay(ServerLevel level) {
		from = segment;
		segment = ArcadeMath.spinWheel(new java.util.SplittableRandom(level.getRandom().nextLong()));
	}

	public WheelSymbol symbol() {
		return ArcadeMath.WHEEL_SEGMENTS[segment];
	}

	@Override
	protected long returnOf(Stake stake) {
		return stake.option().equals(symbol().key()) ? stake.amount() * symbol().payout : 0;
	}

	@Override
	public Component outcome() {
		return Component.translatable("gamblecraft.wheel.symbol." + symbol().key());
	}

	/** Where the wheel stands, in segments, {@code t} ticks into a spin: four turns and a bit, slowing to a stop. */
	public double position(double t) {
		int n = ArcadeMath.WHEEL_SEGMENTS.length;
		double travel = TURNS * n + Math.floorMod(segment - from, n);
		double x = Math.min(1, Math.max(0, t / SPIN_TICKS));
		double eased = 1 - Math.pow(1 - x, 3);
		return from + travel * eased;
	}

	public int restingSegment() {
		return phase == Phase.PLAYING ? from : segment;
	}

	@Override
	protected void tick(ServerLevel level) {
		if (phase == Phase.PLAYING) {
			long t = level.getGameTime() - phaseStart;
			if ((int) position(t) != (int) position(t - 1)) {
				level.playSound(null, worldPosition, pl.pansage.gamblecraft.registry.ModSounds.WHEEL_CLICK, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
			}
		}
		super.tick(level);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("from", from);
		output.putInt("segment", segment);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		from = input.getIntOr("from", 0);
		segment = input.getIntOr("segment", 0);
	}
}
