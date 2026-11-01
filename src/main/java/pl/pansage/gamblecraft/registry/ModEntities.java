package pl.pansage.gamblecraft.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.entity.BouncerEntity;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.CasinoDealerEntity;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.entity.CashierEntity;
import pl.pansage.gamblecraft.entity.LotteryClerkEntity;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;
import pl.pansage.gamblecraft.entity.StreetDealerEntity;

public final class ModEntities {
	public static final EntityType<CardTableEntity> CARD_TABLE = register("card_table",
			EntityType.Builder.<CardTableEntity>of(CardTableEntity::new, MobCategory.MISC)
					.sized(0.95F, 0.12F).noSave().noSummon().clientTrackingRange(8).updateInterval(20));

	public static final EntityType<StreetDealerEntity> STREET_DEALER = register("street_dealer",
			EntityType.Builder.<StreetDealerEntity>of(StreetDealerEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<LotteryClerkEntity> LOTTERY_CLERK = register("lottery_clerk",
			EntityType.Builder.<LotteryClerkEntity>of(LotteryClerkEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<CashierEntity> CASHIER = register("cashier",
			EntityType.Builder.<CashierEntity>of(CashierEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<CasinoDealerEntity> CASINO_DEALER = register("casino_dealer",
			EntityType.Builder.<CasinoDealerEntity>of(CasinoDealerEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<BouncerEntity> BOUNCER = register("bouncer",
			EntityType.Builder.<BouncerEntity>of(BouncerEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<CasinoGuardEntity> CASINO_GUARD = register("casino_guard",
			EntityType.Builder.<CasinoGuardEntity>of(CasinoGuardEntity::new, MobCategory.MISC)
					.sized(1.4F, 2.7F).eyeHeight(2.3F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.AddictedGamblerEntity> ADDICTED_GAMBLER = register("addicted_gambler",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.AddictedGamblerEntity>of(pl.pansage.gamblecraft.entity.AddictedGamblerEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.LoanSharkEntity> LOAN_SHARK = register("loan_shark",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.LoanSharkEntity>of(pl.pansage.gamblecraft.entity.LoanSharkEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.BartenderEntity> BARTENDER = register("bartender",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.BartenderEntity>of(pl.pansage.gamblecraft.entity.BartenderEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.WaitressEntity> WAITRESS = register("waitress",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.WaitressEntity>of(pl.pansage.gamblecraft.entity.WaitressEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.PickpocketEntity> PICKPOCKET = register("pickpocket",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.PickpocketEntity>of(pl.pansage.gamblecraft.entity.PickpocketEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.CasinoBossEntity> CASINO_BOSS = register("casino_boss",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.CasinoBossEntity>of(pl.pansage.gamblecraft.entity.CasinoBossEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.DebtCollectorEntity> DEBT_COLLECTOR = register("debt_collector",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.DebtCollectorEntity>of(pl.pansage.gamblecraft.entity.DebtCollectorEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<pl.pansage.gamblecraft.entity.MuggerEntity> MUGGER = register("mugger",
			EntityType.Builder.<pl.pansage.gamblecraft.entity.MuggerEntity>of(pl.pansage.gamblecraft.entity.MuggerEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F).eyeHeight(1.62F).clientTrackingRange(10));

	public static final EntityType<ScratchCardEntity> SCRATCH_CARD = register("scratch_card",
			EntityType.Builder.<ScratchCardEntity>of(ScratchCardEntity::new, MobCategory.MISC)
					.sized(0.7F, 0.06F).noSummon().clientTrackingRange(8).updateInterval(20));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, GambleCraft.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(STREET_DEALER, StreetDealerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(LOTTERY_CLERK, LotteryClerkEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CASHIER, CashierEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CASINO_DEALER, CasinoDealerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(BOUNCER, BouncerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(CASINO_GUARD, CasinoGuardEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ADDICTED_GAMBLER, pl.pansage.gamblecraft.entity.CasinoNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(LOAN_SHARK, pl.pansage.gamblecraft.entity.CasinoNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(BARTENDER, pl.pansage.gamblecraft.entity.CasinoNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(WAITRESS, pl.pansage.gamblecraft.entity.CasinoNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(PICKPOCKET, pl.pansage.gamblecraft.entity.CasinoNpc.createAttributes());
		FabricDefaultAttributeRegistry.register(CASINO_BOSS, pl.pansage.gamblecraft.entity.CasinoDealerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(DEBT_COLLECTOR, pl.pansage.gamblecraft.entity.DebtCollectorEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MUGGER, pl.pansage.gamblecraft.entity.MuggerEntity.createAttributes());
	}
}
