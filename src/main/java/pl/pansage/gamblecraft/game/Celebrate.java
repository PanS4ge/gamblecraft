package pl.pansage.gamblecraft.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.network.CasinoPayloads.WinFlash;
import pl.pansage.gamblecraft.registry.ModParticles;
import pl.pansage.gamblecraft.registry.ModSounds;

/**
 * How the casino marks a win (at least the stake back): the winner sees WIN! with the amount counting up; a big win
 * (ten times the stake, or a thousand chips) adds a fanfare, confetti and glitter everyone around can see and hear;
 * a jackpot the full show.
 */
public final class Celebrate {
	public static final int WIN = 0;
	public static final int BIG_WIN = 1;
	public static final int JACKPOT = 2;
	private static final int[] COLOURS = {0xE03030, 0xF0C030, 0x30B050, 0x3070E0, 0xC040D0, 0xF08030, 0xFFFFFF};

	private Celebrate() {
	}

	public static int tier(long won, long staked, boolean jackpot) {
		return jackpot ? JACKPOT : won >= Math.max(1000, staked * 10) ? BIG_WIN : WIN;
	}

	/** {@code player} won {@code won} chips on a stake of {@code staked} at {@code at}. */
	public static void win(ServerLevel level, ServerPlayer player, long won, long staked, Vec3 at, boolean jackpot) {
		if (won <= 0) {
			return;
		}
		if (won < staked && !jackpot) {
			// less back than was staked is no win: just the chips sliding over
			level.playSound(null, BlockPos.containing(at), ModSounds.CHIP_CLINK, SoundSource.BLOCKS, 0.7F, 1.0F);
			return;
		}
		int tier = tier(won, staked, jackpot);
		if (player != null) {
			ServerPlayNetworking.send(player, new WinFlash(won, tier));
		}
		BlockPos pos = BlockPos.containing(at);
		switch (tier) {
			case JACKPOT -> {
				level.playSound(null, pos, ModSounds.JACKPOT, SoundSource.BLOCKS, 2.0F, 1.0F);
				level.playSound(null, pos, ModSounds.COINS, SoundSource.BLOCKS, 1.2F, 1.0F);
				confetti(level, at.add(0, 1.2, 0), 160, 1.2);
				glitter(level, at.add(0, 0.8, 0), 60, 1.0);
			}
			case BIG_WIN -> {
				level.playSound(null, pos, ModSounds.BIG_WIN, SoundSource.BLOCKS, 1.4F, 1.0F);
				level.playSound(null, pos, ModSounds.COINS, SoundSource.BLOCKS, 0.8F, 1.1F);
				confetti(level, at.add(0, 1.0, 0), 70, 0.8);
				glitter(level, at.add(0, 0.6, 0), 25, 0.6);
			}
			default -> level.playSound(null, pos, ModSounds.WIN, SoundSource.BLOCKS, 0.9F, 1.0F);
		}
	}

	/** Paper flakes of every colour, fluttering down from {@code at}. */
	public static void confetti(ServerLevel level, Vec3 at, int count, double spread) {
		for (int colour : COLOURS) {
			level.sendParticles(ColorParticleOption.create(ModParticles.CONFETTI, colour), at.x, at.y, at.z, Math.max(1, count / COLOURS.length),
					spread, spread * 0.4, spread, 0.0);
		}
	}

	public static void glitter(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ModParticles.GOLD_GLITTER, at.x, at.y, at.z, count, spread, spread * 0.6, spread, 0.02);
	}
}
