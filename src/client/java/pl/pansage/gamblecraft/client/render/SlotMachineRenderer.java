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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.block.SlotMachineBlock;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.core.casino.SlotMath;
import pl.pansage.gamblecraft.core.casino.SlotTiming;

/**
 * The face of a slot machine: three reels rolling down behind the glass, the winning lines blinking, a ring of
 * chasing bulbs, the lever, and the bet, last win and jackpot. Drawn for everyone from the synced spin.
 */
public class SlotMachineRenderer implements BlockEntityRenderer<SlotMachineBlockEntity, SlotMachineRenderer.State> {
	private static final Identifier WHITE = GambleCraft.id("textures/misc/white.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	// the window, in the machine's own frame (x to the player's right, y up from the bottom block, z out of the front)
	private static final float WIN_X = 0.33F;
	private static final float WIN_Y0 = 1.13F;
	private static final float WIN_Y1 = 1.62F;
	private static final float WIN_Z = 0.405F;
	private static final float REEL_GAP = 0.025F;

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public double t;
		public boolean spinning;
		public int[] stops = new int[3];
		public boolean suspense;
		public int bet;
		public int lastWin;
		public boolean jackpotWon;
		public long jackpot;
		public double sinceDone;
		public float time;
		/** The top half has a block entity too (world generation insists), but only the bottom one draws. */
		public boolean upper;
	}

	private final Font font;

	public SlotMachineRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SlotMachineBlockEntity machine, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(machine, state, crumbling);
		state.facing = machine.getBlockState().getValue(SlotMachineBlock.FACING);
		state.upper = machine.getBlockState().getValue(SlotMachineBlock.HALF) == DoubleBlockHalf.UPPER;
		double now = machine.getLevel().getGameTime() + partialTick;
		state.t = now - machine.spinStart();
		state.spinning = machine.spinning();
		for (int i = 0; i < 3; i++) {
			state.stops[i] = machine.stop(i);
		}
		state.suspense = machine.suspense();
		state.bet = machine.bet();
		state.lastWin = machine.lastWin();
		state.jackpotWon = machine.jackpotWon();
		state.jackpot = machine.jackpot();
		state.sinceDone = state.t - SlotTiming.doneTick(state.suspense);
		state.time = (float) now;
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.upper) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		reels(state, pose, collector);
		bulbs(state, pose, collector);
		lever(state, pose, collector);
		texts(state, pose, collector);
		pose.popPose();
	}

	private void reels(State state, PoseStack pose, SubmitNodeCollector collector) {
		float reelW = (2 * WIN_X - 2 * REEL_GAP) / 3;
		float rowH = (WIN_Y1 - WIN_Y0) / 3;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> {
			quad(p, v, -WIN_X - 0.02F, WIN_Y0 - 0.02F, WIN_X + 0.02F, WIN_Y1 + 0.02F, WIN_Z - 0.003F, 0xFF101014, 255);
			for (int reel = 0; reel < 3; reel++) {
				float x0 = -WIN_X + reel * (reelW + REEL_GAP);
				quad(p, v, x0, WIN_Y0, x0 + reelW, WIN_Y1, WIN_Z - 0.002F, 0xFFF4F0E4, 255);
			}
		});
		for (int reel = 0; reel < 3; reel++) {
			float x0 = -WIN_X + reel * (reelW + REEL_GAP);
			double pos = state.spinning ? SlotTiming.position(reel, state.stops[reel], state.t, state.suspense) : state.stops[reel];
			boolean moving = state.spinning && SlotTiming.moving(reel, state.t, state.suspense) && state.t > SlotTiming.LEVER;
			double speed = moving ? Math.abs(SlotTiming.position(reel, state.stops[reel], state.t + 1, state.suspense) - pos) : 0;
			int first = (int) Math.floor(pos) - 2;
			for (int i = first; i <= first + 4; i++) {
				// the symbol at strip position i sits (i - pos) rows below the middle row
				float centre = WIN_Y0 + rowH * 1.5F - (float) (i - pos) * rowH;
				SlotMath.Symbol symbol = SlotMath.symbolAt(reel, i);
				symbol(pose, collector, symbol, x0 + reelW * 0.12F, x0 + reelW * 0.88F, centre - rowH * 0.42F, centre + rowH * 0.42F, speed);
			}
		}
		// shading at the top and bottom of the window, and the pay line
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (p, v) -> {
			quad(p, v, -WIN_X, WIN_Y1 - rowH * 0.35F, WIN_X, WIN_Y1, WIN_Z + 0.002F, 0xFF000000, 110);
			quad(p, v, -WIN_X, WIN_Y0, WIN_X, WIN_Y0 + rowH * 0.35F, WIN_Z + 0.002F, 0xFF000000, 110);
			if (!state.spinning && state.lastWin > 0 && state.sinceDone < 80 && ((int) (state.time / 4)) % 2 == 0) {
				winLines(state, p, v, reelW, rowH);
			}
		});
	}

	/** One symbol, clipped to the window; when the reel is fast, smeared up and down. */
	private void symbol(PoseStack pose, SubmitNodeCollector collector, SlotMath.Symbol symbol, float x0, float x1, float y0, float y1, double speed) {
		float top = Math.min(y1, WIN_Y1);
		float bottom = Math.max(y0, WIN_Y0);
		if (top <= bottom) {
			return;
		}
		float v0 = (y1 - top) / (y1 - y0);
		float v1 = 1 - (bottom - y0) / (y1 - y0);
		Identifier texture = GambleCraft.id("textures/gui/slots/" + symbol.key() + ".png");
		int alpha = speed > 0.3 ? 150 : 255;
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(texture), (p, v) -> {
			texQuad(p, v, x0, bottom, x1, top, WIN_Z, v0, v1, alpha);
			if (speed > 0.3) {
				float smear = (y1 - y0) * 0.25F;
				texQuad(p, v, x0, Math.max(bottom - smear, WIN_Y0), x1, Math.min(top - smear, WIN_Y1), WIN_Z + 0.0005F, v0, v1, 60);
				texQuad(p, v, x0, Math.max(bottom + smear, WIN_Y0), x1, Math.min(top + smear, WIN_Y1), WIN_Z + 0.0005F, v0, v1, 60);
			}
		});
	}

	private static void winLines(State state, PoseStack.Pose p, VertexConsumer v, float reelW, float rowH) {
		SlotMath.Spin spin = SlotMath.evaluate(state.stops);
		for (SlotMath.LineWin win : spin.wins()) {
			int[] rows = SlotMath.LINES[win.line()];
			for (int reel = 0; reel < 3; reel++) {
				float x0 = -WIN_X + reel * (reelW + REEL_GAP);
				float yc = WIN_Y1 - (rows[reel] + 0.5F) * rowH;
				float h = rowH * 0.48F;
				int gold = state.jackpotWon ? 0xFFFF4040 : 0xFFFFD040;
				quad(p, v, x0, yc + h - 0.012F, x0 + reelW, yc + h, WIN_Z + 0.003F, gold, 230);
				quad(p, v, x0, yc - h, x0 + reelW, yc - h + 0.012F, WIN_Z + 0.003F, gold, 230);
				quad(p, v, x0, yc - h, x0 + 0.012F, yc + h, WIN_Z + 0.003F, gold, 230);
				quad(p, v, x0 + reelW - 0.012F, yc - h, x0 + reelW, yc + h, WIN_Z + 0.003F, gold, 230);
			}
		}
	}

	/** A ring of bulbs round the window: chasing while it spins, all flashing after a win, a slow twinkle at rest. */
	private void bulbs(State state, PoseStack pose, SubmitNodeCollector collector) {
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> {
			int n = 0;
			boolean party = !state.spinning && state.lastWin > 0 && state.sinceDone < 80;
			for (float x = -WIN_X - 0.04F; x <= WIN_X + 0.05F; x += 0.0825F) {
				bulb(p, v, x, WIN_Y1 + 0.05F, state, n++, party);
				bulb(p, v, x, WIN_Y0 - 0.05F, state, n++, party);
			}
			for (float y = WIN_Y0; y <= WIN_Y1 + 0.01F; y += 0.098F) {
				bulb(p, v, -WIN_X - 0.05F, y, state, n++, party);
				bulb(p, v, WIN_X + 0.05F, y, state, n++, party);
			}
		});
	}

	private static void bulb(PoseStack.Pose p, VertexConsumer v, float x, float y, State state, int n, boolean party) {
		boolean on;
		if (party) {
			on = ((int) (state.time / 2)) % 2 == 0;
		} else if (state.spinning) {
			on = (n + (int) (state.time / 1.5F)) % 4 == 0;
		} else {
			on = (n * 7 + (int) (state.time / 10)) % 5 != 0;
		}
		int color = on ? (n % 3 == 0 ? 0xFFFFE070 : n % 3 == 1 ? 0xFFFF6060 : 0xFFFFFFFF) : 0xFF503828;
		float r = 0.018F;
		quad(p, v, x - r, y - r, x + r, y + r, WIN_Z + 0.03F, color, 255);
	}

	/** The lever on the right side, pulled down at the start of a spin. */
	private void lever(State state, PoseStack pose, SubmitNodeCollector collector) {
		float pull = 0;
		if (state.spinning && state.t < SlotTiming.LEVER * 3) {
			double t = state.t / (SlotTiming.LEVER * 3.0);
			pull = (float) Math.sin(t * Math.PI);
		}
		pose.pushPose();
		pose.translate(0.47F, 0.95F, 0.1F);
		pose.rotateDegrees(Axis.XP, pull * 75);
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> {
			box(p, v, 0, 0, 0, 0.045F, 0.55F, 0.045F, 0xFFB8B8C0);
			box(p, v, 0.0225F, 0.6F, 0, 0.065F, 0.065F, 0.065F, 0xFFE02020);
		});
		pose.popPose();
	}

	private void texts(State state, PoseStack pose, SubmitNodeCollector collector) {
		text(pose, collector, Component.translatable("gamblecraft.slots.jackpot_sign", state.jackpot).getVisualOrderText(), 0, 1.8F, 0.006F, 0xFFFFD040);
		text(pose, collector, Component.translatable("gamblecraft.slots.bet_sign", state.bet).getVisualOrderText(), -0.17F, 1.08F, 0.0045F, 0xFF301000);
		if (state.lastWin > 0 && !state.spinning) {
			text(pose, collector, Component.literal(String.format(Locale.ROOT, "+%d", state.lastWin)).getVisualOrderText(), 0.17F, 1.08F, 0.0045F,
					((int) (state.time / 4)) % 2 == 0 ? 0xFF60FF60 : 0xFFFFFFFF);
		}
	}

	private void text(PoseStack pose, SubmitNodeCollector collector, FormattedCharSequence text, float x, float y, float scale, int color) {
		pose.pushPose();
		pose.translate(x, y, WIN_Z + 0.04F);
		pose.scale(scale, -scale, scale);
		collector.submitText(pose, -font.width(text) / 2.0F, -4, text, false, Font.DisplayMode.POLYGON_OFFSET, FULL_BRIGHT, color, 0, 0);
		pose.popPose();
	}

	private static void quad(PoseStack.Pose p, VertexConsumer v, float x0, float y0, float x1, float y1, float z, int rgb, int alpha) {
		vertex(p, v, x0, y0, z, 0, 1, rgb, alpha);
		vertex(p, v, x1, y0, z, 1, 1, rgb, alpha);
		vertex(p, v, x1, y1, z, 1, 0, rgb, alpha);
		vertex(p, v, x0, y1, z, 0, 0, rgb, alpha);
	}

	private static void texQuad(PoseStack.Pose p, VertexConsumer v, float x0, float y0, float x1, float y1, float z, float v0, float v1, int alpha) {
		vertex(p, v, x0, y0, z, 0, v1, 0xFFFFFFFF, alpha);
		vertex(p, v, x1, y0, z, 1, v1, 0xFFFFFFFF, alpha);
		vertex(p, v, x1, y1, z, 1, v0, 0xFFFFFFFF, alpha);
		vertex(p, v, x0, y1, z, 0, v0, 0xFFFFFFFF, alpha);
	}

	/** A small box from (x, y, z), sized w x h x d, all faces lit. */
	private static void box(PoseStack.Pose p, VertexConsumer v, float x, float y, float z, float w, float h, float d, int rgb) {
		float x0 = x - w / 2;
		float x1 = x + w / 2;
		float z0 = z - d / 2;
		float z1 = z + d / 2;
		float y1 = y + h;
		// front, back, left, right, top
		face(p, v, x0, y, z1, x1, y, z1, x1, y1, z1, x0, y1, z1, rgb);
		face(p, v, x1, y, z0, x0, y, z0, x0, y1, z0, x1, y1, z0, rgb);
		face(p, v, x0, y, z0, x0, y, z1, x0, y1, z1, x0, y1, z0, rgb);
		face(p, v, x1, y, z1, x1, y, z0, x1, y1, z0, x1, y1, z1, rgb);
		face(p, v, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, rgb);
	}

	private static void face(PoseStack.Pose p, VertexConsumer v, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int rgb) {
		vertex(p, v, ax, ay, az, 0, 1, rgb, 255);
		vertex(p, v, bx, by, bz, 1, 1, rgb, 255);
		vertex(p, v, cx, cy, cz, 1, 0, rgb, 255);
		vertex(p, v, dx, dy, dz, 0, 0, rgb, 255);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer v, float x, float y, float z, float u, float uv, int rgb, int alpha) {
		v.addVertex(p, x, y, z)
				.setColor(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, alpha)
				.setUv(u, uv)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(FULL_BRIGHT)
				.setNormal(p, 0, 0, 1);
	}
}
