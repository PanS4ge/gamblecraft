package pl.pansage.gamblecraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.model.CasinoGuardModel;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;

/** Draws the casino guards with their Blockbench model and the actions synced from the server. */
public class CasinoGuardRenderer extends MobRenderer<CasinoGuardEntity, CasinoGuardModel.State, CasinoGuardModel> {
	private static final Identifier TEXTURE = GambleCraft.id("textures/entity/casino_guard.png");
	private static final Identifier ELITE = GambleCraft.id("textures/entity/casino_guard_elite.png");

	public CasinoGuardRenderer(EntityRendererProvider.Context context) {
		super(context, new CasinoGuardModel(context.bakeLayer(CasinoGuardModel.LAYER)), 0.8F);
	}

	@Override
	public CasinoGuardModel.State createRenderState() {
		return new CasinoGuardModel.State();
	}

	@Override
	public void extractRenderState(CasinoGuardEntity guard, CasinoGuardModel.State state, float partialTick) {
		super.extractRenderState(guard, state, partialTick);
		state.punch.copyFrom(guard.punchAnimation);
		state.charge.copyFrom(guard.chargeAnimation);
		state.fire.copyFrom(guard.fireAnimation);
		state.elite = guard.elite();
	}

	@Override
	public Identifier getTextureLocation(CasinoGuardModel.State state) {
		return state.elite ? ELITE : TEXTURE;
	}
}
