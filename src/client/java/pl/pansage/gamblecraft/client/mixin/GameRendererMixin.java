package pl.pansage.gamblecraft.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.pansage.gamblecraft.client.scratch.ClientScratch;
import pl.pansage.gamblecraft.client.table.TableCamera;

/** No first-person hand while the camera hovers over a card table or the player scratches a card. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void gamblecraft$hideHandOverTable(CallbackInfo ci) {
		if (TableCamera.isActive() || ClientScratch.isActive()) {
			ci.cancel();
		}
	}
}
