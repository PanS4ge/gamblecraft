package pl.pansage.gamblecraft.client.scratch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.client.config.ClientConfig;
import pl.pansage.gamblecraft.client.screen.ScratchScreen;
import pl.pansage.gamblecraft.client.screen.ScratchTableScreen;
import pl.pansage.gamblecraft.client.table.TableCamera;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.network.ScratchPayloads.ScratchRub;
import pl.pansage.gamblecraft.network.ScratchPayloads.Strokes;

/**
 * The local player's scratching session, and how other people's strokes look here. Strokes are applied to the
 * card's foil at once (quantised exactly as the server will), batched to the server every other tick, and each
 * bit of foil that comes off may leave a speck of silver dust that drifts down onto the card and the block.
 */
public final class ClientScratch {
	private static final int SEND_EVERY = 2;
	private static final BlockParticleOption FLAKE = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.IRON_BLOCK.defaultBlockState());

	private static int cardId = -1;
	private static final List<Integer> pending = new ArrayList<>();
	private static boolean stroking;
	private static double lastX = Double.NaN;
	private static double lastY;
	private static int ticks;
	private static int lastSound = -10;
	/** Where the local hand hovers over the card, in grid units (NaN when off the card). */
	private static double handX = Double.NaN;
	private static double handY = Double.NaN;
	private static final Set<Integer> scratchers = new HashSet<>();

	private ClientScratch() {
	}

	public static boolean isActive() {
		return cardId >= 0;
	}

	public static ScratchCardEntity card() {
		Minecraft mc = Minecraft.getInstance();
		return cardId >= 0 && mc.level != null && mc.level.getEntity(cardId) instanceof ScratchCardEntity card ? card : null;
	}

	public static boolean isLocalCard(int entityId) {
		return entityId == cardId;
	}

	/** The server laid our card on a block: look at it from above (or open the panel). */
	public static void open(int entityId) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.level.getEntity(entityId) instanceof ScratchCardEntity card)) {
			return;
		}
		cardId = entityId;
		pending.clear();
		stroking = false;
		lastX = Double.NaN;
		if (ClientConfig.get().scratchPanel) {
			mc.gui.setScreen(new ScratchScreen(card));
		} else {
			if (ClientConfig.get().overheadCamera) {
				TableCamera.activate(card);
			}
			mc.gui.setScreen(new ScratchTableScreen(card));
		}
	}

	/** Leaves the card where it lies; right-clicking it again resumes. */
	public static void close() {
		flush();
		cardId = -1;
		stroking = false;
		handX = Double.NaN;
		TableCamera.release();
	}

	public static void hover(double x, double y) {
		handX = x;
		handY = y;
	}

	public static double handX() {
		return handX;
	}

	public static double handY() {
		return handY;
	}

	public static boolean isStroking() {
		return stroking;
	}

	public static void beginStroke() {
		stroking = true;
		lastX = Double.NaN;
		if (!pending.isEmpty()) {
			pending.add(ScratchCardEntity.BREAK);
			pending.add(ScratchCardEntity.BREAK);
		}
	}

	public static void endStroke() {
		stroking = false;
		lastX = Double.NaN;
	}

	/** Rubs towards a point (grid units) on our card. {@code extra} also gets every flake, e.g. for dust on a panel. */
	public static int strokeTo(double x, double y, ScratchFoil.Flakes extra) {
		ScratchCardEntity card = card();
		if (card == null || card.result() != ScratchCardEntity.RUNNING) {
			return 0;
		}
		int qx = (int) Math.round(Math.max(-40, Math.min(ScratchFoil.SIZE + 40, x)) * 4);
		int qy = (int) Math.round(Math.max(-40, Math.min(ScratchFoil.SIZE + 40, y)) * 4);
		if (!Double.isNaN(lastX) && qx == (int) (lastX * 4) && qy == (int) (lastY * 4)) {
			return 0;
		}
		List<float[]> flakes = new ArrayList<>();
		ScratchFoil.Flakes sink = (fx, fy) -> {
			flakes.add(new float[] {fx, fy});
			if (extra != null) {
				extra.at(fx, fy);
			}
		};
		int removed = Double.isNaN(lastX) ? card.foil().rub(qx / 4.0, qy / 4.0, sink) : card.foil().rubLine(lastX, lastY, qx / 4.0, qy / 4.0, sink);
		lastX = qx / 4.0;
		lastY = qy / 4.0;
		pending.add(qx);
		pending.add(qy);
		effects(card, flakes);
		return removed;
	}

	public static void tick() {
		ticks++;
		Minecraft mc = Minecraft.getInstance();
		scratchers.clear();
		if (mc.level != null) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof ScratchCardEntity card && card.result() == ScratchCardEntity.RUNNING) {
					scratchers.add(card.ownerId());
				}
			}
		}
		if (cardId >= 0 && card() == null) {
			cardId = -1;
			TableCamera.release();
			if (mc.gui.screen() instanceof ScratchTableScreen || mc.gui.screen() instanceof ScratchScreen) {
				mc.gui.setScreen(null);
			}
			return;
		}
		if (ticks % SEND_EVERY == 0) {
			flush();
		}
	}

	private static void flush() {
		if (cardId < 0 || pending.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new ScratchRub(cardId, List.copyOf(pending)));
		pending.clear();
		// the next batch starts where this one ended, so the server rubs the joining segment too
		if (stroking && !Double.isNaN(lastX)) {
			pending.add((int) Math.round(lastX * 4));
			pending.add((int) Math.round(lastY * 4));
		}
	}

	/** Somebody else's strokes on a card near us. */
	public static void applyRemote(Strokes strokes) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.level.getEntity(strokes.entityId()) instanceof ScratchCardEntity card)) {
			return;
		}
		List<float[]> flakes = new ArrayList<>();
		ScratchCardEntity.applyStrokes(card.foil(), strokes.points(), (x, y) -> flakes.add(new float[] {x, y}));
		effects(card, flakes);
	}

	/** Players whose arm should reach for a card they are scratching. */
	public static boolean isScratching(int playerId) {
		return scratchers.contains(playerId);
	}

	/** Silver dust drifts down from where the foil came off; a soft scratching sound. */
	public static void effects(ScratchCardEntity card, List<float[]> flakes) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || flakes.isEmpty()) {
			return;
		}
		RandomSource random = mc.level.getRandom();
		int budget = Math.min(6, 1 + flakes.size() / 8);
		for (int i = 0; i < budget; i++) {
			float[] f = flakes.get(random.nextInt(flakes.size()));
			Vec3 at = ScratchGeometry.toWorld(card, f[0], f[1], 1.0F).add(0, 0.04 + random.nextDouble() * 0.05, 0);
			mc.level.addParticle(FLAKE, at.x, at.y, at.z, 0, 0, 0);
		}
		if (ticks - lastSound >= 3) {
			lastSound = ticks;
			mc.level.playLocalSound(card.getX(), card.getY(), card.getZ(), SoundEvents.BRUSH_SAND, SoundSource.PLAYERS, 0.45F,
					1.4F + random.nextFloat() * 0.4F, false);
		}
	}
}
