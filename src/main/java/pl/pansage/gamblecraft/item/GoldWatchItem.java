package pl.pansage.gamblecraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A gambler's gold watch, handed over in thanks: use it to read the time. */
public class GoldWatchItem extends DescribedItem {
	public GoldWatchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer serverPlayer) {
			long time = serverPlayer.level().getOverworldClockTime();
			long ofDay = Math.floorMod(time, 24000L);
			int hours = (int) ((ofDay / 1000 + 6) % 24);
			int minutes = (int) (ofDay % 1000 * 60 / 1000);
			serverPlayer.sendOverlayMessage(Component.translatable("gamblecraft.watch.time", PromissoryNoteItem.day(time),
					String.format(java.util.Locale.ROOT, "%02d:%02d", hours, minutes)));
			level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.8F);
		}
		return InteractionResult.SUCCESS;
	}
}
