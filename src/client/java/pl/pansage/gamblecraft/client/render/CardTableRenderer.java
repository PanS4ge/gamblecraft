package pl.pansage.gamblecraft.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.client.table.ClientCurrency;
import pl.pansage.gamblecraft.client.table.ClientTables;
import pl.pansage.gamblecraft.client.table.ClientTables.CardAnim;
import pl.pansage.gamblecraft.client.table.ClientTables.Toss;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.table.CardGame;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.entity.CardTableEntity;

/**
 * Draws a card game lying on the block top. Table space has the dealer on the -Z side and the seats around +Z;
 * {@link #tableYaw} turns it so the dealer's side faces the dealer. New cards fly in on an arc from the dealer's hands
 * and turn over; stakes and cards thrown by players fly from their hand to their place; hand values float above the cards.
 */
public class CardTableRenderer extends EntityRenderer<CardTableEntity, CardTableRenderer.State> {
	public static final float CARD_W = 0.16F;
	public static final float CARD_H = CARD_W * 124F / 88F;
	private static final Identifier BACK = texture(-1);
	private static final int TOSS_TICKS = 9;
	private static final int TOSS_LINGER = 12;

	/** Seat anchors in table space: x, z, rotation in degrees (cards and labels turn towards the seat). */
	private static final float[][] SEATS = {
			{0.0F, 0.30F, 0},
			{-0.30F, 0.05F, 90},
			{0.30F, 0.05F, -90},
			{-0.22F, 0.33F, 25},
			{0.22F, 0.33F, -25},
			{0.0F, 0.0F, 0},
	};

	public static class State extends EntityRenderState {
		public final List<CardDraw> cards = new ArrayList<>();
		public final List<Label> labels = new ArrayList<>();
		public final List<FlyingItem> items = new ArrayList<>();
		public float yaw;
	}

	/** One card in table space: position, yaw, flip angle (0 face up, 180 face down) and its face texture. */
	public record CardDraw(float x, float y, float z, float yaw, float flip, Identifier face) {
	}

	/** Text floating above the table, offset from the entity in world axes. */
	/** {@code alongX/alongZ}: table-space direction of the row, the label starts there instead of being centred. */
	public record Label(Vec3 offset, Component text, float alongX, float alongZ) {
	}

	/** A stake item thrown onto the table (or lying flat on it), offset from the entity in world axes. */
	public record FlyingItem(Vec3 offset, float spin, boolean flat, ItemStackRenderState item) {
	}

	private final ItemModelResolver itemModelResolver;

	public CardTableRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0;
		this.itemModelResolver = context.getItemModelResolver();
	}

	public static Identifier texture(int cardId) {
		return GambleCraft.id("textures/card/" + (cardId < 0 ? "back" : Card.of(cardId).code()) + ".png");
	}

	/** Rotation (degrees about Y) from table space to world space: table -Z points at the dealer. */
	public static float tableYaw(float facing) {
		return 180.0F - facing;
	}

	/** Table space to world offset. */
	public static Vec3 toWorld(float facing, double x, double y, double z) {
		double rad = Math.toRadians(tableYaw(facing));
		return new Vec3(x * Math.cos(rad) + z * Math.sin(rad), y, -x * Math.sin(rad) + z * Math.cos(rad));
	}

	/** World offset to table space. */
	public static Vec3 toTable(float facing, Vec3 rel) {
		double rad = Math.toRadians(tableYaw(facing));
		return new Vec3(rel.x * Math.cos(rad) - rel.z * Math.sin(rad), rel.y, rel.x * Math.sin(rad) + rel.z * Math.cos(rad));
	}

	/** Where a seat's cards go, in table space (x, z, rotation). */
	public static float[] seatAnchor(ClientTables.View view, int seat) {
		List<SeatView> seats = view.state.snapshot().seats();
		int order = 0;
		for (int i = 0; i < seats.size(); i++) {
			if (seats.get(i).index() == seat) {
				order = i;
			}
		}
		return SEATS[Math.min(order, SEATS.length - 1)];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public boolean shouldRender(CardTableEntity entity, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, float partialTick) {
		return ClientTables.get(entity.getId()) != null && super.shouldRender(entity, frustum, x, y, z, partialTick);
	}

	@Override
	public void extractRenderState(CardTableEntity table, State state, float partialTick) {
		super.extractRenderState(table, state, partialTick);
		state.cards.clear();
		state.labels.clear();
		state.items.clear();
		float facing = table.facing();
		state.yaw = tableYaw(facing);
		ClientTables.View view = ClientTables.get(table.getId());
		if (view == null || view.state == null) {
			return;
		}
		float now = ClientTables.now() + partialTick;
		Vec3 tablePos = table.getPosition(partialTick);
		Vec3 from = dealerHands(table, partialTick);
		int[] handsPerSeat = new int[16];
		for (HandView hand : view.state.snapshot().hands()) {
			List<CardAnim> anims = view.cards.get(hand.id());
			if (anims == null) {
				continue;
			}
			float baseX;
			float baseZ;
			float rot;
			float spacing = 0.09F;
			if (hand.owner() == CardGame.DEALER) {
				baseX = 0;
				baseZ = -0.30F;
				rot = 0;
			} else if (hand.owner() == CardGame.COMMUNITY) {
				baseX = 0;
				baseZ = -0.04F;
				rot = 0;
				spacing = CARD_W + 0.012F;
			} else {
				float[] a = seatAnchor(view, hand.owner());
				int extra = handsPerSeat[Math.min(hand.owner(), 15)]++;
				float rad = (float) Math.toRadians(a[2]);
				float side = extra * 0.16F;
				baseX = a[0] + Mth.cos(rad) * side;
				baseZ = a[1] - Mth.sin(rad) * side;
				rot = a[2];
			}
			int n = anims.size();
			float rad = (float) Math.toRadians(rot);
			for (int i = 0; i < n; i++) {
				CardAnim anim = anims.get(i);
				float offset = (i - (n - 1) / 2.0F) * spacing;
				float tx = baseX + Mth.cos(rad) * offset;
				float tz = baseZ - Mth.sin(rad) * offset;
				float yaw = rot + ((anim.card * 37 + i * 13) % 9 - 4);
				float lift = 0.002F + i * 0.0015F;

				float dealT = Mth.clamp((now - anim.dealtAt) / ClientTables.DEAL_TICKS, 0, 1);
				float x = tx;
				float y = lift;
				float z = tz;
				if (dealT < 1) {
					float e = 1 - (1 - dealT) * (1 - dealT);
					x = Mth.lerp(e, (float) from.x, tx);
					z = Mth.lerp(e, (float) from.z, tz);
					y = Mth.lerp(e, (float) from.y, lift) + Mth.sin(dealT * Mth.PI) * 0.25F;
					yaw += (1 - e) * 540;
				}
				float flip;
				if (anim.card < 0) {
					flip = 180;
				} else if (anim.flippedAt != Long.MIN_VALUE) {
					float flipT = Mth.clamp((now - anim.flippedAt) / ClientTables.FLIP_TICKS, 0, 1);
					flip = 180 * (1 - flipT);
					y += Mth.sin(flipT * Mth.PI) * 0.08F;
				} else {
					flip = dealT < 1 ? 180 * (1 - dealT) : 0;
				}
				if ((hand.marks() & (1 << i)) != 0) {
					z -= 0.025F;
				}
				state.cards.add(new CardDraw(x, y, z, yaw, flip, texture(anim.card)));
			}
			Component label = labelFor(hand);
			if (label != null) {
				// Beside the end of the row, so the text never covers the cards.
				float along = (n - 1) / 2.0F * spacing + CARD_W / 2 + 0.05F;
				float lx = baseX + Mth.cos(rad) * along;
				float lz = baseZ - Mth.sin(rad) * along;
				state.labels.add(new Label(toWorld(facing, lx, 0.05, lz), label, Mth.cos(rad), -Mth.sin(rad)));
			}
		}
		java.util.Set<Integer> stakesInFlight = new java.util.HashSet<>();
		for (Toss toss : view.tosses) {
			float age = now - toss.startedAt();
			if (age < 0 || age > TOSS_TICKS + TOSS_LINGER) {
				continue;
			}
			Entity thrower = table.level().getEntity(toss.entityId());
			float[] seat = seatAnchor(view, toss.seat());
			Vec3 target = toss.card() ? toWorld(facing, seat[0] * 0.8, 0.03, seat[1] * 0.8) : stakeSpot(facing, seat);
			Vec3 start = thrower != null
					? thrower.getEyePosition(partialTick).add(0, -0.45, 0).add(thrower.getViewVector(partialTick).scale(0.35)).subtract(tablePos)
					: target.add(0, 0.6, 0);
			float t = Mth.clamp(age / TOSS_TICKS, 0, 1);
			if (t >= 1 && !toss.card()) {
				continue;
			}
			float e = 1 - (1 - t) * (1 - t);
			Vec3 pos = start.lerp(target, e).add(0, Mth.sin(t * Mth.PI) * 0.35, 0);
			if (toss.card()) {
				Vec3 local = toTable(facing, pos);
				state.cards.add(new CardDraw((float) local.x, (float) local.y, (float) local.z, seat[2] + (1 - t) * 720, 180, BACK));
			} else {
				stakesInFlight.add(toss.seat());
				state.items.add(new FlyingItem(pos, (1 - t) * 900 + age * 4, false, itemState(table, toss.stack(), toss.entityId())));
			}
		}
		// Stakes lie on the felt in front of their seat until the round is paid out; chips stand in neat stacks.
		boolean chips = ClientCurrency.isChips(view.state.currency());
		for (SeatView seat : view.state.snapshot().seats()) {
			net.minecraft.world.item.ItemStack currency = ClientCurrency.stack(view.state.currency(), seat.bet());
			if (seat.bet() <= 0 || currency.isEmpty() || stakesInFlight.contains(seat.index()) || seat.player().equals(pl.pansage.gamblecraft.core.games.HoldemGame.HOUSE)) {
				continue;
			}
			float[] anchor = seatAnchor(view, seat.index());
			Vec3 spot = stakeSpot(facing, anchor);
			int pile = Math.min(chips ? 6 : 4, 1 + (int) (Math.log(seat.bet()) / Math.log(3)));
			for (int i = 0; i < pile; i++) {
				state.items.add(new FlyingItem(spot.add(0, i * (chips ? 0.018 : 0.03), 0), chips ? anchor[2] : anchor[2] + i * 23 + seat.index() * 40,
						!chips, itemState(table, currency, seat.index() * 7 + i)));
			}
		}
	}

	private static Vec3 stakeSpot(float facing, float[] anchor) {
		return toWorld(facing, anchor[0] * 0.45, 0.02, anchor[1] * 0.45);
	}

	private ItemStackRenderState itemState(CardTableEntity table, net.minecraft.world.item.ItemStack stack, int seed) {
		ItemStackRenderState item = new ItemStackRenderState();
		itemModelResolver.updateForTopItem(item, stack, ItemDisplayContext.GROUND, table.level(), null, seed);
		return item;
	}

	private static Component labelFor(HandView hand) {
		Msg value = pl.pansage.gamblecraft.client.config.ClientConfig.get().assistLevel >= 1 ? hand.value() : null;
		MutableComponent text = value == null ? null : Component.translatable(value.key(), value.args().toArray()).copy();
		if (hand.result() != null) {
			MutableComponent result = Component.translatable("gamblecraft.result." + hand.result());
			result.withStyle(switch (hand.result()) {
				case "win", "blackjack", "oczko", "perskie_oczko", "war_win", "cash_out" -> net.minecraft.ChatFormatting.GREEN;
				case "push", "tie" -> net.minecraft.ChatFormatting.YELLOW;
				default -> net.minecraft.ChatFormatting.RED;
			});
			text = text == null ? result : text.append("  ").append(result);
		}
		if (text != null && hand.active()) {
			text = Component.literal("▶ ").withStyle(net.minecraft.ChatFormatting.GOLD).append(text);
		}
		return text;
	}

	/** The dealer's hands in table space, where flying cards start. */
	private static Vec3 dealerHands(CardTableEntity table, float partialTick) {
		Entity dealer = table.level().getEntity(table.dealerId());
		Vec3 world = dealer != null ? dealer.getPosition(partialTick).add(0, 1.0, 0) : table.position().add(0, 0.5, 0);
		return toTable(table.facing(), world.subtract(table.getPosition(partialTick)));
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		int light = state.lightCoords;
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, state.yaw);
		for (CardDraw card : state.cards) {
			pose.pushPose();
			pose.translate(card.x(), card.y(), card.z());
			pose.rotateDegrees(Axis.YP, card.yaw());
			pose.rotateDegrees(Axis.ZP, card.flip());
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(card.face()), (p, v) -> quad(p, v, light, 0.0006F, true));
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(BACK), (p, v) -> quad(p, v, light, -0.0006F, false));
			pose.popPose();
		}
		pose.popPose();

		for (FlyingItem item : state.items) {
			pose.pushPose();
			pose.translate(item.offset().x, item.offset().y, item.offset().z);
			pose.rotateDegrees(Axis.YP, item.spin());
			if (item.flat()) {
				pose.rotateDegrees(Axis.XP, 90);
				pose.scale(0.45F, 0.45F, 0.45F);
			} else {
				pose.scale(0.6F, 0.6F, 0.6F);
			}
			item.item().submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
			pose.popPose();
		}

		Font font = getFont();
		for (Label label : state.labels) {
			pose.pushPose();
			pose.translate(label.offset().x, label.offset().y, label.offset().z);
			pose.rotate(camera.orientation);
			pose.scale(0.011F, -0.011F, 0.011F);
			var text = label.text().getVisualOrderText();
			float x = 0;
			collector.submitText(pose, x, 0, text, false, Font.DisplayMode.NORMAL, light, 0xFFFFFFFF, 0x90000000, 0);
			pose.popPose();
		}
		super.submit(state, pose, collector, camera);
	}

	/** A flat card quad; {@code up} faces +Y with the card's face, otherwise -Y with the back. */
	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int light, float y, boolean up) {
		float hw = CARD_W / 2;
		float hh = CARD_H / 2;
		float ny = up ? 1 : -1;
		if (up) {
			vertex(pose, consumer, -hw, y, -hh, 0, 0, light, ny);
			vertex(pose, consumer, -hw, y, hh, 0, 1, light, ny);
			vertex(pose, consumer, hw, y, hh, 1, 1, light, ny);
			vertex(pose, consumer, hw, y, -hh, 1, 0, light, ny);
		} else {
			vertex(pose, consumer, hw, y, -hh, 0, 0, light, ny);
			vertex(pose, consumer, hw, y, hh, 0, 1, light, ny);
			vertex(pose, consumer, -hw, y, hh, 1, 1, light, ny);
			vertex(pose, consumer, -hw, y, -hh, 1, 0, light, ny);
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u, float v, int light, float ny) {
		consumer.addVertex(pose, x, y, z)
				.setColor(255, 255, 255, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, 0, ny, 0);
	}
}
