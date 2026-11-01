package pl.pansage.gamblecraft.client.table;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Screen effects sent by the server: the camera shakes when a jackpot goes off nearby; the casino alarm flashes red. */
public final class ClientEffects {
	/** Client ticks until the red alarm flashing fades (the server repeats it every second while it lasts). */
	private static int alarmTicks;
	private static int shakeTicks;
	private static int shakeTotal;
	private static float shakeStrength;
	private static final RandomSource RANDOM = RandomSource.create();

	private ClientEffects() {
	}

	public static void shake(int ticks, float strength) {
		shakeTicks = shakeTotal = Math.max(1, ticks);
		shakeStrength = strength;
	}

	public static void alarm(boolean on) {
		alarmTicks = on ? 30 : 0;
	}

	/** Red edges pulsing round the screen while the alarm sounds. */
	public static void drawAlarm(GuiGraphicsExtractor graphics, float partialTick) {
		if (alarmTicks <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float time = (mc.level == null ? 0 : mc.level.getGameTime()) + partialTick;
		float pulse = 0.5F + 0.5F * Mth.sin(time * 0.31F);
		int alpha = (int) (Math.min(1.0F, alarmTicks / 10.0F) * (60 + 90 * pulse));
		int red = alpha << 24 | 0xD01010;
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		int edge = Math.max(12, h / 6);
		graphics.fillGradient(0, 0, w, edge, red, 0x00D01010);
		graphics.fillGradient(0, h - edge, w, h, 0x00D01010, red);
	}

	public static void tick() {
		if (alarmTicks > 0) {
			alarmTicks--;
		}
		Minecraft mc = Minecraft.getInstance();
		if (shakeTicks <= 0 || mc.player == null) {
			return;
		}
		float fade = shakeStrength * shakeTicks / (float) shakeTotal;
		mc.player.setYRot(mc.player.getYRot() + (RANDOM.nextFloat() - 0.5F) * 2.5F * fade);
		mc.player.setXRot(mc.player.getXRot() + (RANDOM.nextFloat() - 0.5F) * 2.0F * fade);
		shakeTicks--;
	}
}
