package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.registry.ModBlockEntities;
import pl.pansage.gamblecraft.registry.ModBlocks;

/**
 * The roulette wheel. Put three felts in a row behind it and the table assembles itself; the wheel then runs the
 * game. Taking any piece away takes the table apart again and returns every chip on it.
 */
public class RouletteWheelBlock extends Block implements EntityBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty ASSEMBLED = BooleanProperty.create("assembled");
	public static final int FELTS = 3;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

	public RouletteWheelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ASSEMBLED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ASSEMBLED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		super.onPlace(state, level, pos, old, movedByPiston);
		if (!level.isClientSide() && !state.getValue(ASSEMBLED)) {
			tryAssemble(level, pos);
		}
	}

	/** Looks for a loose wheel with three loose felts in a row through {@code changed}, and joins them up. */
	public static void tryAssemble(Level level, BlockPos changed) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int k = 0; k <= FELTS; k++) {
				BlockPos wheel = changed.relative(d, -k);
				BlockState state = level.getBlockState(wheel);
				if (!state.is(ModBlocks.ROULETTE_WHEEL) || state.getValue(ASSEMBLED)) {
					continue;
				}
				boolean row = true;
				for (int i = 1; i <= FELTS && row; i++) {
					BlockState felt = level.getBlockState(wheel.relative(d, i));
					row = felt.is(ModBlocks.ROULETTE_FELT) && felt.getValue(RouletteFeltBlock.PART) == 0;
				}
				if (row) {
					assemble(level, wheel, d);
					return;
				}
			}
		}
	}

	private static void assemble(Level level, BlockPos wheel, Direction d) {
		level.setBlock(wheel, level.getBlockState(wheel).setValue(FACING, d).setValue(ASSEMBLED, true), 3);
		for (int i = 1; i <= FELTS; i++) {
			BlockPos pos = wheel.relative(d, i);
			level.setBlock(pos, level.getBlockState(pos).setValue(RouletteFeltBlock.FACING, d).setValue(RouletteFeltBlock.PART, i), 3);
		}
		if (level.getBlockEntity(wheel) instanceof RouletteBlockEntity table) {
			table.assembled(level.getGameTime());
		}
		level.playSound(null, wheel, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.playSound(null, wheel, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
		if (level instanceof ServerLevel serverLevel) {
			for (int i = 0; i <= FELTS; i++) {
				BlockPos pos = wheel.relative(d, i);
				serverLevel.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.02);
			}
		}
	}

	/** Loosens every piece of the table around this wheel and returns the chips on it. */
	public static void disassemble(Level level, BlockPos wheel) {
		BlockState state = level.getBlockState(wheel);
		Direction d = state.is(ModBlocks.ROULETTE_WHEEL) ? state.getValue(FACING) : null;
		if (d == null) {
			return;
		}
		if (level.getBlockEntity(wheel) instanceof RouletteBlockEntity table) {
			table.refundAll();
		}
		if (state.getValue(ASSEMBLED)) {
			level.setBlock(wheel, state.setValue(ASSEMBLED, false), 3);
		}
		for (int i = 1; i <= FELTS; i++) {
			BlockPos pos = wheel.relative(d, i);
			BlockState felt = level.getBlockState(pos);
			if (felt.is(ModBlocks.ROULETTE_FELT) && felt.getValue(RouletteFeltBlock.PART) == i) {
				level.setBlock(pos, felt.setValue(RouletteFeltBlock.PART, 0), 3);
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (state.getValue(ASSEMBLED)) {
			Direction d = state.getValue(FACING);
			for (int i = 1; i <= FELTS; i++) {
				BlockPos felt = pos.relative(d, i);
				BlockState feltState = level.getBlockState(felt);
				if (feltState.is(ModBlocks.ROULETTE_FELT) && feltState.getValue(RouletteFeltBlock.PART) == i) {
					level.setBlock(felt, feltState.setValue(RouletteFeltBlock.PART, 0), 3);
				}
			}
		}
	}

	/** Using the wheel tells you how the round stands. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof RouletteBlockEntity table) {
			if (!state.getValue(ASSEMBLED)) {
				serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.roulette.assemble"));
			} else {
				long seconds = (table.ticksLeft(level.getGameTime()) + 19) / 20;
				serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.roulette.status." + table.phase().name().toLowerCase(java.util.Locale.ROOT), seconds));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RouletteBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlockEntities.ROULETTE ? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<RouletteBlockEntity>) RouletteBlockEntity::serverTick;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
