package pl.pansage.gamblecraft.game;

import java.util.Optional;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import pl.pansage.gamblecraft.GambleCraft;

/**
 * Villages are told apart by their meeting-point bell. Each gets a name made of translated parts
 * ("Golden Hollow" / "Złota Dolina") derived from the bell position, and the world keeps a secret
 * that scratch-card outcomes are mixed with, so a card's result cannot be worked out from its data.
 */
public final class VillageRegistry extends SavedData {
	public static final int SEARCH_RADIUS = 96;
	public static final int NAME_PARTS = 16;
	private static final Codec<VillageRegistry> CODEC = Codec.LONG.xmap(VillageRegistry::new, r -> r.secret);
	public static final SavedDataType<VillageRegistry> TYPE = new SavedDataType<>(GambleCraft.id("villages"), VillageRegistry::create, CODEC, null);

	private final long secret;

	private VillageRegistry(long secret) {
		this.secret = secret;
	}

	private static VillageRegistry create() {
		VillageRegistry registry = new VillageRegistry(new java.security.SecureRandom().nextLong());
		registry.setDirty();
		return registry;
	}

	public static VillageRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public long secret() {
		return secret;
	}

	/** The bell of the village around {@code pos}, if any. */
	public static Optional<BlockPos> nearestBell(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), pos, SEARCH_RADIUS, PoiManager.Occupancy.ANY);
	}

	/** Name index of a village: two parts out of {@link #NAME_PARTS} each, from its bell position. */
	public static int nameIndex(BlockPos bell) {
		long h = bell.asLong() * 0x9E3779B97F4A7C15L;
		h ^= h >>> 29;
		int adjective = (int) Math.floorMod(h, NAME_PARTS);
		int noun = (int) Math.floorMod(h >>> 17, NAME_PARTS);
		return adjective * NAME_PARTS + noun;
	}

	public static Component name(int index) {
		if (index == pl.pansage.gamblecraft.item.ScratchData.CASINO_NAME) {
			return Component.translatable("gamblecraft.casino.name");
		}
		if (index < 0) {
			return Component.translatable("gamblecraft.village.unknown");
		}
		return Component.translatable("gamblecraft.village.name",
				Component.translatable("gamblecraft.village.adj." + index / NAME_PARTS),
				Component.translatable("gamblecraft.village.noun." + index % NAME_PARTS));
	}

	/** "about 230 blocks north-east" from one position to another. */
	public static Component direction(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		int distance = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
		double angle = Math.toDegrees(Math.atan2(dz, dx));
		String[] dirs = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
		int octant = (int) Math.floorMod(Math.round(angle / 45.0), 8);
		return Component.translatable("gamblecraft.village.direction", distance, Component.translatable("gamblecraft.direction." + dirs[octant]));
	}
}
