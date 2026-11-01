package pl.pansage.gamblecraft.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import pl.pansage.gamblecraft.GambleCraft;

/** GambleCraft's own sounds, synthesised by tools/gen_sounds.py. */
public final class ModSounds {
	public static final SoundEvent CHIP_CLINK = register("chip_clink");
	public static final SoundEvent REEL_STOP = register("reel_stop");
	public static final SoundEvent WHEEL_CLICK = register("wheel_click");
	public static final SoundEvent DICE = register("dice");
	public static final SoundEvent PLINKO_PEG = register("plinko_peg");
	public static final SoundEvent WIN = register("win");
	public static final SoundEvent BIG_WIN = register("big_win");
	public static final SoundEvent JACKPOT = register("jackpot");
	public static final SoundEvent COINS = register("coins");
	public static final SoundEvent ALARM = register("alarm");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = GambleCraft.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
