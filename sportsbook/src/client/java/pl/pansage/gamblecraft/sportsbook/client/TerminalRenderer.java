package pl.pansage.gamblecraft.sportsbook.client;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.client.render.Shapes;
import pl.pansage.gamblecraft.sportsbook.block.BettingTerminalBlockEntity;
import pl.pansage.gamblecraft.sportsbook.feed.EventState;
import pl.pansage.gamblecraft.sportsbook.feed.League;
import pl.pansage.gamblecraft.sportsbook.item.CouponItem;
import pl.pansage.gamblecraft.sportsbook.network.SbPayloads.EventView;

/**
 * The terminal's screen: games running now first, then the next ones with their prices, five to a page, turning
 * every five seconds. Everyone sees the same screen, since every player gets the same feed.
 */
public class TerminalRenderer implements BlockEntityRenderer<BettingTerminalBlockEntity, TerminalRenderer.State> {
	/** The screen's face in the block's local frame (front towards +z after turning by the facing). */
	private static final float SCREEN_Z = 0.34375F + 0.003F;
	private static final float X0 = -0.33F;
	private static final float X1 = 0.33F;
	private static final float Y0 = 1.07F;
	private static final float Y1 = 1.625F;
	private static final float SCALE = 0.0039F;
	private static final int PER_PAGE = 5;
	/** Live games and the next ones, at most this many in the loop. */
	private static final int MAX_SHOWN = 25;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	public record Line(Component left, Component right, int leftColour, int rightColour) {
	}

	public static class State extends BlockEntityRenderState {
		public Direction facing = Direction.NORTH;
		public boolean upper;
		public float time;
		public final List<Line> lines = new ArrayList<>();
		public int page;
		public int pages;
	}

	private final Font font;

	public TerminalRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BettingTerminalBlockEntity terminal, State state, float partialTick, Vec3 camera,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		BlockEntityRenderState.extractBase(terminal, state, crumbling);
		state.facing = terminal.getBlockState().getValue(TallMachineBlock.FACING);
		state.upper = terminal.getBlockState().getValue(TallMachineBlock.HALF) == DoubleBlockHalf.UPPER;
		state.time = terminal.getLevel().getGameTime() + partialTick;
		state.lines.clear();
		if (state.upper) {
			return;
		}
		long now = System.currentTimeMillis();
		List<EventView> shown = new ArrayList<>();
		for (EventView e : ClientFeed.events()) {
			if (e.state() == EventState.LIVE.ordinal()) {
				shown.add(e);
			}
		}
		for (EventView e : ClientFeed.events()) {
			if (ClientFeed.open(e, now) && shown.size() < MAX_SHOWN) {
				shown.add(e);
			}
		}
		state.pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
		state.page = (int) (terminal.getLevel().getGameTime() / 100) % state.pages;
		int width = (int) ((X1 - X0) / SCALE) - 4;
		ZoneId zone = ZoneId.systemDefault();
		for (int i = state.page * PER_PAGE; i < Math.min(shown.size(), (state.page + 1) * PER_PAGE); i++) {
			EventView e = shown.get(i);
			String teams = font.plainSubstrByWidth(e.home() + " - " + e.away(), width);
			state.lines.add(new Line(Component.literal(teams), Component.empty(), 0xFFFFFFFF, 0));
			League league = League.of(e.league());
			Component tag = league == null ? Component.empty() : Component.translatable(league.translationKey() + ".short");
			if (e.state() == EventState.LIVE.ordinal()) {
				state.lines.add(new Line(Component.literal("● ").append(e.clock()).append(" ").append(tag), Component.literal(e.score()),
						0xFFFF5050, 0xFFFFFFFF));
			} else {
				String odds = e.oddsDraw() > 1 ? CouponItem.odds(e.oddsHome()) + " " + CouponItem.odds(e.oddsDraw()) + " " + CouponItem.odds(e.oddsAway())
						: CouponItem.odds(e.oddsHome()) + "  " + CouponItem.odds(e.oddsAway());
				state.lines.add(new Line(Component.literal(TIME.format(Instant.ofEpochMilli(e.kickoff()).atZone(zone)) + " ").append(tag),
						Component.literal(odds), 0xFF9AB0C8, 0xFFFFD040));
			}
		}
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.upper) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5, 0, 0.5);
		pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(Shapes.WHITE), (p, v) -> {
			// the bezel over the whole face hides the texture's painted lines, then the lit screen inside it
			Shapes.wall(p, v, -0.405F, 1.0F, 0.405F, 1.6875F, SCREEN_Z - 0.001F, 0xFF1C1F26, state.lightCoords);
			Shapes.wall(p, v, X0, Y0, X1, Y1, SCREEN_Z, 0xFF0A1626, Shapes.FULL_BRIGHT);
			Shapes.wall(p, v, X0, Y1 - 0.06F, X1, Y1, SCREEN_Z + 0.001F, 0xFF1F7A34, Shapes.FULL_BRIGHT);
		});
		int width = (int) ((X1 - X0) / SCALE);
		boolean blink = ((int) (state.time / 10)) % 2 == 0;
		text(pose, collector, Component.translatable("gamblecraft_sportsbook.terminal.header"), 2, 2, 0xFFFFFFFF);
		Component page = Component.literal((state.page + 1) + "/" + state.pages);
		text(pose, collector, page, width - 2 - font.width(page), 2, 0xFFCCFFCC);
		if (state.lines.isEmpty()) {
			text(pose, collector, Component.translatable("gamblecraft_sportsbook.terminal.no_games"), 3, 22, 0xFF8090A0);
		}
		int y = 19;
		for (int i = 0; i < state.lines.size(); i++) {
			Line line = state.lines.get(i);
			int colour = line.leftColour() == 0xFFFF5050 && !blink ? 0xFFB03030 : line.leftColour();
			text(pose, collector, line.left(), 3, y, colour);
			if (line.rightColour() != 0) {
				text(pose, collector, line.right(), width - 3 - font.width(line.right()), y, line.rightColour());
			}
			y += i % 2 == 0 ? 10 : 13;
		}
		pose.popPose();
	}

	/** Text on the screen, {@code x}/{@code y} in font pixels from its top left corner. */
	private void text(PoseStack pose, SubmitNodeCollector collector, Component text, float x, float y, int colour) {
		pose.pushPose();
		pose.translate(X0, Y1, SCREEN_Z + 0.002F);
		pose.scale(SCALE, -SCALE, SCALE);
		collector.submitText(pose, x, y, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, Shapes.FULL_BRIGHT, colour, 0, 0);
		pose.popPose();
	}
}
