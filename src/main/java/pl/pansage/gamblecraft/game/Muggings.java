package pl.pansage.gamblecraft.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import pl.pansage.gamblecraft.entity.MuggerEntity;
import pl.pansage.gamblecraft.entity.ThugEntity;
import pl.pansage.gamblecraft.registry.ModEntities;

/**
 * Word gets round when someone wins big. A player who walks out of a casino at least {@link GambleConfig#muggingThreshold}
 * chips up may be followed: a little way down the road a gang of muggers, as well armed as the player, comes for the
 * winnings.
 */
public final class Muggings {
	/** How far outside the casino the gang strikes (or after {@link #WAIT} ticks, whichever comes first). */
	private static final int STRIKE_DISTANCE = 20;
	private static final long WAIT = 600;

	private record Tail(long winnings, BoundingBox casino, long since) {
	}

	private static final Map<UUID, BoundingBox> INSIDE = new HashMap<>();
	private static final Map<UUID, Tail> FOLLOWED = new HashMap<>();

	private Muggings() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level == level.getServer().overworld() && level.getGameTime() % 20 == 13) {
				tick(level);
			}
		});
	}

	/** The chance a winner of {@code winnings} is followed: about a third at the threshold, rising to four in five (configurable). */
	public static double chance(long winnings) {
		return winnings < GambleConfig.get().muggingThreshold ? 0 : Math.min(GambleConfig.get().muggingMaxChance, 0.3 + winnings / 20000.0);
	}

	/** How many come: two, three from 5000, four from 10000. */
	public static int gangSize(long winnings) {
		return 2 + (int) Math.min(2, winnings / 5000);
	}

	private static void tick(ServerLevel level) {
		PlayerLedger ledger = PlayerLedger.get(level.getServer());
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			UUID id = player.getUUID();
			var casino = CasinoRegion.find(level, player.blockPosition());
			BoundingBox was = INSIDE.get(id);
			if (casino.isPresent()) {
				INSIDE.put(id, casino.get().box());
				FOLLOWED.remove(id);
			} else if (was != null) {
				INSIDE.remove(id);
				long winnings = ledger.closeVisit(id);
				if (!player.isCreative() && !player.isSpectator() && level.getRandom().nextDouble() < chance(winnings)) {
					FOLLOWED.put(id, new Tail(winnings, was, now));
				}
			}
			Tail tail = FOLLOWED.get(id);
			if (tail != null && (!tail.casino().inflatedBy(STRIKE_DISTANCE).isInside(player.blockPosition()) || now - tail.since() >= WAIT)) {
				FOLLOWED.remove(id);
				ambush(level, player, tail.winnings());
			}
		}
		INSIDE.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		FOLLOWED.keySet().removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
	}

	/** The gang strikes: muggers behind the player, kitted out like them, after {@code winnings}. */
	public static void ambush(ServerLevel level, ServerPlayer player, long winnings) {
		if (ChipCurrency.INSTANCE.balance(player) <= 0) {
			return;
		}
		for (MuggerEntity mugger : ThugEntity.send(level, player, ModEntities.MUGGER, gangSize(winnings))) {
			mugger.equipLike(player, winnings);
		}
		player.sendSystemMessage(Component.translatable("gamblecraft.mugger.ambush").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		level.playSound(null, player.blockPosition(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.9F);
	}
}
