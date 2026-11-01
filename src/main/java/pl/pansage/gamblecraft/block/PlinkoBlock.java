package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import pl.pansage.gamblecraft.block.entity.PlinkoBlockEntity;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/** A plinko board, two blocks high. Use drops a ball; sneak-use the bottom to change the bet, the top to change the risk. */
public class PlinkoBlock extends TallMachineBlock {
	public PlinkoBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(lower(state, pos)) instanceof PlinkoBlockEntity board) {
			if (!player.isShiftKeyDown()) {
				board.drop(serverPlayer);
			} else if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
				board.cycleRisk(serverPlayer);
			} else {
				board.cycleBet(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PlinkoBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlockEntities.PLINKO || state.getValue(HALF) == DoubleBlockHalf.UPPER ? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<PlinkoBlockEntity>) PlinkoBlockEntity::serverTick;
	}
}
