package pl.pansage.gamblecraft.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.block.ArcadeBlock;
import pl.pansage.gamblecraft.block.CasinoDoorBlock;
import pl.pansage.gamblecraft.block.entity.CrashPadBlockEntity;
import pl.pansage.gamblecraft.block.entity.DiceTableBlockEntity;
import pl.pansage.gamblecraft.block.entity.FortuneWheelBlockEntity;
import pl.pansage.gamblecraft.block.KolekturaBlock;
import pl.pansage.gamblecraft.block.PlinkoBlock;
import pl.pansage.gamblecraft.block.ScratchKioskBlock;
import pl.pansage.gamblecraft.block.RouletteFeltBlock;
import pl.pansage.gamblecraft.block.RouletteWheelBlock;
import pl.pansage.gamblecraft.block.ScratchLitterBlock;
import pl.pansage.gamblecraft.block.SewingTableBlock;
import pl.pansage.gamblecraft.block.SlotMachineBlock;

public final class ModBlocks {
	public static final Block KOLEKTURA = register("kolektura", KolekturaBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).randomTicks().noOcclusion());
	public static final Block SEWING_TABLE = register("sewing_table", SewingTableBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
	public static final Block CASINO_DOOR = register("casino_door", CasinoDoorBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block VIP_DOOR = register("vip_door", pl.pansage.gamblecraft.block.VipDoorBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block NPC_MARKER = register("npc_marker", pl.pansage.gamblecraft.block.NpcMarkerBlock::new, BlockBehaviour.Properties.of()
			.noCollision().noLootTable().replaceable().strength(-1.0F, 3600000.0F).pushReaction(PushReaction.POPPED));
	public static final Block SLOT_MACHINE = register("slot_machine", SlotMachineBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_RED).strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 9)
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final Block ROULETTE_WHEEL = register("roulette_wheel", RouletteWheelBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block ROULETTE_FELT = register("roulette_felt", RouletteFeltBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GREEN).strength(2.0F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
	public static final Block FORTUNE_WHEEL = register("fortune_wheel", p -> new ArcadeBlock(p, Block.box(2, 0, 2, 14, 16, 14), FortuneWheelBlockEntity::new),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(3.0F).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 8));
	public static final Block DICE_TABLE = register("dice_table", p -> new ArcadeBlock(p, Block.box(0, 0, 0, 16, 14, 16), DiceTableBlockEntity::new),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
	public static final Block CRASH_PAD = register("crash_pad", p -> new ArcadeBlock(p, Block.box(0, 0, 0, 16, 6, 16), CrashPadBlockEntity::new),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 6));
	public static final Block PLINKO = register("plinko", PlinkoBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLUE).strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 9).pushReaction(PushReaction.IMMOVEABLE));
	public static final Block SCRATCH_KIOSK = register("scratch_kiosk", ScratchKioskBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE).strength(2.5F).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 7));
	public static final Block SCRATCH_LITTER = register("scratch_litter", ScratchLitterBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.SNOW).noCollision().instabreak().sound(SoundType.WOOL).replaceable().pushReaction(PushReaction.POPPED).noOcclusion());

	private ModBlocks() {
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, GambleCraft.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
