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
import pl.pansage.gamblecraft.block.entity.SlotMachineBlockEntity;
import pl.pansage.gamblecraft.registry.ModBlockEntities;

/**
 * A two-block-high slot machine: cabinet below, reels and marquee above. Use it to pull the lever; sneak-use to
 * change the bet. The state lives on the lower half.
 */
public class SlotMachineBlock extends TallMachineBlock {
	public SlotMachineBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(lower(state, pos)) instanceof SlotMachineBlockEntity machine) {
			machine.use(serverPlayer, player.isShiftKeyDown());
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SlotMachineBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != ModBlockEntities.SLOT_MACHINE || state.getValue(HALF) == DoubleBlockHalf.UPPER ? null
				: (BlockEntityTicker<T>) (BlockEntityTicker<SlotMachineBlockEntity>) SlotMachineBlockEntity::serverTick;
	}
}
