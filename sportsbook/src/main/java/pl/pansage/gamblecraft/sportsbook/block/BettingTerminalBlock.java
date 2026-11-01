package pl.pansage.gamblecraft.sportsbook.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import pl.pansage.gamblecraft.block.TallMachineBlock;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.sportsbook.network.SbNetworking;

/**
 * A two-block betting terminal: a cabinet with a coupon printer and a screen that scrolls the next games and live
 * scores. Using it opens the betting window.
 */
public class BettingTerminalBlock extends TallMachineBlock {
	public BettingTerminalBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && AgeGate.require(serverPlayer)) {
			SbNetworking.open(serverPlayer, lower(state, pos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BettingTerminalBlockEntity(pos, state);
	}
}
