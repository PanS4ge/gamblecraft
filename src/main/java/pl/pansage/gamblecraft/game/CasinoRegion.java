package pl.pansage.gamblecraft.game;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import pl.pansage.gamblecraft.GambleCraft;

/** Where the casino is: the bounding boxes of the generated {@code gamblecraft:casino} structure. */
public final class CasinoRegion {
	public static final ResourceKey<Structure> CASINO = ResourceKey.create(Registries.STRUCTURE, GambleCraft.id("casino"));
	/** For {@code /locate structure #gamblecraft:casino} and maps. */
	public static final TagKey<Structure> CASINO_TAG = TagKey.create(Registries.STRUCTURE, GambleCraft.id("casino"));
	/** Where the guards turn out from, in the casino template (tools/gen_casino.py GUARD_POST). */
	public static final BlockPos GUARD_POST = new BlockPos(8, 1, 50);

	/** One casino: a stable key, the area it covers and the guards' post (null when it has none). */
	public record Casino(String key, BoundingBox box, BlockPos guardPost) {
		public boolean contains(BlockPos pos) {
			return box.isInside(pos);
		}
	}

	/** Casinos declared in code rather than generated (game tests). */
	private static final List<Casino> DECLARED = new CopyOnWriteArrayList<>();

	private CasinoRegion() {
	}

	/** The casino structure with a piece at {@code pos}, or null. */
	public static StructureStart at(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(CASINO));
		return start != null && start.isValid() ? start : null;
	}

	public static boolean contains(ServerLevel level, BlockPos pos) {
		return find(level, pos).isPresent();
	}

	/** The casino around {@code pos}, generated or declared. */
	public static Optional<Casino> find(ServerLevel level, BlockPos pos) {
		for (Casino casino : DECLARED) {
			if (casino.contains(pos)) {
				return Optional.of(casino);
			}
		}
		StructureStart start = at(level, pos);
		if (start == null) {
			return Optional.empty();
		}
		BlockPos post = null;
		if (!start.getPieces().isEmpty() && start.getPieces().getFirst() instanceof PoolElementStructurePiece piece) {
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
			post = StructureTemplate.calculateRelativePosition(settings, GUARD_POST).offset(piece.getPosition());
		}
		return Optional.of(new Casino("casino_" + start.getChunkPos().pack(), start.getBoundingBox(), post));
	}

	/** Treats {@code box} as a casino from now on (for game tests, which cannot generate the structure). */
	public static Casino declare(String key, BoundingBox box, BlockPos guardPost) {
		Casino casino = new Casino(key, box, guardPost);
		DECLARED.add(casino);
		return casino;
	}

	public static void forget(Casino casino) {
		DECLARED.remove(casino);
	}
}
