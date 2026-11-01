package pl.pansage.gamblecraft.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.model.StreetDealerModel;
import pl.pansage.gamblecraft.entity.CasinoNpc;

/** Any villager-shaped GambleCraft person: the Blockbench dealer model with its own texture (and deck or not). */
public class CasinoNpcRenderer<T extends CasinoNpc> extends MobRenderer<T, StreetDealerModel.State, StreetDealerModel> {
	private final Identifier texture;
	private final boolean deck;
	private final boolean tray;
	private final boolean cigar;

	public CasinoNpcRenderer(EntityRendererProvider.Context context, String texture, boolean deck) {
		this(context, texture, deck, false, false);
	}

	public CasinoNpcRenderer(EntityRendererProvider.Context context, String texture, boolean deck, boolean tray, boolean cigar) {
		super(context, new StreetDealerModel(context.bakeLayer(StreetDealerModel.LAYER)), 0.5F);
		this.texture = GambleCraft.id("textures/entity/" + texture + ".png");
		this.deck = deck;
		this.tray = tray;
		this.cigar = cigar;
	}

	@Override
	public StreetDealerModel.State createRenderState() {
		return new StreetDealerModel.State();
	}

	@Override
	public void extractRenderState(T npc, StreetDealerModel.State state, float partialTick) {
		super.extractRenderState(npc, state, partialTick);
		state.shuffle.copyFrom(npc.shuffleAnimation);
		state.deal.copyFrom(npc.dealAnimation);
		state.cheer.copyFrom(npc.cheerAnimation);
		state.sulk.copyFrom(npc.sulkAnimation);
		state.deck = deck;
		state.tray = tray;
		state.cigar = cigar;
	}

	@Override
	public Identifier getTextureLocation(StreetDealerModel.State state) {
		return texture;
	}
}
