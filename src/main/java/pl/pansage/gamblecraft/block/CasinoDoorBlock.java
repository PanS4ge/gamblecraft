package pl.pansage.gamblecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import pl.pansage.gamblecraft.game.CasinoSecurity;
import pl.pansage.gamblecraft.game.Suit;

/**
 * The casino's front door: it opens only for someone in a full suit, closes itself again after a moment and
 * ignores redstone, so nobody walks in underdressed.
 */
public class CasinoDoorBlock extends DoorBlock {
	private static final int CLOSE_AFTER = 50;

	public CasinoDoorBlock(Properties properties) {
		super(BlockSetType.DARK_OAK, properties);
	}

	/** Why this door stays shut for {@code player} on either side (null: it opens). */
	protected String refusal(Player player) {
		return player.isCreative() || Suit.isWearing(player) ? null : "gamblecraft.casino.dress_code";
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		String shut = refusal(player);
		if (shut != null) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable(shut));
				level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
			}
			return InteractionResult.SUCCESS;
		}
		if (!player.isCreative() && level instanceof ServerLevel serverLevel) {
			CasinoSecurity security = CasinoSecurity.get(serverLevel.getServer());
			String refusal = security.lockedDown(serverLevel, pos) ? "gamblecraft.security.lockdown"
					: security.banned(serverLevel, player) ? "gamblecraft.casino.banned" : null;
			if (refusal != null) {
				player.sendOverlayMessage(Component.translatable(refusal));
				level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.7F);
				return InteractionResult.SUCCESS;
			}
		}
		InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
		BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		if (!level.isClientSide() && level.getBlockState(lower).is(this) && level.getBlockState(lower).getValue(OPEN)) {
			level.scheduleTick(lower, this, CLOSE_AFTER);
		}
		return result;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.is(this) && state.getValue(OPEN)) {
			setOpen(null, level, state, pos, false);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
		// no redstone: the door answers to the dress code only
	}
}
