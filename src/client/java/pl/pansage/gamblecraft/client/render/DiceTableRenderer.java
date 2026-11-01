package pl.pansage.gamblecraft.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.entity.ArcadeBlockEntity;
import pl.pansage.gamblecraft.block.entity.DiceTableBlockEntity;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.core.casino.ArcadeMath.DiceBet;
import pl.pansage.gamblecraft.game.ChipCurrency;

/**
 * The dice table: four betting zones with everyone's chips on the near half, and two dice thrown across the far half.
 * The throw bounces where the synced seed says and the dice settle on the numbers the server rolled.
 */
public class DiceTableRenderer implements BlockEntityRenderer<DiceTableBlockEntity, DiceTableRenderer.State> {
	private static final float TOP = 14 / 16.0F;
	private static final float SIZE = 0.1F;
	private static final float SETTLE = DiceTableBlockEntity.BOUNCES[DiceTableBlockEntity.BOUNCES.length - 1];
	/** Zones in the table's frame: x0, z0, x1, z1 (z towards the player). */
	private static final float[][] ZONES = {
			{-0.44F, 0.24F, -0.01F, 0.45F},
			{-0.44F, 0.01F, -0.01F, 0.22F},
			{0.01F, 0.24F, 0.44F, 0.45F},
			{0.01F, 0.01F, 0.44F, 0.22F},
	};
	private static final int[][] PIPS = {
			{4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8},
	};

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public ArcadeBlockEntity.Phase phase = ArcadeBlockEntity.Phase.IDLE;
		public double t;
		public float time;
		public int a = 6;
		public int b = 1;
		public long seed;
		public Component call = Component.empty();
		public final long[] totals = new long[4];
		public final List<ItemStackRenderState> chips = new ArrayList<>();
	}

	private final Font font;
	private final ItemModelResolver items;

	public DiceTableRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
		this.items = context.itemModelResolver();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DiceTableBlockEntity table, State state, float partialTick, Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(table, state, crumbling);
		long now = table.getLevel().getGameTime();
		state.facing = table.getBlockState().getValue(ArcadeBlock.FACING);
		state.phase = table.phase();
		state.t = state.phase == ArcadeBlockEntity.Phase.PLAYING ? now - table.phaseStart() + partialTick : DiceTableBlockEntity.ROLL_TICKS;
		state.time = now + partialTick;
		state.a = table.a();
		state.b = table.b();
		state.seed = table.throwSeed();
		state.call = switch (table.phase()) {
			case IDLE -> Component.translatable("gamblecraft.roulette.sign.idle");
			case BETTING -> Component.translatable("gamblecraft.roulette.sign.betting", table.secondsLeft(now));
			case PLAYING -> Component.translatable("gamblecraft.roulette.no_more_bets");
			case RESULT -> table.outcome();
		};
		state.chips.clear();
		DiceBet[] bets = DiceBet.values();
		for (int i = 0; i < bets.length; i++) {
			state.totals[i] = table.totalOn(bets[i].key());
			ItemStackRenderState item = new ItemStackRenderState();
			if (state.totals[i] > 0) {
				items.updateForTopItem(item, ClientCurrency.stack(ChipCurrency.ID, state.totals[i]), ItemDisplayContext.GROUND, table.getLevel(), null, i);
			}
			state.chips.add(item);
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		zones(state, pose, collector);
		boolean rolling = state.phase == ArcadeBlockEntity.Phase.PLAYING;
		die(state, pose, collector, 0, state.a, rolling);
		die(state, pose, collector, 1, state.b, rolling);
		pose.popPose();
		Shapes.billboard(font, pose, collector, camera, state.call, 0.5, TOP + 0.7, 0.5, 0.016F, 0xFFFFE070);
	}

	private void zones(State state, PoseStack pose, SubmitNodeCollector collector) {
		DiceBet[] bets = DiceBet.values();
		boolean blink = state.phase == ArcadeBlockEntity.Phase.RESULT && ((int) (state.time / 4)) % 2 == 0;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			for (int i = 0; i < ZONES.length; i++) {
				float[] z = ZONES[i];
				boolean won = blink && bets[i].wins(state.a, state.b);
				Shapes.floor(p, v, z[0], z[1], z[2], z[3], TOP + 0.001F, 0xFFD8B040, state.lightCoords);
				Shapes.floor(p, v, z[0] + 0.012F, z[1] + 0.012F, z[2] - 0.012F, z[3] - 0.012F, TOP + 0.002F, won ? 0xFF7AD878 : 0xFF1E7A3E,
						won ? Shapes.FULL_BRIGHT : state.lightCoords);
			}
			// the line across the middle: dice are thrown beyond it
			Shapes.floor(p, v, -0.46F, -0.008F, 0.46F, 0.004F, TOP + 0.001F, 0xFFF0F0E8, state.lightCoords);
		});
		for (int i = 0; i < ZONES.length; i++) {
			float[] z = ZONES[i];
			float cx = (z[0] + z[2]) / 2;
			float cz = (z[1] + z[3]) / 2;
			pose.pushPose();
			pose.translate(cx - 0.05F, TOP + 0.004F, cz);
			pose.rotateDegrees(Axis.XP, -90);
			Shapes.label(font, pose, collector, Component.translatable("gamblecraft.dice.zone." + bets[i].key()), 0, 0.03F, 0, 0.0062F, 0xFFFFFFFF,
					state.lightCoords);
			Shapes.label(font, pose, collector, Component.literal("x" + trim(bets[i].payout)), 0, -0.035F, 0, 0.005F, 0xFFFFE070, state.lightCoords);
			pose.popPose();
			if (state.totals[i] > 0) {
				int pile = Math.min(6, 1 + (int) (Math.log(state.totals[i]) / Math.log(4)));
				for (int c = 0; c < pile; c++) {
					pose.pushPose();
					pose.translate(z[2] - 0.07F, TOP + 0.005F + c * 0.012F, cz);
					pose.scale(0.4F, 0.4F, 0.4F);
					state.chips.get(i).submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
					pose.popPose();
				}
			}
		}
	}

	private static String trim(double value) {
		return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
	}

	/** One die: thrown from the far edge, hopping at the bounce ticks and tumbling slower each time, then at rest. */
	private void die(State state, PoseStack pose, SubmitNodeCollector collector, int index, int value, boolean rolling) {
		Random rng = new Random(state.seed ^ (index + 1) * 0x9E3779B97F4A7C15L);
		float restX = (index == 0 ? -0.15F : 0.13F) + (rng.nextFloat() - 0.5F) * 0.12F;
		float restZ = -0.22F + (rng.nextFloat() - 0.5F) * 0.18F;
		float yaw = rng.nextFloat() * 360;
		float ax = rng.nextFloat() - 0.5F;
		float ay = rng.nextFloat() - 0.5F;
		float az = rng.nextFloat() - 0.5F;
		float len = Math.max(0.01F, Mth.sqrt(ax * ax + ay * ay + az * az));
		float spin = (float) Math.toRadians(720 + rng.nextInt(360));
		int[] bounces = DiceTableBlockEntity.BOUNCES;
		float[] bx = new float[bounces.length + 1];
		float[] bz = new float[bounces.length + 1];
		bx[0] = index == 0 ? -0.06F : 0.06F;
		bz[0] = -0.47F;
		for (int i = 1; i <= bounces.length; i++) {
			float f = 1 - (float) Math.pow(0.55, i);
			float jitter = 0.16F * (1 - f);
			bx[i] = Mth.lerp(f, bx[0], restX) + (rng.nextFloat() - 0.5F) * jitter;
			bz[i] = Mth.lerp(f, bz[0], restZ) + (rng.nextFloat() - 0.5F) * jitter;
		}
		float x = restX;
		float z = restZ;
		float lift = 0;
		float tumble = 0;
		double t = state.t;
		if (rolling && t < DiceTableBlockEntity.ROLL_TICKS) {
			if (t < SETTLE) {
				int seg = 0;
				while (seg < bounces.length && t >= bounces[seg]) {
					seg++;
				}
				float start = seg == 0 ? 0 : bounces[seg - 1];
				float f = (float) ((t - start) / (bounces[seg] - start));
				x = Mth.lerp(f, bx[seg], bx[seg + 1]);
				z = Mth.lerp(f, bz[seg], bz[seg + 1]);
				// the first leg falls from the croupier's hand, the others are hops getting smaller
				lift = seg == 0 ? 0.55F * (1 - f * f) : 0.2F * (float) Math.pow(0.5, seg - 1) * 4 * f * (1 - f);
				float s = (float) (t / SETTLE);
				tumble = spin * (1 - s) * (1 - s);
			} else {
				float f = (float) ((t - SETTLE) / (DiceTableBlockEntity.ROLL_TICKS - SETTLE));
				float eased = 1 - (1 - f) * (1 - f);
				x = Mth.lerp(eased, bx[bounces.length], restX);
				z = Mth.lerp(eased, bz[bounces.length], restZ);
			}
		}
		pose.pushPose();
		pose.translate(x, TOP + SIZE / 2 + lift, z);
		pose.rotateDegrees(Axis.YP, yaw);
		switch (value) {
			case 6 -> pose.rotateDegrees(Axis.XP, 180);
			case 2 -> pose.rotateDegrees(Axis.XP, -90);
			case 5 -> pose.rotateDegrees(Axis.XP, 90);
			case 3 -> pose.rotateDegrees(Axis.ZP, 90);
			case 4 -> pose.rotateDegrees(Axis.ZP, -90);
			default -> {
			}
		}
		if (tumble != 0) {
			pose.rotate(new Quaternionf().rotateAxis(tumble, ax / len, ay / len, az / len));
		}
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			float h = SIZE / 2;
			Shapes.box(p, v, 0, -h, 0, SIZE, SIZE, SIZE, 0xFFF6F2EA, state.lightCoords);
			// faces: 1 up, 6 down, 2 front, 5 back, 3 right, 4 left (opposites add up to seven)
			face(p, v, 1, 0, h, 0, 1, 0, 0, 0, 0, 1, state.lightCoords);
			face(p, v, 6, 0, -h, 0, 1, 0, 0, 0, 0, -1, state.lightCoords);
			face(p, v, 2, 0, 0, h, 1, 0, 0, 0, 1, 0, state.lightCoords);
			face(p, v, 5, 0, 0, -h, -1, 0, 0, 0, 1, 0, state.lightCoords);
			face(p, v, 3, h, 0, 0, 0, 0, -1, 0, 1, 0, state.lightCoords);
			face(p, v, 4, -h, 0, 0, 0, 0, 1, 0, 1, 0, state.lightCoords);
		});
		pose.popPose();
	}

	/** The pips of one face centred at (cx, cy, cz), spanned by the unit axes u and w; lifted a hair off the face. */
	private static void face(PoseStack.Pose p, VertexConsumer v, int value, float cx, float cy, float cz, float ux, float uy, float uz, float wx, float wy,
			float wz, int light) {
		float out = 1.04F;
		float ox = cx * out;
		float oy = cy * out;
		float oz = cz * out;
		float cell = SIZE / 3.6F;
		float pip = SIZE * 0.085F;
		int color = value == 1 ? 0xFFC81E28 : 0xFF1A1A20;
		for (int spot : PIPS[value - 1]) {
			float a = (spot % 3 - 1) * cell;
			float b = (1 - spot / 3) * cell;
			float px = ox + ux * a + wx * b;
			float py = oy + uy * a + wy * b;
			float pz = oz + uz * a + wz * b;
			Shapes.quad(p, v,
					px - ux * pip - wx * pip, py - uy * pip - wy * pip, pz - uz * pip - wz * pip,
					px + ux * pip - wx * pip, py + uy * pip - wy * pip, pz + uz * pip - wz * pip,
					px + ux * pip + wx * pip, py + uy * pip + wy * pip, pz + uz * pip + wz * pip,
					px - ux * pip + wx * pip, py - uy * pip + wy * pip, pz - uz * pip + wz * pip, color, light);
		}
	}
}
