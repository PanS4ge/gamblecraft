package pl.pansage.gamblecraft.block;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.pansage.gamblecraft.game.AgeGate;
import pl.pansage.gamblecraft.game.ChipCurrency;
import pl.pansage.gamblecraft.item.ScratchCardItem;
import pl.pansage.gamblecraft.network.CasinoPayloads.OpenKiosk;
import pl.pansage.gamblecraft.registry.ModBlocks;
import pl.pansage.gamblecraft.registry.ModItems;

/** The casino's scratch-card kiosk: cards for chips, scratched like village ones, paid in chips by the cashier. */
public class ScratchKioskBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private static final Item[] CARDS = {ModItems.SCRATCH_CARD_BRONZE, ModItems.SCRATCH_CARD_SILVER, ModItems.SCRATCH_CARD_GOLD};
	private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

	public ScratchKioskBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer && AgeGate.require(serverPlayer)) {
			ServerPlayNetworking.send(serverPlayer, new OpenKiosk(pos));
		}
		return InteractionResult.SUCCESS;
	}

	/** Sells one casino card of {@code tier} to a player standing at the kiosk. */
	public static void buy(ServerPlayer player, BlockPos pos, int tier) {
		if (tier < 0 || tier >= CARDS.length || !AgeGate.require(player) || !player.level().getBlockState(pos).is(ModBlocks.SCRATCH_KIOSK)
				|| player.position().distanceTo(Vec3.atCenterOf(pos)) > 8) {
			return;
		}
		int price = ScratchCardItem.Tier.values()[tier].chips();
		if (!ChipCurrency.INSTANCE.withdraw(player, price)) {
			player.sendOverlayMessage(Component.translatable("gamblecraft.slots.no_chips", price));
			return;
		}
		pl.pansage.gamblecraft.game.PlayerLedger.get(player.level().getServer()).staked(player.getUUID(), price);
		ItemStack card = ScratchCardItem.casino(CARDS[tier], player.level().getRandom().nextLong());
		if (!player.getInventory().add(card)) {
			player.drop(card, false, Prediction.SERVER_ONLY);
		}
		player.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.3F);
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
