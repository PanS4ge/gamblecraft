package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pansage.gamblecraft.core.casino.ScratchCards;
import pl.pansage.gamblecraft.game.VillageRegistry;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.registry.ModItems;

/**
 * A scratch card thrown on the ground. Picking it up usually gives a used (losing) ticket, but now and then
 * somebody threw away one they never scratched, worth a small prize at this village's clerk.
 */
public class ScratchLitterBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 0.5, 14);
	private static final float LUCKY_CHANCE = 0.12F;

	public ScratchLitterBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(VARIANT, 0));
	}

	public static BlockState randomState(RandomSource random) {
		return pl.pansage.gamblecraft.registry.ModBlocks.SCRATCH_LITTER.defaultBlockState()
				.setValue(FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random))
				.setValue(VARIANT, random.nextInt(3));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, VARIANT);
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.6F, 1.6F);
		ItemStack found = level.getRandom().nextFloat() < LUCKY_CHANCE ? luckyTicket(serverLevel) : new ItemStack(ModItems.USED_SCRATCH_CARD);
		if (!player.getInventory().add(found)) {
			Block.popResource(level, pos, found);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** An unscratched bronze card whose prize is the smallest or second-smallest one. */
	private static ItemStack luckyTicket(ServerLevel level) {
		long secret = VillageRegistry.get(level.getServer()).secret();
		RandomSource random = level.getRandom();
		for (int attempt = 0; attempt < 2000; attempt++) {
			long seed = random.nextLong();
			ScratchCards.Prize prize = ScratchCards.generate(seed ^ secret).prize();
			if (prize == ScratchCards.Prize.CLOVER || prize == ScratchCards.Prize.HORSESHOE) {
				return ScratchCardItem.unbound(ModItems.SCRATCH_CARD_BRONZE, seed);
			}
		}
		return new ItemStack(ModItems.USED_SCRATCH_CARD);
	}
}
