package pl.pansage.gamblecraft.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import pl.pansage.gamblecraft.block.NpcMarkerBlock;
import pl.pansage.gamblecraft.entity.CasinoNpc;
import pl.pansage.gamblecraft.registry.ModBlockEntities;
import pl.pansage.gamblecraft.registry.ModEntities;

/** The marker's roll: {@code chance} per cent of a guest, facing {@code yaw}, then the block becomes {@code replace}. */
public class NpcMarkerBlockEntity extends BlockEntity {
	private int chance = 50;
	private float yaw;
	private String replace = "minecraft:air";

	public NpcMarkerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.NPC_MARKER, pos, state);
	}

	/** Sets the roll (structures set it from their block NBT). */
	public void configure(int chance, float yaw, String replace) {
		this.chance = chance;
		this.yaw = yaw;
		this.replace = replace;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, NpcMarkerBlockEntity marker) {
		if (level instanceof ServerLevel serverLevel) {
			marker.resolve(serverLevel, pos, state);
		}
	}

	private void resolve(ServerLevel level, BlockPos pos, BlockState state) {
		if (level.getRandom().nextInt(100) < chance) {
			EntityType<? extends CasinoNpc> type = switch (state.getValue(NpcMarkerBlock.NPC)) {
				case GAMBLER -> ModEntities.ADDICTED_GAMBLER;
				case PICKPOCKET -> ModEntities.PICKPOCKET;
				case WAITRESS -> ModEntities.WAITRESS;
			};
			CasinoNpc npc = type.create(level, EntitySpawnReason.STRUCTURE);
			if (npc != null) {
				npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
				npc.setYHeadRot(yaw);
				npc.setPersistenceRequired();
				npc.setHome(pos);
				level.addFreshEntity(npc);
			}
		}
		Identifier id = Identifier.tryParse(replace);
		Block block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.getValue(id);
		level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("chance", chance);
		output.putFloat("yaw", yaw);
		output.putString("replace", replace);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		chance = input.getIntOr("chance", 50);
		yaw = input.getFloatOr("yaw", 0);
		replace = input.getStringOr("replace", "minecraft:air");
	}
}
