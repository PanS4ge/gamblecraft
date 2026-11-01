package pl.pansage.gamblecraft.sportsbook.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.pansage.gamblecraft.sportsbook.registry.SbRegistry;

/** Holds nothing: it is there so the screen can be drawn; what it shows comes from the feed every player gets. */
public class BettingTerminalBlockEntity extends BlockEntity {
	public BettingTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(SbRegistry.BETTING_TERMINAL_ENTITY, pos, state);
	}
}
