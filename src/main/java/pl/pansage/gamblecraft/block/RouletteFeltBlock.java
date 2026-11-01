package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pansage.gamblecraft.block.entity.RouletteBlockEntity;
import pl.pansage.gamblecraft.core.casino.Roulette;
import pl.pansage.gamblecraft.core.casino.RouletteLayout;
import pl.pansage.gamblecraft.item.ChipItem;

/**
 * A piece of roulette felt. Three of them in a row behind a wheel make the betting table; then a chip used on
 * the felt is a bet on the field under it, and sneak-using the felt takes your chips back while bets are open.
 */
public class RouletteFeltBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** 0 when loose, else the felt's place counted from the wheel. */
	public static final IntegerProperty PART = IntegerProperty.create("part", 0, RouletteWheelBlock.FELTS);
	public static final double TOP = 14 / 16.0;
	private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 14, 16);

	public RouletteFeltBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(PART, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PART);
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
		if (!level.isClientSide() && state.getValue(PART) == 0 && !old.is(this)) {
			RouletteWheelBlock.tryAssemble(level, pos);
		}
	}

	public static BlockPos wheelOf(BlockState state, BlockPos pos) {
		return pos.relative(state.getValue(FACING), -state.getValue(PART));
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		if (state.getValue(PART) > 0) {
			RouletteWheelBlock.disassemble(level, wheelOf(state, pos));
		}
	}

	/** Board coordinates (u along the table from the wheel, v across towards the players) of a point on the felt. */
	public static double[] boardPoint(BlockPos wheel, Direction d, Vec3 point) {
		Direction side = d.getClockWise();
		Vec3 origin = Vec3.atBottomCenterOf(wheel).add(d.getStepX() * 0.5, 0, d.getStepZ() * 0.5);
		Vec3 rel = point.subtract(origin);
		double u = rel.x * d.getStepX() + rel.z * d.getStepZ();
		double v = rel.x * side.getStepX() + rel.z * side.getStepZ() + 0.5;
		return new double[] {u, v};
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (!(stack.getItem() instanceof ChipItem chip)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (state.getValue(PART) == 0) {
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.roulette.assemble"));
			return InteractionResult.SUCCESS_SERVER;
		}
		BlockPos wheel = wheelOf(state, pos);
		if (!(level.getBlockEntity(wheel) instanceof RouletteBlockEntity table)) {
			return InteractionResult.SUCCESS_SERVER;
		}
		double[] uv = boardPoint(wheel, state.getValue(FACING), hit.getLocation());
		Roulette.Bet bet = RouletteLayout.betAt(uv[0], uv[1]);
		if (bet == null) {
			return InteractionResult.SUCCESS_SERVER;
		}
		if (table.placeBet(serverPlayer, bet, chip.value())) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && state.getValue(PART) > 0
				&& level.getBlockEntity(wheelOf(state, pos)) instanceof RouletteBlockEntity table) {
			if (player.isShiftKeyDown()) {
				table.takeBack(serverPlayer);
			} else {
				serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.roulette.how_to"));
			}
		}
		return InteractionResult.SUCCESS;
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
