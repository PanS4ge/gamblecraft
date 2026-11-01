package pl.pansage.gamblecraft.client.render;

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
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity;
import pl.pansage.gamblecraft.core.casino.ArcadeMath;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.WheelSymbol;

/**
 * The big wheel standing over its base: fifty coloured segments with their symbols, bulbs round the rim, the
 * clapper at the top flicking as segments pass, and the croupier's call above.
 */
public class FortuneWheelRenderer implements BlockEntityRenderer<FortuneWheelBlockEntity, FortuneWheelRenderer.State> {
	private static final float RADIUS = 1.2F;
	private static final float CENTRE_Y = 2.25F;
	private static final float FACE_Z = 0.12F;
	private static final int SEGMENTS = ArcadeMath.WHEEL_SEGMENTS.length;

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public double position;
		public ArcadeBlockEntity.Phase phase = ArcadeBlockEntity.Phase.IDLE;
		public int winner = -1;
		public float time;
		public Component call = Component.empty();
	}

	private final Font font;

	public FortuneWheelRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	public static int color(WheelSymbol symbol) {
		return switch (symbol) {
			case ONE -> 0xFF3A6EC8;
			case THREE -> 0xFF2FA052;
			case SIX -> 0xFFE8A020;
			case ELEVEN -> 0xFFD03030;
			case TWENTY_THREE -> 0xFF8A3AC0;
			case JOKER -> 0xFFF0D040;
		};
	}

	public static String label(WheelSymbol symbol) {
		return switch (symbol) {
			case ONE -> "1";
			case THREE -> "3";
			case SIX -> "6";
			case ELEVEN -> "11";
			case TWENTY_THREE -> "23";
			case JOKER -> "J";
		};
	}

	@Override
	public void extractRenderState(FortuneWheelBlockEntity wheel, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(wheel, state, crumbling);
		long now = wheel.getLevel().getGameTime();
		state.facing = wheel.getBlockState().getValue(ArcadeBlock.FACING);
		state.phase = wheel.phase();
		state.time = now + partialTick;
		state.position = wheel.phase() == ArcadeBlockEntity.Phase.PLAYING ? wheel.position(now - wheel.phaseStart() + partialTick) : wheel.restingSegment();
		state.winner = wheel.phase() == ArcadeBlockEntity.Phase.RESULT ? wheel.restingSegment() : -1;
		state.call = switch (wheel.phase()) {
			case IDLE -> Component.translatable("gamblecraft.roulette.sign.idle");
			case BETTING -> Component.translatable("gamblecraft.roulette.sign.betting", wheel.secondsLeft(now));
			case PLAYING -> Component.translatable("gamblecraft.roulette.no_more_bets");
			case RESULT -> wheel.outcome();
		};
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		// the post from the stand up to the hub, and the wheel's wooden back so it is solid from behind
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			Shapes.box(p, v, 0, 0.95F, FACE_Z - 0.09F, 0.16F, CENTRE_Y - 0.95F, 0.08F, 0xFF4A2A14, state.lightCoords);
			Shapes.box(p, v, 0, CENTRE_Y - 0.08F, FACE_Z - 0.06F, 0.16F, 0.16F, 0.12F, 0xFFC09030, state.lightCoords);
		});
		pose.translate(0, CENTRE_Y, FACE_Z);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) ->
				sector(p, v, 0, -360, 0, RADIUS + 0.04F, -0.03F, 0xFF3A2010));
		float step = 360.0F / SEGMENTS;
		// the segment under the clapper is the one at `position`; turning clockwise brings the next one up
		float turn = (float) ((state.position + 0.5) * step);
		boolean blink = ((int) (state.time / 4)) % 2 == 0;
		pose.pushPose();
		pose.rotateDegrees(Axis.ZP, turn);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			for (int i = 0; i < SEGMENTS; i++) {
				WheelSymbol symbol = ArcadeMath.WHEEL_SEGMENTS[i];
				int color = i == state.winner && blink ? 0xFFFFFFFF : color(symbol);
				sector(p, v, -i * step, -(i + 1) * step, 0.18F, RADIUS - 0.05F, 0, color);
				sector(p, v, -i * step + 0.4F, -i * step - 0.4F, 0.18F, RADIUS - 0.05F, 0.001F, 0xFF201810);
			}
			sector(p, v, 0, -360, 0, 0.18F, 0.002F, 0xFFE0C050);
			sector(p, v, 0, -360, RADIUS - 0.05F, RADIUS + 0.04F, 0.001F, 0xFF5A3018);
		});
		for (int i = 0; i < SEGMENTS; i++) {
			pose.pushPose();
			pose.rotateDegrees(Axis.ZP, -(i + 0.5F) * step);
			Shapes.label(font, pose, collector, Component.literal(label(ArcadeMath.WHEEL_SEGMENTS[i])), 0, RADIUS - 0.2F, 0.004F, 0.011F, 0xFFFFFFFF,
					Shapes.FULL_BRIGHT);
			pose.popPose();
		}
		pose.popPose();
		// bulbs round the rim and the clapper, which kicks as each segment passes
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			for (int i = 0; i < 24; i++) {
				double a = Math.toRadians(i * 15);
				float bx = (float) Math.sin(a) * (RADIUS + 0.08F);
				float by = (float) Math.cos(a) * (RADIUS + 0.08F);
				boolean on = state.phase == ArcadeBlockEntity.Phase.PLAYING ? (i + (int) (state.time / 2)) % 3 == 0 : (i + (int) (state.time / 8)) % 2 == 0;
				Shapes.wall(p, v, bx - 0.03F, by - 0.03F, bx + 0.03F, by + 0.03F, 0.01F, on ? 0xFFFFF0A0 : 0xFF5A4020, Shapes.FULL_BRIGHT);
			}
		});
		float kick = state.phase == ArcadeBlockEntity.Phase.PLAYING ? (float) (1 - (state.position - Math.floor(state.position))) * 25 : 0;
		pose.pushPose();
		pose.translate(0, RADIUS + 0.12F, 0.02F);
		pose.rotateDegrees(Axis.ZP, kick);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			Shapes.vertex(p, v, -0.09F, 0.06F, 0, 0, 1, 0xFFE02020, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, 0, -0.16F, 0, 1, 1, 0xFFE02020, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, 0, -0.16F, 0, 1, 0, 0xFFE02020, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, 0.09F, 0.06F, 0, 0, 0, 0xFFE02020, Shapes.FULL_BRIGHT, 0, 0, 1);
		});
		pose.popPose();
		pose.popPose();
		Shapes.billboard(font, pose, collector, camera, state.call, 0.5, CENTRE_Y + RADIUS + 0.55, 0.5, 0.02F, 0xFFFFE070);
	}

	/** A ring sector in the x-y plane, angles in degrees clockwise from +y (negative = clockwise). */
	private static void sector(PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer v, float a0, float a1, float r0, float r1, float z, int color) {
		int steps = Math.max(1, (int) (Math.abs(a1 - a0) / 6));
		for (int s = 0; s < steps; s++) {
			double t0 = Math.toRadians(a0 + (a1 - a0) * s / steps);
			double t1 = Math.toRadians(a0 + (a1 - a0) * (s + 1) / steps);
			Shapes.vertex(p, v, (float) -Math.sin(t0) * r0, (float) Math.cos(t0) * r0, z, 0, 0, color, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, (float) -Math.sin(t0) * r1, (float) Math.cos(t0) * r1, z, 0, 1, color, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, (float) -Math.sin(t1) * r1, (float) Math.cos(t1) * r1, z, 1, 1, color, Shapes.FULL_BRIGHT, 0, 0, 1);
			Shapes.vertex(p, v, (float) -Math.sin(t1) * r0, (float) Math.cos(t1) * r0, z, 1, 0, color, Shapes.FULL_BRIGHT, 0, 0, 1);
		}
	}
}
