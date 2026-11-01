package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import pl.pansage.gamblecraft.entity.LotteryClerkEntity;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * The lottery counter of a village exchange. Now and then villagers "play" here and throw their used tickets
 * on the ground around it (up to a few at a time), which is where the litter in villages comes from.
 */
public class KolekturaBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final int LITTER_RADIUS = 5;
	private static final int MAX_LITTER = 6;
	private static final int CLERK_RANGE = 12;

	public KolekturaBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		hireClerk(state, level, pos);
		if (random.nextInt(3) != 0) {
			return;
		}
		int litter = 0;
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-LITTER_RADIUS, -2, -LITTER_RADIUS), pos.offset(LITTER_RADIUS, 2, LITTER_RADIUS))) {
			if (level.getBlockState(p).is(ModBlocks.SCRATCH_LITTER)) {
				litter++;
			}
		}
		if (litter >= MAX_LITTER) {
			return;
		}
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos spot = pos.offset(random.nextInt(LITTER_RADIUS * 2 + 1) - LITTER_RADIUS, random.nextInt(3) - 1, random.nextInt(LITTER_RADIUS * 2 + 1) - LITTER_RADIUS);
			BlockState litterState = ScratchLitterBlock.randomState(random);
			if (level.getBlockState(spot).isAir() && litterState.canSurvive(level, spot)) {
				level.setBlockAndUpdate(spot, litterState);
				return;
			}
		}
	}

	/** A counter inside a village with nobody behind it gets a clerk, so a crafted counter works like a generated one. */
	private static void hireClerk(BlockState state, ServerLevel level, BlockPos pos) {
		if (!level.getEntitiesOfClass(LotteryClerkEntity.class, new AABB(pos).inflate(CLERK_RANGE)).isEmpty()
				|| VillageRegistry.nearestBell(level, pos).isEmpty()) {
			return;
		}
		BlockPos spot = pos.relative(state.getValue(FACING).getOpposite());
		if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
				|| !level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty()) {
			return;
		}
		LotteryClerkEntity clerk = ModEntities.LOTTERY_CLERK.create(level, EntitySpawnReason.STRUCTURE);
		if (clerk == null) {
			return;
		}
		float yaw = state.getValue(FACING).toYRot();
		clerk.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0);
		clerk.setYHeadRot(yaw);
		clerk.setYBodyRot(yaw);
		clerk.setPersistenceRequired();
		level.addFreshEntity(clerk);
	}
}
