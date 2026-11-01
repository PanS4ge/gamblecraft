package pl.pansage.gamblecraft.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.block.entity.PlinkoBlockEntity;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.PlinkoRisk;

/**
 * The plinko board: a triangle of pegs over thirteen bins coloured by their multiplier. Each ball follows the path
 * the server drew, hopping from peg to peg (which flash as they are hit); every player's balls have their own colour.
 */
public class PlinkoRenderer implements BlockEntityRenderer<PlinkoBlockEntity, PlinkoRenderer.State> {
	private static final int ROWS = ArcadeMath.PLINKO_ROWS;
	private static final float FACE = 0.38F;
	private static final float HALF_W = 0.38F;
	private static final float DX = 2 * HALF_W / (ROWS + 1);
	private static final float TOP_ROW = 1.58F;
	private static final float BOTTOM_ROW = 0.47F;
	private static final float ROW_H = (TOP_ROW - BOTTOM_ROW) / (ROWS - 1);
	private static final float BIN_Y0 = 0.23F;
	private static final float BIN_Y1 = 0.37F;
	private static final float START_Y = 1.70F;
	private static final float PEG = 0.0075F;
	private static final float BALL = 0.019F;
	private static final int[] PALETTE = {0xFFFF4A6A, 0xFF4AC8FF, 0xFFFFD040, 0xFF7AF060, 0xFFC070FF, 0xFFFF9A3A, 0xFFF0F0F0, 0xFF40F0C8};

	/** A ball as drawn this frame. */
	public record BallView(float x, float y, int color) {
	}

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public boolean upper;
		public PlinkoRisk risk = PlinkoRisk.MEDIUM;
		public int bet;
		public float time;
		public final List<BallView> balls = new ArrayList<>();
		public final boolean[][] lit = new boolean[ROWS][ROWS + 3];
		public final float[] binGlow = new float[ROWS + 1];
	}

	private final Font font;

	public PlinkoRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	private static float rowY(int row) {
		return TOP_ROW - row * ROW_H;
	}

	/** Where the ball is {@code rights} pegs right of the left edge after {@code row} rows. */
	private static float xAt(int row, int rights) {
		return (rights - row / 2.0F) * DX;
	}

	@Override
	public void extractRenderState(PlinkoBlockEntity board, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(board, state, crumbling);
		state.facing = board.getBlockState().getValue(TallMachineBlock.FACING);
		state.upper = board.getBlockState().getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER;
		state.risk = board.risk();
		state.bet = board.bet();
		state.time = board.getLevel().getGameTime() + partialTick;
		state.balls.clear();
		for (boolean[] row : state.lit) {
			java.util.Arrays.fill(row, false);
		}
		java.util.Arrays.fill(state.binGlow, 0);
		double now = board.getLevel().getGameTime() + partialTick;
		for (PlinkoBlockEntity.Ball ball : board.balls()) {
			double t = now - ball.start();
			int color = PALETTE[Math.floorMod(ball.player().hashCode(), PALETTE.length)];
			int[] rights = new int[ROWS + 1];
			for (int r = 0; r < ROWS; r++) {
				rights[r + 1] = rights[r] + (ball.right(r) ? 1 : 0);
				double hit = (r + 1) * PlinkoBlockEntity.STEP;
				if (t >= hit && t < hit + 3) {
					state.lit[r][rights[r] + 1] = true;
				}
			}
			float rest = BALL + 0.002F;
			if (t >= PlinkoBlockEntity.LAND) {
				state.balls.add(new BallView(xAt(ROWS, rights[ROWS]), BIN_Y0 + rest, color));
				double since = t - PlinkoBlockEntity.LAND;
				if (since < 30) {
					state.binGlow[ball.bin()] = Math.max(state.binGlow[ball.bin()], (float) (1 - since / 30));
				}
				continue;
			}
			int seg = (int) Math.max(0, Math.floor(t / PlinkoBlockEntity.STEP));
			float f = (float) ((t - seg * PlinkoBlockEntity.STEP) / PlinkoBlockEntity.STEP);
			float x;
			float y;
			if (seg == 0) {
				x = 0;
				y = Mth.lerp(f * f, START_Y, rowY(0) + PEG + BALL);
			} else {
				float x0 = xAt(seg - 1, rights[seg - 1]);
				float y0 = rowY(seg - 1) + PEG + BALL;
				float x1 = xAt(seg, rights[seg]);
				float y1 = seg == ROWS ? BIN_Y0 + rest : rowY(seg) + PEG + BALL;
				x = Mth.lerp(f, x0, x1);
				// a little hop off the peg, then falling onto the next one
				y = Mth.lerp(f * f, y0, y1) + 0.03F * 4 * f * (1 - f);
			}
			state.balls.add(new BallView(x, y, color));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	public static int binColor(double multiplier) {
		if (multiplier >= 20) {
			return 0xFFE0283C;
		}
		if (multiplier >= 5) {
			return 0xFFF0502C;
		}
		if (multiplier >= 2) {
			return 0xFFF08A28;
		}
		if (multiplier >= 1) {
			return 0xFFF0C030;
		}
		return 0xFFE8E060;
	}

	private static String trim(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.upper) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		double[] bins = state.risk.bins;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			Shapes.wall(p, v, -HALF_W, BIN_Y0 - 0.02F, HALF_W, START_Y + 0.06F, FACE, 0xFF141C34, Shapes.FULL_BRIGHT);
			for (int r = 0; r < ROWS; r++) {
				for (int j = -1; j <= r + 1; j++) {
					boolean lit = state.lit[r][j + 1];
					Shapes.disc(p, v, xAt(r, j), rowY(r), FACE + 0.004F, lit ? PEG * 1.5F : PEG, lit ? 0xFFFFF4A0 : 0xFFE8ECF4, Shapes.FULL_BRIGHT);
				}
			}
			for (int k = 0; k <= ROWS; k++) {
				float cx = xAt(ROWS, k);
				float glow = state.binGlow[k];
				int color = glow > 0 ? lerpColor(binColor(bins[k]), 0xFFFFFFFF, glow) : binColor(bins[k]);
				float drop = glow * 0.015F;
				Shapes.wall(p, v, cx - DX / 2 + 0.003F, BIN_Y0 - drop, cx + DX / 2 - 0.003F, BIN_Y1 - drop, FACE + 0.003F, color, Shapes.FULL_BRIGHT);
			}
			for (BallView ball : state.balls) {
				Shapes.disc(p, v, ball.x(), ball.y(), FACE + 0.012F, BALL, ball.color(), Shapes.FULL_BRIGHT);
				Shapes.disc(p, v, ball.x() - BALL * 0.35F, ball.y() + BALL * 0.35F, FACE + 0.013F, BALL * 0.3F, 0xFFFFFFFF, Shapes.FULL_BRIGHT);
			}
		});
		// multipliers written up the bins, and the risk and bet on the tray
		for (int k = 0; k <= ROWS; k++) {
			pose.pushPose();
			pose.translate(xAt(ROWS, k), (BIN_Y0 + BIN_Y1) / 2, FACE + 0.006F);
			pose.rotateDegrees(Axis.ZP, 90);
			Shapes.label(font, pose, collector, Component.literal(trim(bins[k])), 0, 0, 0, 0.0042F, 0xFF1A1020, Shapes.FULL_BRIGHT);
			pose.popPose();
		}
		// the name on the marquee, in chasing colours
		int hue = (int) (state.time / 6) % PALETTE.length;
		Shapes.label(font, pose, collector, Component.literal("PLINKO"), 0, 1.89F, 0.502F, 0.012F, PALETTE[hue], Shapes.FULL_BRIGHT);
		Component risk = Component.translatable("gamblecraft.plinko.risk", Component.translatable("gamblecraft.plinko.risk." + state.risk.key()));
		Shapes.label(font, pose, collector, risk, -0.2F, 0.11F, 0.502F, 0.0042F, 0xFFFFE070, Shapes.FULL_BRIGHT);
		Shapes.label(font, pose, collector, Component.translatable("gamblecraft.plinko.bet", state.bet), 0.22F, 0.11F, 0.502F, 0.0042F, 0xFF70FF70,
				Shapes.FULL_BRIGHT);
		pose.popPose();
	}

	private static int lerpColor(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
