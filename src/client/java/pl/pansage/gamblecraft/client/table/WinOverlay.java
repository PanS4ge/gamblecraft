package pl.pansage.gamblecraft.client.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * WIN! over the screen when the server says this player won: the word pops in, the amount counts up to what was
 * won, and for a big win or a jackpot paper confetti rains down the screen.
 */
public final class WinOverlay {
	private static final int[] COLOURS = {0xFFE03030, 0xFFF0C030, 0xFF30B050, 0xFF3070E0, 0xFFC040D0, 0xFFF08030, 0xFFFFFFFF};
	private static final int[] TITLE_COLOURS = {0xFFFFE070, 0xFFFF9A20, 0xFFFFD700};
	private static final RandomSource RANDOM = RandomSource.create();

	private static final class Flake {
		float x;
		float y;
		float vx;
		float vy;
		float phase;
		int colour;
		int size;
	}

	private static long amount;
	private static int tier;
	private static int age = -1;
	private static final List<Flake> FLAKES = new ArrayList<>();

	private WinOverlay() {
	}

	private static int duration() {
		return tier == 0 ? 50 : tier == 1 ? 80 : 110;
	}

	public static void show(long won, int newTier) {
		amount = won;
		tier = newTier;
		age = 0;
		FLAKES.clear();
		int count = tier == 0 ? 0 : tier == 1 ? 80 : 160;
		for (int i = 0; i < count; i++) {
			Flake f = new Flake();
			f.x = RANDOM.nextFloat();
			f.y = -RANDOM.nextFloat() * 0.6F;
			f.vx = (RANDOM.nextFloat() - 0.5F) * 0.004F;
			f.vy = 0.006F + RANDOM.nextFloat() * 0.008F;
			f.phase = RANDOM.nextFloat() * 6.28F;
			f.colour = COLOURS[RANDOM.nextInt(COLOURS.length)];
			f.size = 2 + RANDOM.nextInt(3);
			FLAKES.add(f);
		}
	}

	public static void tick() {
		if (age < 0) {
			return;
		}
		if (++age > duration()) {
			age = -1;
			FLAKES.clear();
			return;
		}
		for (Flake f : FLAKES) {
			f.x += f.vx + Mth.sin(f.phase + age * 0.15F) * 0.0012F;
			f.y += f.vy;
			f.phase += 0.25F;
		}
	}

	public static void draw(GuiGraphicsExtractor graphics, float partialTick) {
		if (age < 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		float t = age + partialTick;
		for (Flake f : FLAKES) {
			int x = (int) (f.x * w);
			int y = (int) (f.y * h);
			int width = Math.max(1, (int) Math.abs(Mth.cos(f.phase) * f.size));
			graphics.fill(x - width, y, x + width, y + f.size, f.colour);
		}
		// the word pops (too big, then settles); it all fades over the last half second
		float pop = t < 6 ? Mth.lerp(t / 6, 0.2F, 1.35F) : t < 10 ? Mth.lerp((t - 6) / 4, 1.35F, 1.0F) : 1.0F + 0.03F * Mth.sin(t * 0.4F);
		float fade = Mth.clamp((duration() - t) / 10.0F, 0, 1);
		int alpha = (int) (fade * 255) << 24;
		int colour = tier == 2 ? COLOURS[(age / 3) % COLOURS.length] : TITLE_COLOURS[tier];
		Component title = Component.translatable("gamblecraft.win.title." + tier);
		graphics.pose().pushMatrix();
		graphics.pose().translate(w / 2.0F, h * 0.3F);
		graphics.pose().scale(pop * (tier == 0 ? 2.5F : 3.2F), pop * (tier == 0 ? 2.5F : 3.2F));
		graphics.centeredText(mc.font, title, 0, -4, alpha | (colour & 0xFFFFFF));
		graphics.pose().popMatrix();
		float counted = Math.min(1, t / 25);
		long shown = Math.round(amount * (1 - (1 - counted) * (1 - counted)));
		Component line = Component.translatable("gamblecraft.win.amount", String.format(Locale.ROOT, "%,d", shown).replace(',', ' '));
		graphics.pose().pushMatrix();
		graphics.pose().translate(w / 2.0F, h * 0.3F + 22 + (tier == 0 ? 0 : 6));
		graphics.pose().scale(1.6F, 1.6F);
		graphics.centeredText(mc.font, line, 0, -4, alpha | 0x55FF55);
		graphics.pose().popMatrix();
	}
}
