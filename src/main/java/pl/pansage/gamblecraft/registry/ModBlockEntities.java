package pl.pansage.gamblecraft.registry;

import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.block.entity.DiceTableBlockEntity;
import pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity;
import pl.pansage.gamblecraft.block.entity.PlinkoBlockEntity;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;

public final class ModBlockEntities {
	public static final BlockEntityType<SlotMachineBlockEntity> SLOT_MACHINE = register("slot_machine", SlotMachineBlockEntity::new, ModBlocks.SLOT_MACHINE);
	public static final BlockEntityType<RouletteBlockEntity> ROULETTE = register("roulette", RouletteBlockEntity::new, ModBlocks.ROULETTE_WHEEL);
	public static final BlockEntityType<FortuneWheelBlockEntity> FORTUNE_WHEEL = register("fortune_wheel", FortuneWheelBlockEntity::new, ModBlocks.FORTUNE_WHEEL);
	public static final BlockEntityType<DiceTableBlockEntity> DICE_TABLE = register("dice_table", DiceTableBlockEntity::new, ModBlocks.DICE_TABLE);
	public static final BlockEntityType<PlinkoBlockEntity> PLINKO = register("plinko", PlinkoBlockEntity::new, ModBlocks.PLINKO);
	public static final BlockEntityType<CrashPadBlockEntity> CRASH_PAD = register("crash_pad", CrashPadBlockEntity::new, ModBlocks.CRASH_PAD);
	public static final BlockEntityType<pl.pansage.gamblecraft.block.entity.NpcMarkerBlockEntity> NPC_MARKER = register("npc_marker",
			pl.pansage.gamblecraft.block.entity.NpcMarkerBlockEntity::new, ModBlocks.NPC_MARKER);

	private ModBlockEntities() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntitySupplier<T> factory, Block block) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, GambleCraft.id(name), new BlockEntityType<>(factory, Set.of(block)));
	}

	public static void init() {
	}
}
