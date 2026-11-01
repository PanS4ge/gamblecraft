package pl.pansage.gamblecraft.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;

/** The Luck effect (champagne, the lucky coin) tips the casino games a little: one per cent more on every win. */
public final class Luck {
	public static final double EDGE = GambleConfig.get().luckBonusPercent / 100.0;

	private Luck() {
	}

	/** The lucky coin, held in the off hand, keeps Luck on its holder. */
	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 20 != 0) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (player.getOffhandItem().is(pl.pansage.gamblecraft.registry.ModItems.LUCKY_COIN)) {
					player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.LUCK, 60, 0, true, false, true));
				}
			}
		});
	}

	/** Extra chips for a lucky winner; the fraction of a chip is paid by chance, so the average is exactly one per cent. */
	public static long bonus(ServerPlayer player, long won, RandomSource random) {
		if (won <= 0 || player == null || !player.hasEffect(MobEffects.LUCK)) {
			return 0;
		}
		return (long) Math.floor(won * EDGE + random.nextDouble());
	}
}
