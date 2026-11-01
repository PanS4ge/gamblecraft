package pl.pansage.gamblecraft.client.scratch;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import pl.pansage.gamblecraft.client.render.CardTableRenderer;
import pl.pansage.gamblecraft.core.casino.ScratchFoil;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;

/**
 * Where things are on a scratch card lying on a block. The card is laid out like the panel screen, in grid units:
 * {@link #PANEL_W} x {@link #PANEL_H} with the 3x3 grid at ({@link #GRID_LEFT}, {@link #GRID_TOP}). In card space
 * +x is the card's right and +z its bottom edge, which faces the player who laid it.
 */
public final class ScratchGeometry {
	public static final int GRID_LEFT = 14;
	public static final int GRID_TOP = 40;
	public static final int PANEL_W = ScratchFoil.SIZE + 28;
	public static final int PANEL_H = ScratchFoil.SIZE + 74;
	/** Blocks per grid unit. */
	public static final float SCALE = 0.82F / PANEL_H;

	private ScratchGeometry() {
	}

	/** Card space (blocks) of a panel point. */
	public static double localX(double panelX) {
		return (panelX - PANEL_W / 2.0) * SCALE;
	}

	public static double localZ(double panelY) {
		return (panelY - PANEL_H / 2.0) * SCALE;
	}

	/** World position of a grid point on the card's surface. */
	public static Vec3 toWorld(ScratchCardEntity card, double gx, double gy, float partialTick) {
		Vec3 offset = CardTableRenderer.toWorld(card.facing(), localX(GRID_LEFT + gx), 0, localZ(GRID_TOP + gy));
		return card.getPosition(partialTick).add(offset);
	}

	/** Grid point under a world point (the y is ignored). */
	public static double[] toGrid(ScratchCardEntity card, Vec3 world) {
		Vec3 local = CardTableRenderer.toTable(card.facing(), world.subtract(card.position()));
		return new double[] {local.x / SCALE + PANEL_W / 2.0 - GRID_LEFT, local.z / SCALE + PANEL_H / 2.0 - GRID_TOP};
	}

	/** True if a grid point lies on the card itself (not beside it). */
	public static boolean onCard(double gx, double gy) {
		return gx > -GRID_LEFT && gx < PANEL_W - GRID_LEFT && gy > -GRID_TOP && gy < PANEL_H - GRID_TOP;
	}

	/** The grid point under the mouse (GUI coordinates), or null if the ray misses the card's plane. */
	public static double[] pick(ScratchCardEntity card, double mouseX, double mouseY, int width, int height) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		double tan = Math.tan(Math.toRadians(camera.getFov()) / 2);
		double aspect = width / (double) height;
		double nx = mouseX / width * 2 - 1;
		double ny = 1 - mouseY / height * 2;
		Vector3fc f = camera.forwardVector();
		Vector3fc u = camera.upVector();
		Vector3fc l = camera.leftVector();
		double dx = f.x() + u.x() * ny * tan - l.x() * nx * tan * aspect;
		double dy = f.y() + u.y() * ny * tan - l.y() * nx * tan * aspect;
		double dz = f.z() + u.z() * ny * tan - l.z() * nx * tan * aspect;
		Vec3 eye = camera.position();
		double t = (card.getY() - eye.y) / dy;
		if (dy >= 0 || t <= 0) {
			return null;
		}
		return toGrid(card, new Vec3(eye.x + dx * t, card.getY(), eye.z + dz * t));
	}

	/** GUI coordinates of a grid point, the inverse of {@link #pick}. */
	public static double[] project(ScratchCardEntity card, double gx, double gy, int width, int height) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		double tan = Math.tan(Math.toRadians(camera.getFov()) / 2);
		double aspect = width / (double) height;
		Vec3 v = toWorld(card, gx, gy, 1.0F).subtract(camera.position());
		Vector3fc f = camera.forwardVector();
		Vector3fc u = camera.upVector();
		Vector3fc l = camera.leftVector();
		double z = v.x * f.x() + v.y * f.y() + v.z * f.z();
		double y = v.x * u.x() + v.y * u.y() + v.z * u.z();
		double x = -(v.x * l.x() + v.y * l.y() + v.z * l.z());
		double nx = x / (z * tan * aspect);
		double ny = y / (z * tan);
		return new double[] {(nx + 1) / 2 * width, (1 - ny) / 2 * height};
	}
}
