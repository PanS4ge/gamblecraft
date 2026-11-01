package pl.pansage.gamblecraft.registry;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.pansage.gamblecraft.GambleCraft;

/** Confetti (tinted paper flakes that flutter down) and gold glitter for wins. */
public final class ModParticles {
	public static final ParticleType<ColorParticleOption> CONFETTI = Registry.register(BuiltInRegistries.PARTICLE_TYPE, GambleCraft.id("confetti"),
			FabricParticleTypes.complex(ColorParticleOption::codec, ColorParticleOption::streamCodec));
	public static final SimpleParticleType GOLD_GLITTER = Registry.register(BuiltInRegistries.PARTICLE_TYPE, GambleCraft.id("gold_glitter"),
			FabricParticleTypes.simple());

	private ModParticles() {
	}

	public static void init() {
	}
}
