package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pansage.gamblecraft.block.entity.NpcMarkerBlockEntity;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/**
 * An invisible spot in a structure where a casino guest may turn up: on its first tick it rolls its chance, puts the
 * guest there (or not) and becomes the block it stood in for (a carpet).
 */
public class NpcMarkerBlock extends Block implements EntityBlock {
	public enum Npc implements StringRepresentable {
		GAMBLER, PICKPOCKET, WAITRESS;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static final EnumProperty<Npc> NPC = EnumProperty.create("npc", Npc.class);

	public NpcMarkerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NPC, Npc.GAMBLER));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NPC);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NpcMarkerBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlockEntities.NPC_MARKER ? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<NpcMarkerBlockEntity>) NpcMarkerBlockEntity::serverTick;
	}
}
