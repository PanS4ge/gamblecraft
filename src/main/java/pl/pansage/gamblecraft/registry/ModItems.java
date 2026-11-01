package pl.pansage.gamblecraft.registry;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorType;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.casino.Chips;
import pl.pansage.gamblecraft.game.Suit;
import pl.pansage.gamblecraft.item.CardDeckItem;
import pl.pansage.gamblecraft.item.ChipItem;
import pl.pansage.gamblecraft.item.PlayingCardItem;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.item.SecurityGear;
import pl.pansage.gamblecraft.item.ShockwavePistolItem;

public final class ModItems {
	public static final Item CARD = register("card", PlayingCardItem::new, new Item.Properties().stacksTo(16));
	public static final Item CARD_DECK = register("card_deck", CardDeckItem::new, new Item.Properties().stacksTo(1));
	public static final Item STREET_DEALER_SPAWN_EGG = register("street_dealer_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.STREET_DEALER));
	public static final Item LOTTERY_CLERK_SPAWN_EGG = register("lottery_clerk_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.LOTTERY_CLERK));
	public static final Item SCRATCH_CARD_BRONZE = register("scratch_card_bronze", p -> new ScratchCardItem(ScratchCardItem.Tier.BRONZE, p),
			new Item.Properties().stacksTo(1));
	public static final Item SCRATCH_CARD_SILVER = register("scratch_card_silver", p -> new ScratchCardItem(ScratchCardItem.Tier.SILVER, p),
			new Item.Properties().stacksTo(1));
	public static final Item SCRATCH_CARD_GOLD = register("scratch_card_gold", p -> new ScratchCardItem(ScratchCardItem.Tier.GOLD, p),
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item USED_SCRATCH_CARD = register("used_scratch_card", Item::new, new Item.Properties());
	public static final Item KOLEKTURA = register("kolektura", p -> new BlockItem(ModBlocks.KOLEKTURA, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SCRATCH_LITTER = register("scratch_litter", p -> new BlockItem(ModBlocks.SCRATCH_LITTER, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final Item SEWING_TABLE = register("sewing_table", p -> new BlockItem(ModBlocks.SEWING_TABLE, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SUIT_JACKET = register("suit_jacket", Item::new, new Item.Properties().humanoidArmor(Suit.MATERIAL, ArmorType.CHESTPLATE));
	public static final Item SUIT_TROUSERS = register("suit_trousers", Item::new, new Item.Properties().humanoidArmor(Suit.MATERIAL, ArmorType.LEGGINGS));
	public static final Item SUIT_SHOES = register("suit_shoes", Item::new, new Item.Properties().humanoidArmor(Suit.MATERIAL, ArmorType.BOOTS));
	/** One item per denomination, in the order of {@link Chips#VALUES}. */
	public static final Item[] CHIPS = chips();
	public static final Item CASHIER_SPAWN_EGG = register("cashier_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CASHIER));

	public static final Item CASINO_DOOR = register("casino_door", p -> new DoubleHighBlockItem(ModBlocks.CASINO_DOOR, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CASINO_DEALER_SPAWN_EGG = register("casino_dealer_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CASINO_DEALER));
	public static final Item BOUNCER_SPAWN_EGG = register("bouncer_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.BOUNCER));
	public static final Item CASINO_GUARD_SPAWN_EGG = register("casino_guard_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CASINO_GUARD));
	public static final Item ADDICTED_GAMBLER_SPAWN_EGG = register("addicted_gambler_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.ADDICTED_GAMBLER));
	public static final Item LOAN_SHARK_SPAWN_EGG = register("loan_shark_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.LOAN_SHARK));
	public static final Item BARTENDER_SPAWN_EGG = register("bartender_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.BARTENDER));
	public static final Item WAITRESS_SPAWN_EGG = register("waitress_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.WAITRESS));
	public static final Item PICKPOCKET_SPAWN_EGG = register("pickpocket_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.PICKPOCKET));
	public static final Item CASINO_BOSS_SPAWN_EGG = register("casino_boss_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.CASINO_BOSS));
	public static final Item DEBT_COLLECTOR_SPAWN_EGG = register("debt_collector_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.DEBT_COLLECTOR));
	public static final Item MUGGER_SPAWN_EGG = register("mugger_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ModEntities.MUGGER));
	public static final Item CLUB = register("club", Item::new, new Item.Properties().sword(net.minecraft.world.item.ToolMaterial.WOOD, 4.0F, -2.8F));
	public static final Item PROMISSORY_NOTE = register("promissory_note", pl.pansage.gamblecraft.item.PromissoryNoteItem::new, new Item.Properties().stacksTo(1));
	public static final Item LUCKY_COIN = register("lucky_coin", pl.pansage.gamblecraft.item.DescribedItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item GOLD_WATCH = register("gold_watch", pl.pansage.gamblecraft.item.GoldWatchItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item PAWN_TICKET = register("pawn_ticket", pl.pansage.gamblecraft.item.DescribedItem::new, new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item CHAMPAGNE = register("champagne", pl.pansage.gamblecraft.item.DescribedItem::new,
			pl.pansage.gamblecraft.item.Drinks.properties(pl.pansage.gamblecraft.item.Drinks.champagne()));
	public static final Item ESPRESSO = register("espresso", pl.pansage.gamblecraft.item.DescribedItem::new,
			pl.pansage.gamblecraft.item.Drinks.properties(pl.pansage.gamblecraft.item.Drinks.espresso()));
	public static final Item WHISKY = register("whisky", pl.pansage.gamblecraft.item.DescribedItem::new,
			pl.pansage.gamblecraft.item.Drinks.properties(pl.pansage.gamblecraft.item.Drinks.whisky()));
	public static final Item MOJITO = register("mojito", pl.pansage.gamblecraft.item.DescribedItem::new,
			pl.pansage.gamblecraft.item.Drinks.properties(pl.pansage.gamblecraft.item.Drinks.mojito()));
	public static final Item VIP_CARD = register("vip_card", pl.pansage.gamblecraft.item.DescribedItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	public static final Item GOLDEN_SIGNET = register("golden_signet", pl.pansage.gamblecraft.item.DescribedItem::new, new Item.Properties().stacksTo(1)
			.rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	public static final Item VIP_DOOR = register("vip_door", p -> new DoubleHighBlockItem(ModBlocks.VIP_DOOR, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item NPC_MARKER = register("npc_marker", p -> new BlockItem(ModBlocks.NPC_MARKER, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SECURITY_CAP = register("security_cap", Item::new,
			new Item.Properties().humanoidArmor(SecurityGear.CAP_MATERIAL, ArmorType.HELMET).rarity(Rarity.RARE));
	public static final Item SHOCKWAVE_PISTOL = register("shockwave_pistol", ShockwavePistolItem::new,
			new Item.Properties().durability(100).repairable(Items.ECHO_SHARD).rarity(Rarity.RARE));

	public static final Item SLOT_MACHINE = register("slot_machine", p -> new DoubleHighBlockItem(ModBlocks.SLOT_MACHINE, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item ROULETTE_WHEEL = register("roulette_wheel", p -> new BlockItem(ModBlocks.ROULETTE_WHEEL, p),
			new Item.Properties().useBlockDescriptionPrefix());
	public static final Item ROULETTE_FELT = register("roulette_felt", p -> new BlockItem(ModBlocks.ROULETTE_FELT, p),
			new Item.Properties().useBlockDescriptionPrefix());

	public static final Item FORTUNE_WHEEL = register("fortune_wheel", p -> new BlockItem(ModBlocks.FORTUNE_WHEEL, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item DICE_TABLE = register("dice_table", p -> new BlockItem(ModBlocks.DICE_TABLE, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item PLINKO = register("plinko", p -> new DoubleHighBlockItem(ModBlocks.PLINKO, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item SCRATCH_KIOSK = register("scratch_kiosk", p -> new BlockItem(ModBlocks.SCRATCH_KIOSK, p), new Item.Properties().useBlockDescriptionPrefix());
	public static final Item CRASH_PAD = register("crash_pad", p -> new BlockItem(ModBlocks.CRASH_PAD, p), new Item.Properties().useBlockDescriptionPrefix());

	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GambleCraft.id("main"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.gamblecraft"))
					.icon(CardDeckItem::full)
					.displayItems((params, output) -> {
						output.accept(STREET_DEALER_SPAWN_EGG);
						output.accept(LOTTERY_CLERK_SPAWN_EGG);
						output.accept(CASHIER_SPAWN_EGG);
						output.accept(CASINO_DEALER_SPAWN_EGG);
						output.accept(BOUNCER_SPAWN_EGG);
						output.accept(CASINO_GUARD_SPAWN_EGG);
						output.accept(ADDICTED_GAMBLER_SPAWN_EGG);
						output.accept(LOAN_SHARK_SPAWN_EGG);
						output.accept(BARTENDER_SPAWN_EGG);
						output.accept(WAITRESS_SPAWN_EGG);
						output.accept(PICKPOCKET_SPAWN_EGG);
						output.accept(CASINO_BOSS_SPAWN_EGG);
						output.accept(DEBT_COLLECTOR_SPAWN_EGG);
						output.accept(MUGGER_SPAWN_EGG);
						output.accept(VIP_CARD);
						output.accept(GOLDEN_SIGNET);
						output.accept(CHAMPAGNE);
						output.accept(ESPRESSO);
						output.accept(WHISKY);
						output.accept(MOJITO);
						output.accept(LUCKY_COIN);
						output.accept(GOLD_WATCH);
						output.accept(PAWN_TICKET);
						output.accept(PROMISSORY_NOTE);
						output.accept(CLUB);
						output.accept(VIP_DOOR);
						output.accept(CASINO_DOOR);
						output.accept(SLOT_MACHINE);
						output.accept(ROULETTE_WHEEL);
						output.accept(ROULETTE_FELT);
						output.accept(FORTUNE_WHEEL);
						output.accept(DICE_TABLE);
						output.accept(CRASH_PAD);
						output.accept(PLINKO);
						output.accept(SCRATCH_KIOSK);
						output.accept(SECURITY_CAP);
						output.accept(SHOCKWAVE_PISTOL);
						output.accept(SUIT_JACKET);
						output.accept(SUIT_TROUSERS);
						output.accept(SUIT_SHOES);
						output.accept(SEWING_TABLE);
						for (Item chip : CHIPS) {
							output.accept(chip);
						}
						output.accept(KOLEKTURA);
						output.accept(SCRATCH_CARD_BRONZE);
						output.accept(SCRATCH_CARD_SILVER);
						output.accept(SCRATCH_CARD_GOLD);
						output.accept(USED_SCRATCH_CARD);
						output.accept(SCRATCH_LITTER);
						output.accept(CardDeckItem.full());
						output.accept(CardDeckItem.empty());
						for (int id = 0; id < Card.DECK_SIZE; id++) {
							output.accept(PlayingCardItem.stackOf(Card.of(id)));
						}
					})
					.build());

	private ModItems() {
	}

	private static Item[] chips() {
		Item[] chips = new Item[Chips.VALUES.length];
		for (int i = 0; i < chips.length; i++) {
			int value = Chips.VALUES[i];
			chips[i] = register("chip_" + value, p -> new ChipItem(value, p),
					new Item.Properties().rarity(value >= 500 ? Rarity.RARE : value >= 100 ? Rarity.UNCOMMON : Rarity.COMMON));
		}
		return chips;
	}

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, GambleCraft.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	public static void init() {
	}
}
