package pl.pansage.gamblecraft.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** The addicted gambler's stash: a chest he buried a little way off, and the map with an X on it. */
public final class StashMaps {
	private StashMaps() {
	}

	/** Buries a chest 150 to 300 blocks from {@code near} and returns the map to it. */
	public static ItemStack create(ServerLevel level, BlockPos near, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = 150 + random.nextInt(150);
		int x = near.getX() + (int) (Math.cos(angle) * distance);
		int z = near.getZ() + (int) (Math.sin(angle) * distance);
		level.getChunk(x >> 4, z >> 4);
		BlockPos chest = new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 3, z);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
		if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
			box.setLootTable(BuiltInLootTables.BURIED_TREASURE);
			box.setLootTableSeed(random.nextLong());
		}
		ItemStack map = MapItem.create(level, x, z, (byte) 1, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, chest, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("gamblecraft.gambler.stash_map"));
		return map;
	}
}
