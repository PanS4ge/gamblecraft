package pl.pansage.gamblecraft.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;

/**
 * Other players scratching a card reach for it with their main arm (the brushing pose); their arm swings come from
 * the server while they rub. The local player is left alone: they see their own hand on the card instead.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
			at = @At("TAIL"))
	private void gamblecraft$reachForScratchCard(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
		if (avatar != Minecraft.getInstance().player && ClientScratch.isScratching(avatar.getId())) {
			if (state.mainArm == HumanoidArm.LEFT) {
				state.leftArmPose = HumanoidModel.ArmPose.BRUSH;
			} else {
				state.rightArmPose = HumanoidModel.ArmPose.BRUSH;
			}
		}
	}
}
