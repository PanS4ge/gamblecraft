package pl.pansage.gamblecraft.sportsbook.registry;

import java.util.Set;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import pl.pansage.gamblecraft.sportsbook.Sportsbook;
import pl.pansage.gamblecraft.sportsbook.block.BettingTerminalBlock;
import pl.pansage.gamblecraft.sportsbook.block.BettingTerminalBlockEntity;
import pl.pansage.gamblecraft.sportsbook.item.CouponItem;
import pl.pansage.gamblecraft.sportsbook.item.CouponView;

public final class SbRegistry {
	public static final DataComponentType<CouponView> COUPON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Sportsbook.id("coupon"),
			DataComponentType.<CouponView>builder().persistent(CouponView.CODEC).networkSynchronized(CouponView.STREAM_CODEC).cacheEncoding().build());

	public static final Block BETTING_TERMINAL = block("betting_terminal", BettingTerminalBlock::new, BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_GRAY).strength(3.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 7)
			.pushReaction(PushReaction.IMMOVEABLE));
	public static final BlockEntityType<BettingTerminalBlockEntity> BETTING_TERMINAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			Sportsbook.id("betting_terminal"), new BlockEntityType<>(BettingTerminalBlockEntity::new, Set.of(BETTING_TERMINAL)));

	public static final Item BETTING_TERMINAL_ITEM = item("betting_terminal", p -> new BlockItem(BETTING_TERMINAL, p.useBlockDescriptionPrefix()),
			new Item.Properties());
	public static final Item BET_COUPON = item("bet_coupon", CouponItem::new, new Item.Properties().stacksTo(1));

	private SbRegistry() {
	}

	private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Sportsbook.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Sportsbook.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, pl.pansage.gamblecraft.GambleCraft.id("main")))
				.register(entries -> entries.accept(BETTING_TERMINAL_ITEM));
	}
}
