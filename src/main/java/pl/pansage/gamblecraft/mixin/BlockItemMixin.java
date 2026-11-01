package pl.pansage.gamblecraft.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.pansage.gamblecraft.game.CasinoSecurity;

/** Lets casino security see blocks placed inside a casino (Fabric has no event for placing a block). */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
	@Inject(method = "place", at = @At("HEAD"))
	private void gamblecraft$beforePlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		CasinoSecurity.beforePlace(context);
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void gamblecraft$afterPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		CasinoSecurity.afterPlace(context, cir.getReturnValue());
	}
}
