package pl.pansage.gamblecraft.client.render;

import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;

/**
 * The crash launch pad: a little rocket on the pad while bets are taken, lifting off on a flame as the multiplier
 * climbs above it, and gone in a bang where it crashed. Everyone near sees the same flight from the synced start.
 */
public class CrashPadRenderer implements BlockEntityRenderer<CrashPadBlockEntity, CrashPadRenderer.State> {
	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public ArcadeBlockEntity.Phase phase = ArcadeBlockEntity.Phase.IDLE;
		public double multiplier = 1;
		public double y = CrashPadBlockEntity.PAD_TOP;
		public boolean pinned;
		public float time;
		public double sincePhase;
		public long secondsLeft;
		public double crashedAt;
	}

	private final Font font;

	public CrashPadRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CrashPadBlockEntity pad, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(pad, state, crumbling);
		long now = pad.getLevel().getGameTime();
		state.facing = pad.getBlockState().getValue(ArcadeBlock.FACING);
		state.phase = pad.phase();
		state.time = now + partialTick;
		state.sincePhase = now - pad.phaseStart() + partialTick;
		state.secondsLeft = pad.secondsLeft(now);
		state.crashedAt = pad.crashedAt();
		state.multiplier = state.phase == ArcadeBlockEntity.Phase.PLAYING ? CrashPadBlockEntity.multiplier(state.sincePhase) : 1;
		double room = state.phase == ArcadeBlockEntity.Phase.IDLE || state.phase == ArcadeBlockEntity.Phase.BETTING ? 0
				: CrashPadBlockEntity.flightRoom(pad.getLevel(), pad.getBlockPos());
		double height = state.phase == ArcadeBlockEntity.Phase.RESULT ? CrashPadBlockEntity.rocketHeight(state.crashedAt)
				: CrashPadBlockEntity.rocketHeight(state.multiplier);
		state.pinned = height > room;
		state.y = CrashPadBlockEntity.PAD_TOP + Math.min(height, room);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean flying = state.phase == ArcadeBlockEntity.Phase.PLAYING;
		if (state.phase != ArcadeBlockEntity.Phase.RESULT) {
			pose.pushPose();
			pose.translate(0.5, state.y, 0.5);
			pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
			if (flying) {
				// a wobble that grows with speed, and a rattle when it is pressed against the ceiling
				float wobble = (float) Math.min(6, state.multiplier) * (state.pinned ? 1.6F : 0.8F);
				pose.rotateDegrees(Axis.ZP, Mth.sin(state.time * 1.7F) * wobble);
				pose.rotateDegrees(Axis.XP, Mth.cos(state.time * 1.3F) * wobble * 0.6F);
			}
			int light = flying ? Shapes.FULL_BRIGHT : state.lightCoords;
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> rocket(p, v, light));
			if (flying) {
				float flicker = 0.18F + 0.08F * Mth.sin(state.time * 2.3F) + 0.05F * Mth.sin(state.time * 5.1F);
				collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(Shapes.WHITE), (p, v) -> {
					Shapes.box(p, v, 0, -0.04F - flicker, 0, 0.1F, flicker, 0.1F, 0xC0FF7A18, Shapes.FULL_BRIGHT);
					Shapes.box(p, v, 0, -0.04F - flicker * 0.6F, 0, 0.06F, flicker * 0.6F, 0.06F, 0xE0FFE060, Shapes.FULL_BRIGHT);
				});
			}
			pose.popPose();
		}
		Component call;
		int color;
		double y;
		float scale;
		switch (state.phase) {
			case IDLE -> {
				call = Component.translatable("gamblecraft.roulette.sign.idle");
				color = 0xFFFFE070;
				y = 1.6;
				scale = 0.016F;
			}
			case BETTING -> {
				call = Component.translatable("gamblecraft.crash.liftoff", state.secondsLeft);
				color = 0xFFFFE070;
				y = 1.6;
				scale = 0.016F;
			}
			case PLAYING -> {
				call = Component.literal(String.format(Locale.ROOT, "x%.2f", state.multiplier));
				color = state.multiplier >= 2 ? 0xFF60FF60 : 0xFFFFFFFF;
				y = state.y + 1.15;
				scale = 0.03F;
			}
			default -> {
				call = Component.translatable("gamblecraft.crash.crashed", String.format(Locale.ROOT, "%.2f", state.crashedAt));
				color = 0xFFFF5040;
				y = state.y + 0.6;
				scale = 0.026F;
			}
		}
		Shapes.billboard(font, pose, collector, camera, call, 0.5, y, 0.5, scale, color);
	}

	/** A small rocket standing on its base at the origin: fins, a white body with a red band and window, a red nose. */
	private static void rocket(PoseStack.Pose p, VertexConsumer v, int light) {
		int white = 0xFFE8E8EE;
		int red = 0xFFD8282C;
		Shapes.box(p, v, 0, -0.04F, 0, 0.12F, 0.06F, 0.12F, 0xFF3A3A40, light);
		Shapes.box(p, v, 0.12F, 0, 0, 0.05F, 0.18F, 0.02F, red, light);
		Shapes.box(p, v, -0.12F, 0, 0, 0.05F, 0.18F, 0.02F, red, light);
		Shapes.box(p, v, 0, 0, 0.12F, 0.02F, 0.18F, 0.05F, red, light);
		Shapes.box(p, v, 0, 0, -0.12F, 0.02F, 0.18F, 0.05F, red, light);
		Shapes.box(p, v, 0, 0.02F, 0, 0.2F, 0.52F, 0.2F, white, light);
		Shapes.box(p, v, 0, 0.38F, 0, 0.206F, 0.05F, 0.206F, red, light);
		Shapes.box(p, v, 0, 0.22F, 0.1F, 0.08F, 0.08F, 0.012F, 0xFF40A8E8, light);
		Shapes.box(p, v, 0, 0.54F, 0, 0.15F, 0.08F, 0.15F, red, light);
		Shapes.box(p, v, 0, 0.62F, 0, 0.09F, 0.07F, 0.09F, red, light);
		Shapes.box(p, v, 0, 0.69F, 0, 0.04F, 0.05F, 0.04F, red, light);
	}
}
