package pl.pansage.gamblecraft.client.model;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * The village dealer, modelled in Blockbench (blockbench/street_dealer.bbmodel, geometry and animations in
 * {@link StreetDealerModelData}). He idles with the deck held up; one gesture at a time plays over that
 * (shuffle, deal, cheer, sulk), and his legs swing when walking.
 */
public class StreetDealerModel extends EntityModel<StreetDealerModel.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(GambleCraft.id("street_dealer"), "main");

	public static class State extends LivingEntityRenderState {
		public final AnimationState shuffle = new AnimationState();
		public final AnimationState deal = new AnimationState();
		public final AnimationState cheer = new AnimationState();
		public final AnimationState sulk = new AnimationState();
		public boolean deck = true;
		/** Props for the other casino people: the waitress's tray, the boss's and the loan shark's cigar. */
		public boolean tray;
		public boolean cigar;
	}

	private final ModelPart head;
	private final ModelPart deckPart;
	private final ModelPart trayPart;
	private final ModelPart cigarPart;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation shuffle;
	private final KeyframeAnimation deal;
	private final KeyframeAnimation cheer;
	private final KeyframeAnimation sulk;

	public StreetDealerModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.deckPart = root.getChild("right_arm").getChild("deck");
		this.trayPart = root.getChild("left_arm").getChild("tray");
		this.cigarPart = root.getChild("head").getChild("cigar");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.idle = StreetDealerModelData.IDLE.bake(root);
		this.shuffle = StreetDealerModelData.SHUFFLE.bake(root);
		this.deal = StreetDealerModelData.DEAL.bake(root);
		this.cheer = StreetDealerModelData.CHEER.bake(root);
		this.sulk = StreetDealerModelData.SULK.bake(root);
	}

	private static boolean playing(AnimationState state, AnimationDefinition definition, float age) {
		return state.isStarted() && state.getTimeInMillis(age) < definition.lengthInSeconds() * 1000;
	}

	@Override
	public void setupAnim(State state) {
		super.setupAnim(state);
		deckPart.visible = state.deck;
		trayPart.visible = state.tray;
		cigarPart.visible = state.cigar;
		float age = state.ageInTicks;
		// Each gesture starts and ends in the idle pose, so only one runs at a time and it replaces the idle loop.
		if (playing(state.shuffle, StreetDealerModelData.SHUFFLE, age)) {
			shuffle.apply(state.shuffle, age);
		} else if (playing(state.deal, StreetDealerModelData.DEAL, age)) {
			deal.apply(state.deal, age);
		} else if (playing(state.cheer, StreetDealerModelData.CHEER, age)) {
			cheer.apply(state.cheer, age);
		} else if (playing(state.sulk, StreetDealerModelData.SULK, age)) {
			sulk.apply(state.sulk, age);
		} else {
			idle.apply((long) (age * 50.0F), 1.0F);
		}
		head.yRot += state.yRot * Mth.DEG_TO_RAD;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
		float swing = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * state.walkAnimationSpeed * 0.5F;
		rightLeg.xRot = swing;
		leftLeg.xRot = -swing;
	}
}
