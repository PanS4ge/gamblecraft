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
 * The casino guard, modelled in Blockbench (blockbench/casino_guard.bbmodel, geometry and animations in
 * {@link CasinoGuardModelData}). Guards breathe in place, walk like iron golems, and play the punch, the
 * shockwave charge (arm levelled at the target) and the recoil of the shot as the server calls them.
 */
public class CasinoGuardModel extends EntityModel<CasinoGuardModel.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(GambleCraft.id("casino_guard"), "main");

	public static class State extends LivingEntityRenderState {
		public final AnimationState punch = new AnimationState();
		public final AnimationState charge = new AnimationState();
		public final AnimationState fire = new AnimationState();
		public boolean elite;
	}

	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation punch;
	private final KeyframeAnimation charge;
	private final KeyframeAnimation fire;

	public CasinoGuardModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.idle = CasinoGuardModelData.IDLE.bake(root);
		this.punch = CasinoGuardModelData.PUNCH.bake(root);
		this.charge = CasinoGuardModelData.CHARGE.bake(root);
		this.fire = CasinoGuardModelData.FIRE.bake(root);
	}

	private static boolean playing(AnimationState state, AnimationDefinition definition, float age) {
		return state.isStarted() && state.getTimeInMillis(age) < definition.lengthInSeconds() * 1000;
	}

	@Override
	public void setupAnim(State state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		idle.apply((long) (age * 50.0F), 1.0F);
		boolean busy = state.charge.isStarted() || playing(state.fire, CasinoGuardModelData.FIRE, age)
				|| playing(state.punch, CasinoGuardModelData.PUNCH, age);
		charge.apply(state.charge, age);
		fire.apply(state.fire, age);
		punch.apply(state.punch, age);
		head.yRot += state.yRot * Mth.DEG_TO_RAD;
		head.xRot += state.xRot * Mth.DEG_TO_RAD;
		// an iron golem's heavy stride; the arms swing with it unless they are busy
		float pos = state.walkAnimationPos;
		float speed = state.walkAnimationSpeed;
		rightLeg.xRot += -1.5F * Mth.triangleWave(pos, 13.0F) * speed;
		leftLeg.xRot += 1.5F * Mth.triangleWave(pos, 13.0F) * speed;
		if (!busy) {
			rightArm.xRot += (-0.2F + 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
			leftArm.xRot += (-0.2F - 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
		}
	}
}
