package pl.pansage.gamblecraft.client.render;

import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;
import pl.pansage.gamblecraft.client.scratch.ScratchGeometry;
import pl.pansage.gamblecraft.client.screen.ScratchTableScreen;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.item.ScratchCardItem;

/**
 * A scratch card lying flat on a block: tier-coloured frame, the nine symbols, and whatever silver foil is left
 * on them. For the player scratching it, their own arm follows the mouse and rubs the card.
 */
public class ScratchCardRenderer extends EntityRenderer<ScratchCardEntity, ScratchCardRenderer.State> {
	private static final Identifier WHITE = GambleCraft.id("textures/misc/white.png");
	private static final int[] TIER_COLORS = {0xFF8C5A2E, 0xFF8E99A4, 0xFFC9A227};
	private static final float BIT = ScratchFoil.CELL / (float) ScratchFoil.SUB;
	private static final float ARM_SCALE = 0.75F;

	public static class State extends EntityRenderState {
		public float facing;
		public int tier;
		public int[] cells = new int[0];
		public ScratchFoil foil;
		public int result = ScratchCardEntity.RUNNING;
		public float time;
		public Component title = Component.empty();
		public Component village = Component.empty();
		public boolean hand;
		public Vec3 handAt = Vec3.ZERO;
		public Vec3 shoulder = Vec3.ZERO;
		public Identifier skin;
		public boolean slim;
		public boolean rubbing;
	}

	private final ModelPart wideArm;
	private final ModelPart slimArm;

	public ScratchCardRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0;
		this.wideArm = context.bakeLayer(ModelLayers.PLAYER).getChild("right_arm");
		this.slimArm = context.bakeLayer(ModelLayers.PLAYER_SLIM).getChild("right_arm");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ScratchCardEntity card, State state, float partialTick) {
		super.extractRenderState(card, state, partialTick);
		state.facing = card.facing();
		state.tier = Mth.clamp(card.tier(), 0, 2);
		state.cells = card.cells();
		state.foil = card.foil();
		state.result = card.result();
		state.time = card.tickCount + partialTick;
		ScratchCardItem.Tier tier = ScratchCardItem.Tier.values()[state.tier];
		state.title = Component.translatable("gamblecraft.scratch.title_tier", Component.translatable("gamblecraft.scratch.tier." + tier.key()));
		state.village = VillageRegistry.name(card.village());
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		double hx = ClientScratch.handX();
		double hy = ClientScratch.handY();
		state.hand = player != null && ClientScratch.isLocalCard(card.getId()) && mc.gui.screen() instanceof ScratchTableScreen
				&& !Double.isNaN(hx) && ScratchGeometry.onCard(hx, hy);
		if (state.hand) {
			Vec3 origin = card.getPosition(partialTick);
			state.handAt = ScratchGeometry.toWorld(card, hx, hy, partialTick).subtract(origin);
			double yaw = Math.toRadians(player.getYRot(partialTick));
			Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
			state.shoulder = player.getPosition(partialTick).add(0, player.isShiftKeyDown() ? 1.1 : 1.35, 0).add(right.scale(0.33)).subtract(origin);
			PlayerSkin skin = player.getSkin();
			state.skin = skin.body().texturePath();
			state.slim = skin.model() == PlayerModelType.SLIM;
			state.rubbing = ClientScratch.isStroking();
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		ScratchCardItem.Tier tier = ScratchCardItem.Tier.values()[state.tier];
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, CardTableRenderer.tableYaw(state.facing));
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(WHITE), (p, v) -> card(state, p, v, light));
		for (int cell = 0; cell < state.cells.length && cell < 9; cell++) {
			int prize = state.cells[cell];
			float cx = ScratchGeometry.GRID_LEFT + ScratchFoil.cellX(cell) + 7;
			float cy = ScratchGeometry.GRID_TOP + ScratchFoil.cellY(cell) + 3;
			float size = ScratchFoil.CELL - 14;
			Identifier symbol = GambleCraft.id("textures/gui/scratch/" + ScratchCards.Prize.values()[prize].key() + ".png");
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(symbol),
					(p, v) -> rect(p, v, cx, cy, cx + size, cy + size, 0.0026F, 0xFFFFFFFF, light, 0, 0, 1, 1));
			int value = ScratchCards.Prize.values()[prize].multiplier * tier.price;
			text(pose, collector, Component.literal(String.format(Locale.ROOT, "%d", value)).getVisualOrderText(),
					ScratchGeometry.GRID_LEFT + ScratchFoil.cellX(cell) + ScratchFoil.CELL / 2.0F,
					ScratchGeometry.GRID_TOP + ScratchFoil.cellY(cell) + ScratchFoil.CELL - 10, 0xFF3A6A2A, light);
		}
		text(pose, collector, state.title.getVisualOrderText(), ScratchGeometry.PANEL_W / 2.0F, 9, TIER_COLORS[state.tier] | 0xFF000000, light);
		text(pose, collector, state.village.getVisualOrderText(), ScratchGeometry.PANEL_W / 2.0F, 21, 0xFF5A4A2A, light);
		pose.popPose();
		if (state.hand) {
			arm(state, pose, collector, light);
		}
		super.submit(state, pose, collector, camera);
	}

	/** Frame, cream face, white cells, gold frames round a winning line, and the remaining foil. */
	private static void card(State state, PoseStack.Pose p, VertexConsumer v, int light) {
		int w = ScratchGeometry.PANEL_W;
		int h = ScratchGeometry.PANEL_H;
		rect(p, v, 0, 0, w, h, 0.0010F, TIER_COLORS[state.tier], light, 0, 0, 1, 1);
		rect(p, v, 4, 4, w - 4, h - 4, 0.0014F, 0xFFF4EBD0, light, 0, 0, 1, 1);
		int win = state.result;
		float pulse = 0.5F + 0.5F * Mth.sin(state.time * 0.4F);
		int gold = 0xFF000000 | (int) (200 + 55 * pulse) << 16 | (int) (160 + 60 * pulse) << 8;
		for (int cell = 0; cell < 9; cell++) {
			float x0 = ScratchGeometry.GRID_LEFT + ScratchFoil.cellX(cell);
			float y0 = ScratchGeometry.GRID_TOP + ScratchFoil.cellY(cell);
			if (win >= 0 && cell < state.cells.length && state.cells[cell] == win) {
				rect(p, v, x0 - 3, y0 - 3, x0 + ScratchFoil.CELL + 3, y0 + ScratchFoil.CELL + 3, 0.0017F, gold, light, 0, 0, 1, 1);
			}
			rect(p, v, x0, y0, x0 + ScratchFoil.CELL, y0 + ScratchFoil.CELL, 0.0020F, 0xFFFFFFFF, light, 0, 0, 1, 1);
			if (state.foil == null || state.foil.isRevealed(cell)) {
				continue;
			}
			for (int row = 0; row < ScratchFoil.SUB; row++) {
				int shade = 0xB8 + ((row * 7 + cell * 13) % 5) * 8;
				int color = 0xFF000000 | shade << 16 | shade << 8 | Math.min(255, shade + 8);
				int start = -1;
				for (int col = 0; col <= ScratchFoil.SUB; col++) {
					boolean on = col < ScratchFoil.SUB && state.foil.covered(cell, row * ScratchFoil.SUB + col);
					if (on && start < 0) {
						start = col;
					} else if (!on && start >= 0) {
						rect(p, v, x0 + start * BIT, y0 + row * BIT, x0 + col * BIT, y0 + (row + 1) * BIT, 0.0032F, color, light, 0, 0, 1, 1);
						start = -1;
					}
				}
			}
		}
	}

	/** A flat rectangle in panel coordinates (grid units from the card's top-left), facing up. */
	private static void rect(PoseStack.Pose p, VertexConsumer v, float px0, float py0, float px1, float py1, float y, int argb, int light,
			float u0, float v0, float u1, float v1) {
		float x0 = (float) ScratchGeometry.localX(px0);
		float x1 = (float) ScratchGeometry.localX(px1);
		float z0 = (float) ScratchGeometry.localZ(py0);
		float z1 = (float) ScratchGeometry.localZ(py1);
		vertex(p, v, x0, y, z0, u0, v0, argb, light);
		vertex(p, v, x0, y, z1, u0, v1, argb, light);
		vertex(p, v, x1, y, z1, u1, v1, argb, light);
		vertex(p, v, x1, y, z0, u1, v0, argb, light);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer v, float x, float y, float z, float u, float uv, int argb, int light) {
		v.addVertex(p, x, y, z)
				.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, 255)
				.setUv(u, uv)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, 0, 1, 0);
	}

	/** Text lying on the card, centred on a panel point. */
	private void text(PoseStack pose, SubmitNodeCollector collector, FormattedCharSequence text, float px, float py, int color, int light) {
		Font font = getFont();
		pose.pushPose();
		pose.translate(ScratchGeometry.localX(px), 0.0028F, ScratchGeometry.localZ(py));
		pose.rotateDegrees(Axis.XP, -90);
		pose.scale(ScratchGeometry.SCALE, -ScratchGeometry.SCALE, ScratchGeometry.SCALE);
		collector.submitText(pose, -font.width(text) / 2.0F, 0, text, false, Font.DisplayMode.NORMAL, light, color, 0, 0);
		pose.popPose();
	}

	/**
	 * The player's own arm, skin and sleeve included, reaching from their shoulder so that the hand rests on the
	 * card under the mouse. While rubbing it sways a little.
	 */
	private static void armPose(State state, PoseStack pose) {
		Vec3 toHand = state.handAt.subtract(state.shoulder);
		double flat = Math.sqrt(toHand.x * toHand.x + toHand.z * toHand.z);
		float yaw = (float) Math.toDegrees(Math.atan2(toHand.x, toHand.z));
		// kept low over the card so it does not rise into the overhead camera
		float rise = Mth.clamp((float) Math.toDegrees(Math.atan2(-toHand.y, flat)), 10, 26);
		float sway = state.rubbing ? Mth.sin(state.time * 2.2F) * 5 : 0;
		pose.translate(state.handAt.x, state.handAt.y + 0.07, state.handAt.z);
		pose.rotateDegrees(Axis.YP, yaw + sway);
		pose.rotateDegrees(Axis.XP, 90 + rise);
		pose.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
		pose.translate((state.slim ? 0.5F : 1.0F) / 16.0F, -10.0F / 16.0F, 0);
	}

	private void arm(State state, PoseStack pose, SubmitNodeCollector collector, int light) {
		ModelPart arm = state.slim ? slimArm : wideArm;
		arm.resetPose();
		arm.x = 0;
		arm.y = 0;
		arm.z = 0;
		arm.visible = true;
		for (ModelPart child : arm.getAllParts()) {
			child.visible = true;
		}
		pose.pushPose();
		armPose(state, pose);
		collector.submitModelPart(arm, pose, RenderTypes.entityTranslucent(state.skin), light, OverlayTexture.NO_OVERLAY, null);
		pose.popPose();
	}
}
