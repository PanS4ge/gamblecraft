package pl.pansage.gamblecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import pl.pansage.gamblecraft.GambleCraft;

/** Small drawing helpers shared by the casino machine renderers: tinted quads, boxes and floating text. */
public final class Shapes {
	public static final Identifier WHITE = GambleCraft.id("textures/misc/white.png");
	public static final int FULL_BRIGHT = 0xF000F0;

	private Shapes() {
	}

	public static void vertex(PoseStack.Pose p, VertexConsumer v, float x, float y, float z, float u, float uv, int argb, int light, float nx, float ny,
			float nz) {
		v.addVertex(p, x, y, z)
				.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24)
				.setUv(u, uv)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(p, nx, ny, nz);
	}

	/** A rectangle in the x-y plane at depth z, facing +z; uv covers the whole texture. */
	public static void wall(PoseStack.Pose p, VertexConsumer v, float x0, float y0, float x1, float y1, float z, int argb, int light) {
		vertex(p, v, x0, y0, z, 0, 1, argb, light, 0, 0, 1);
		vertex(p, v, x1, y0, z, 1, 1, argb, light, 0, 0, 1);
		vertex(p, v, x1, y1, z, 1, 0, argb, light, 0, 0, 1);
		vertex(p, v, x0, y1, z, 0, 0, argb, light, 0, 0, 1);
	}

	/** A rectangle lying flat at height y, facing up. */
	public static void floor(PoseStack.Pose p, VertexConsumer v, float x0, float z0, float x1, float z1, float y, int argb, int light) {
		vertex(p, v, x0, y, z0, 0, 0, argb, light, 0, 1, 0);
		vertex(p, v, x0, y, z1, 0, 1, argb, light, 0, 1, 0);
		vertex(p, v, x1, y, z1, 1, 1, argb, light, 0, 1, 0);
		vertex(p, v, x1, y, z0, 1, 0, argb, light, 0, 1, 0);
	}

	/** A box centred on (cx, cz), from y to y + h. */
	public static void box(PoseStack.Pose p, VertexConsumer v, float cx, float y, float cz, float w, float h, float d, int argb, int light) {
		float x0 = cx - w / 2;
		float x1 = cx + w / 2;
		float z0 = cz - d / 2;
		float z1 = cz + d / 2;
		float y1 = y + h;
		quad(p, v, x0, y, z1, x1, y, z1, x1, y1, z1, x0, y1, z1, argb, light);
		quad(p, v, x1, y, z0, x0, y, z0, x0, y1, z0, x1, y1, z0, argb, light);
		quad(p, v, x0, y, z0, x0, y, z1, x0, y1, z1, x0, y1, z0, argb, light);
		quad(p, v, x1, y, z1, x1, y, z0, x1, y1, z0, x1, y1, z1, argb, light);
		quad(p, v, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, argb, light);
		quad(p, v, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, argb, light);
	}

	public static void quad(PoseStack.Pose p, VertexConsumer v, float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz, int argb, int light) {
		vertex(p, v, ax, ay, az, 0, 1, argb, light, 0, 1, 0);
		vertex(p, v, bx, by, bz, 1, 1, argb, light, 0, 1, 0);
		vertex(p, v, cx, cy, cz, 1, 0, argb, light, 0, 1, 0);
		vertex(p, v, dx, dy, dz, 0, 0, argb, light, 0, 1, 0);
	}

	/** A filled circle in the x-y plane at depth z, facing +z. */
	public static void disc(PoseStack.Pose p, VertexConsumer v, float cx, float cy, float z, float r, int argb, int light) {
		int steps = 12;
		for (int s = 0; s < steps; s += 2) {
			double a0 = Math.PI * 2 * s / steps;
			double a1 = Math.PI * 2 * (s + 1) / steps;
			double a2 = Math.PI * 2 * (s + 2) / steps;
			vertex(p, v, cx, cy, z, 0, 0, argb, light, 0, 0, 1);
			vertex(p, v, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, z, 0, 1, argb, light, 0, 0, 1);
			vertex(p, v, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, z, 1, 1, argb, light, 0, 0, 1);
			vertex(p, v, cx + (float) Math.cos(a2) * r, cy + (float) Math.sin(a2) * r, z, 1, 0, argb, light, 0, 0, 1);
		}
	}

	/** Text floating at (x, y, z) and turned to the viewer, with a dark backing. */
	public static void billboard(Font font, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Component text,
			double x, double y, double z, float scale, int color) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.rotate(camera.orientation);
		pose.scale(scale, -scale, scale);
		var line = text.getVisualOrderText();
		collector.submitText(pose, -font.width(line) / 2.0F, 0, line, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0x90000000, 0);
		pose.popPose();
	}

	/** Text on a surface facing +z (after the caller's rotation), centred on (x, y). */
	public static void label(Font font, PoseStack pose, SubmitNodeCollector collector, Component text, float x, float y, float z, float scale, int color,
			int light) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.scale(scale, -scale, scale);
		var line = text.getVisualOrderText();
		collector.submitText(pose, -font.width(line) / 2.0F, -4, line, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
		pose.popPose();
	}
}
