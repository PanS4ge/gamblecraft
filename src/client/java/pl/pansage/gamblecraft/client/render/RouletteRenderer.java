package pl.pansage.gamblecraft.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.block.RouletteFeltBlock;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.core.casino.RouletteLayout;
import pl.pansage.gamblecraft.game.ChipCurrency;

/**
 * A roulette table: the wheel turning in its bowl and the ball racing the other way until it drops into the
 * number the server drew; the felt unrolling when the table is put together, chips on the bets, the winning
 * number marked, and the croupier's call floating over the wheel.
 */
public class RouletteRenderer implements BlockEntityRenderer<RouletteBlockEntity, RouletteRenderer.State> {
	private static final Identifier WHITE = GambleCraft.id("textures/misc/white.png");
	private static final float WHEEL_Y = 14.2F / 16;
	private static final float POCKET_IN = 0.25F;
	private static final float POCKET_OUT = 0.37F;
	private static final float RIM = 0.43F;
	private static final float BOARD_Y = (float) RouletteFeltBlock.TOP + 0.002F;
	private static final int UNROLL_TICKS = 30;
	private static final int LAND = 140;
	private static final float BALL_TURNS = 1300;

	public record Chips(double u, double v, ItemStackRenderState item, int pile) {
	}

	public static class State extends BlockEntityRenderState {
		public boolean assembled;
		public Direction facing = Direction.NORTH;
		public RouletteBlockEntity.Phase phase = RouletteBlockEntity.Phase.IDLE;
		public double sincePhase;
		public double sinceAssembled;
		public int result = -1;
		public float time;
		public long secondsLeft;
		public List<Integer> history = new ArrayList<>();
		public final List<Chips> chips = new ArrayList<>();
	}

	private final Font font;
	private final ItemModelResolver items;

	public RouletteRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
		this.items = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RouletteBlockEntity table, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(table, state, crumbling);
		double now = table.getLevel().getGameTime() + partialTick;
		state.assembled = table.getBlockState().getValue(RouletteWheelBlock.ASSEMBLED);
		state.facing = table.getBlockState().getValue(RouletteWheelBlock.FACING);
		state.phase = table.phase();
		state.sincePhase = now - table.phaseStart();
		state.sinceAssembled = now - table.assembledAt();
		state.result = table.result();
		state.time = (float) now;
		state.secondsLeft = (table.ticksLeft(table.getLevel().getGameTime()) + 19) / 20;
		state.history = List.copyOf(table.history());
		state.chips.clear();
		int seed = 0;
		for (RouletteBlockEntity.PlacedBet placed : table.bets()) {
			double[] at = RouletteLayout.anchor(Roulette.Bet.fromId(placed.bet()));
			ItemStackRenderState item = new ItemStackRenderState();
			items.updateForTopItem(item, ClientCurrency.stack(ChipCurrency.ID, placed.amount()), ItemDisplayContext.GROUND, table.getLevel(), null, seed++);
			int pile = Math.min(6, 1 + (int) (Math.log(placed.amount()) / Math.log(4)));
			// several players on one bet: nudge the later stacks so they do not overlap
			double nudge = (seed % 3) * 0.035;
			state.chips.add(new Chips(at[0] + nudge, at[1] - nudge, item, pile));
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 48;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		wheel(state, pose, collector);
		pose.popPose();
		if (state.assembled) {
			board(state, pose, collector);
			call(state, pose, collector, camera);
		}
	}

	// ---------------------------------------------------------------- the wheel and the ball

	private static float wheelAngle(State state) {
		if (state.phase == RouletteBlockEntity.Phase.SPINNING || state.phase == RouletteBlockEntity.Phase.RESULT) {
			double spun = state.phase == RouletteBlockEntity.Phase.SPINNING ? state.sincePhase : RouletteBlockEntity.SPIN_TICKS + state.sincePhase;
			// fast at the start of the spin, easing off as the ball settles
			return (float) (state.time * 0.6 + 3.2 * spun - 0.006 * Math.min(spun, 250) * Math.min(spun, 250));
		}
		return state.time * 0.6F;
	}

	private void wheel(State state, PoseStack pose, SubmitNodeCollector collector) {
		float angle = wheelAngle(state);
		boolean blink = state.phase == RouletteBlockEntity.Phase.RESULT && ((int) (state.time / 4)) % 2 == 0;
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, angle);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> {
			float step = 360.0F / Roulette.WHEEL.length;
			for (int i = 0; i < Roulette.WHEEL.length; i++) {
				int n = Roulette.WHEEL[i];
				int color = blink && n == state.result ? 0xFFFFF4C0 : RouletteLayout.colorOf(n);
				sector(p, v, i * step, (i + 1) * step, POCKET_IN, POCKET_OUT, WHEEL_Y, color, state.lightCoords);
				sector(p, v, i * step - 0.6F, i * step + 0.6F, POCKET_IN, POCKET_OUT + 0.01F, WHEEL_Y + 0.003F, 0xFFD8B040, state.lightCoords);
			}
			sector(p, v, 0, 360, 0.0F, POCKET_IN, WHEEL_Y + 0.004F, 0xFF6A3E1E, state.lightCoords);
			sector(p, v, 0, 360, 0.0F, 0.06F, WHEEL_Y + 0.03F, 0xFFE0C050, state.lightCoords);
			for (int k = 0; k < 4; k++) {
				sector(p, v, k * 90 - 2, k * 90 + 2, 0.06F, 0.2F, WHEEL_Y + 0.02F, 0xFFE0C050, state.lightCoords);
			}
		});
		pose.popPose();
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> sector(p, v, 0, 360, POCKET_OUT + 0.01F, RIM, WHEEL_Y - 0.004F,
				0xFF4A2A14, state.lightCoords));
		ball(state, pose, collector, angle);
	}

	private void ball(State state, PoseStack pose, SubmitNodeCollector collector, float wheelAngle) {
		if (state.phase != RouletteBlockEntity.Phase.SPINNING && state.phase != RouletteBlockEntity.Phase.RESULT) {
			return;
		}
		double t = state.phase == RouletteBlockEntity.Phase.SPINNING ? state.sincePhase : RouletteBlockEntity.SPIN_TICKS + state.sincePhase;
		float pocket = (Roulette.wheelIndex(Math.max(0, state.result)) + 0.5F) * 360.0F / Roulette.WHEEL.length;
		double radius;
		double angle;
		double lift = 0.03;
		if (t < LAND) {
			// against the wheel, slowing down, ending exactly over its pocket
			double left = LAND - t;
			double landWheel = wheelAngle(withSince(state, LAND));
			angle = landWheel + pocket + BALL_TURNS * left * left / ((double) LAND * LAND);
			double drop = Mth.clamp((t - LAND * 0.7) / (LAND * 0.3), 0, 1);
			radius = Mth.lerp(drop, RIM - 0.03, (POCKET_IN + POCKET_OUT) / 2);
			lift += drop > 0 && drop < 1 ? Math.abs(Math.sin(drop * Math.PI * 4)) * 0.04 * (1 - drop) : 0;
		} else {
			angle = wheelAngle + pocket;
			radius = (POCKET_IN + POCKET_OUT) / 2;
		}
		double rad = Math.toRadians(angle);
		float bx = (float) (Math.sin(rad) * radius);
		float bz = (float) (Math.cos(rad) * radius);
		pose.pushPose();
		pose.translate(bx, WHEEL_Y + lift, bz);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> cube(p, v, 0.022F, 0xFFF8F8F8, state.lightCoords));
		pose.popPose();
	}

	private static State withSince(State state, double t) {
		State copy = new State();
		copy.phase = RouletteBlockEntity.Phase.SPINNING;
		copy.sincePhase = t;
		copy.time = (float) (state.time - (state.phase == RouletteBlockEntity.Phase.SPINNING ? state.sincePhase : RouletteBlockEntity.SPIN_TICKS + state.sincePhase) + t);
		return copy;
	}

	// ---------------------------------------------------------------- the felt

	/** World offset (from the wheel block's corner) of a board point. */
	private static Vec3 boardToLocal(State state, double u, double v) {
		Direction d = state.facing;
		Direction side = d.getClockWise();
		double x = 0.5 + d.getStepX() * (0.5 + u) + side.getStepX() * (v - 0.5);
		double z = 0.5 + d.getStepZ() * (0.5 + u) + side.getStepZ() * (v - 0.5);
		return new Vec3(x, BOARD_Y, z);
	}

	private void board(State state, PoseStack pose, SubmitNodeCollector collector) {
		double reach = RouletteLayout.LENGTH * Mth.clamp(state.sinceAssembled / UNROLL_TICKS, 0, 1);
		List<RouletteLayout.Cell> cells = RouletteLayout.cells();
		boolean showResult = state.phase == RouletteBlockEntity.Phase.RESULT && state.result >= 0;
		float pulse = 0.5F + 0.5F * Mth.sin(state.time * 0.5F);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> {
			boardQuad(state, p, v, 0, 0, Math.min(reach, RouletteLayout.LENGTH), 1, 0, RouletteLayout.FELT);
			for (RouletteLayout.Cell cell : cells) {
				if (cell.u0() >= reach) {
					continue;
				}
				double u1 = Math.min(cell.u1(), reach);
				boardQuad(state, p, v, cell.u0(), cell.v0(), u1, cell.v1(), 0.0006F, 0xFFD8C070);
				int color = cell.color();
				if (showResult && cell.bet().key().equals("straight") && cell.bet().wins(state.result)) {
					color = blend(color, 0xFFFFF4C0, pulse);
				}
				boardQuad(state, p, v, cell.u0() + 0.008, cell.v0() + 0.008, u1 - 0.008, cell.v1() - 0.008, 0.0012F, color);
			}
		});
		if (reach >= RouletteLayout.LENGTH) {
			for (RouletteLayout.Cell cell : cells) {
				label(state, pose, collector, cell);
			}
		}
		for (Chips chips : state.chips) {
			Vec3 at = boardToLocal(state, chips.u(), chips.v());
			for (int i = 0; i < chips.pile(); i++) {
				pose.pushPose();
				pose.translate(at.x, at.y + 0.004 + i * 0.012, at.z);
				pose.scale(0.42F, 0.42F, 0.42F);
				chips.item().submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
				pose.popPose();
			}
		}
		if (showResult) {
			double[] at = RouletteLayout.anchor(Roulette.straight(state.result));
			Vec3 dolly = boardToLocal(state, at[0], at[1]);
			pose.pushPose();
			pose.translate(dolly.x, dolly.y + 0.03, dolly.z);
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> cube(p, v, 0.035F, 0xFFF0F0F0, state.lightCoords));
			pose.popPose();
		}
	}

	private static int blend(int a, int b, float t) {
		int r = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	private static void boardQuad(State state, PoseStack.Pose p, VertexConsumer v, double u0, double v0, double u1, double v1, float lift, int color) {
		Vec3 a = boardToLocal(state, u0, v0);
		Vec3 b = boardToLocal(state, u0, v1);
		Vec3 c = boardToLocal(state, u1, v1);
		Vec3 d = boardToLocal(state, u1, v0);
		// wind counter-clockwise seen from above whichever way the table faces
		Direction dir = state.facing;
		Direction side = dir.getClockWise();
		boolean flip = dir.getStepX() * side.getStepZ() - dir.getStepZ() * side.getStepX() < 0;
		Vec3[] corners = flip ? new Vec3[] {a, d, c, b} : new Vec3[] {a, b, c, d};
		for (Vec3 corner : corners) {
			v.addVertex(p, (float) corner.x, (float) corner.y + lift, (float) corner.z)
					.setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 255)
					.setUv(0, 0)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(state.lightCoords)
					.setNormal(p, 0, 1, 0);
		}
	}

	/** A cell's label lying on the felt, upright for the players standing along it. */
	private void label(State state, PoseStack pose, SubmitNodeCollector collector, RouletteLayout.Cell cell) {
		Vec3 at = boardToLocal(state, (cell.u0() + cell.u1()) / 2, (cell.v0() + cell.v1()) / 2);
		Direction d = state.facing;
		pose.pushPose();
		pose.translate(at.x, at.y + 0.002, at.z);
		// text runs along the table (u) and reads from the players' side (its bottom towards +v), lying face up
		pose.rotateDegrees(Axis.YP, (float) Math.toDegrees(Math.atan2(-d.getStepZ(), d.getStepX())));
		pose.rotateDegrees(Axis.XP, -90);
		float scale = cell.label().length() > 3 ? 0.0055F : 0.008F;
		pose.scale(scale, -scale, scale);
		var text = Component.literal(cell.label()).getVisualOrderText();
		collector.submitText(pose, -font.width(text) / 2.0F, -4, text, false, Font.DisplayMode.POLYGON_OFFSET, state.lightCoords, 0xFFFFFFFF, 0, 0);
		pose.popPose();
	}

	/** The croupier's call and the last numbers, floating over the wheel and turned to the viewer. */
	private void call(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		MutableComponent line = switch (state.phase) {
			case IDLE -> Component.translatable("gamblecraft.roulette.sign.idle");
			case BETTING -> Component.translatable("gamblecraft.roulette.sign.betting", state.secondsLeft);
			case SPINNING -> Component.translatable("gamblecraft.roulette.no_more_bets");
			case RESULT -> Component.translatable(state.result == 0 ? "gamblecraft.roulette.green"
					: Roulette.isRed(state.result) ? "gamblecraft.roulette.red" : "gamblecraft.roulette.black", state.result);
		};
		MutableComponent history = Component.empty();
		for (int n : state.history) {
			int color = n == 0 ? 0x40D060 : Roulette.isRed(n) ? 0xFF5050 : 0xE0E0E0;
			history.append(Component.literal(n + " ").withColor(color));
		}
		pose.pushPose();
		pose.translate(0.5, 1.55, 0.5);
		pose.rotate(camera.orientation);
		pose.scale(0.02F, -0.02F, 0.02F);
		var text = line.getVisualOrderText();
		collector.submitText(pose, -font.width(text) / 2.0F, 0, text, false, Font.DisplayMode.NORMAL, 0xF000F0, 0xFFFFE070, 0x90000000, 0);
		if (!state.history.isEmpty()) {
			var hist = history.getVisualOrderText();
			pose.scale(0.7F, 0.7F, 0.7F);
			collector.submitText(pose, -font.width(hist) / 2.0F, 16, hist, false, Font.DisplayMode.NORMAL, 0xF000F0, 0xFFFFFFFF, 0x90000000, 0);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- shapes

	/** A flat ring sector on the wheel, angles in degrees from +z towards +x. */
	private static void sector(PoseStack.Pose p, VertexConsumer v, float a0, float a1, float r0, float r1, float y, int color, int light) {
		int steps = Math.max(1, (int) ((a1 - a0) / 10));
		for (int s = 0; s < steps; s++) {
			double t0 = Math.toRadians(a0 + (a1 - a0) * s / steps);
			double t1 = Math.toRadians(a0 + (a1 - a0) * (s + 1) / steps);
			corner(p, v, Math.sin(t0) * r0, y, Math.cos(t0) * r0, color, light);
			corner(p, v, Math.sin(t0) * r1, y, Math.cos(t0) * r1, color, light);
			corner(p, v, Math.sin(t1) * r1, y, Math.cos(t1) * r1, color, light);
			corner(p, v, Math.sin(t1) * r0, y, Math.cos(t1) * r0, color, light);
		}
	}

	private static void corner(PoseStack.Pose p, VertexConsumer v, double x, float y, double z, int color, int light) {
		v.addVertex(p, (float) x, y, (float) z)
				.setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, 255)
				.setUv(0, 0)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, 0, 1, 0);
	}

	private static void cube(PoseStack.Pose p, VertexConsumer v, float r, int color, int light) {
		float[][] faces = {
				{-r, r, -r, r, r, -r, r, r, r, -r, r, r},
				{-r, -r, r, r, -r, r, r, -r, -r, -r, -r, -r},
				{-r, -r, r, -r, r, r, r, r, r, r, -r, r},
				{r, -r, -r, r, r, -r, -r, r, -r, -r, -r, -r},
				{r, -r, r, r, r, r, r, r, -r, r, -r, -r},
				{-r, -r, -r, -r, r, -r, -r, r, r, -r, -r, r},
		};
		for (float[] f : faces) {
			for (int i = 0; i < 4; i++) {
				corner(p, v, f[i * 3], f[i * 3 + 1] + r, f[i * 3 + 2], color, light);
			}
		}
	}
}
