package pl.pansage.gamblecraft.client.table;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.entity.CardTableEntity;
import pl.pansage.gamblecraft.entity.ScratchCardEntity;

/**
 * Overhead view of a card table or a scratch card: the camera glides from the player's eyes to a spot above their
 * side of it and looks down; it glides back when released. Uses a client-only marker as the camera entity,
 * so the player's own model is visible at the table.
 */
public final class TableCamera {
	private static final int GLIDE_TICKS = 12;
	private static Marker camera;
	private static int tableId = -1;
	private static int age;
	private static boolean leaving;
	private static Vec3 startPos;
	private static float startYaw;
	private static float startPitch;

	private TableCamera() {
	}

	public static boolean isActive() {
		return camera != null;
	}

	public static void activate(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (camera == null) {
			camera = (Marker) EntityTypes.MARKER.create(mc.level, EntitySpawnReason.LOAD);
			if (camera == null) {
				return;
			}
			startPos = mc.player.getEyePosition();
			startYaw = mc.player.getYRot();
			startPitch = mc.player.getXRot();
			camera.snapTo(startPos.x, startPos.y, startPos.z, startYaw, startPitch);
			mc.setCameraEntity(camera);
			age = 0;
		} else if (leaving) {
			startPos = camera.position();
			startYaw = camera.getYRot();
			startPitch = camera.getXRot();
			age = 0;
		}
		tableId = target.getId();
		leaving = false;
	}

	/** Starts gliding back to the player's eyes; the camera is handed back when the glide ends. */
	public static void release() {
		if (camera != null && !leaving) {
			leaving = true;
			age = 0;
			startPos = camera.position();
			startYaw = camera.getYRot();
			startPitch = camera.getXRot();
		}
	}

	private static void restore() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.setCameraEntity(mc.player);
		}
		camera = null;
		tableId = -1;
		leaving = false;
	}

	public static void tick() {
		if (camera == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Entity table = mc.level == null ? null : mc.level.getEntity(tableId);
		if (mc.player == null || mc.level == null || (table == null && !leaving)) {
			restore();
			return;
		}
		age++;
		float t = Mth.clamp(age / (float) GLIDE_TICKS, 0, 1);
		float e = t * t * (3 - 2 * t);
		Vec3 targetPos;
		float targetYaw;
		float targetPitch;
		if (leaving) {
			targetPos = mc.player.getEyePosition();
			targetYaw = mc.player.getYRot();
			targetPitch = mc.player.getXRot();
		} else if (table instanceof ScratchCardEntity card) {
			// Nearly straight down onto the card, from just in front of its bottom edge.
			double rad = Math.toRadians(card.facing());
			Vec3 towardsPlayer = new Vec3(Math.sin(rad), 0, -Math.cos(rad));
			targetPos = card.position().add(towardsPlayer.scale(0.3)).add(0, 0.82, 0);
			targetYaw = card.facing();
			targetPitch = 70;
		} else {
			float facing = ((CardTableEntity) table).facing();
			// Look from the player's side of the table towards the dealer, high enough to see every seat.
			double rad = Math.toRadians(facing);
			Vec3 towardsDealer = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
			targetPos = table.position().add(towardsDealer.scale(-0.6)).add(0, 0.95, 0);
			targetYaw = facing;
			targetPitch = 64;
		}
		camera.setOldPosAndRot();
		Vec3 pos = startPos.lerp(targetPos, e);
		camera.setPos(pos.x, pos.y, pos.z);
		camera.setYRot(startYaw + Mth.wrapDegrees(targetYaw - startYaw) * e);
		camera.setXRot(Mth.lerp(e, startPitch, targetPitch));
		if (leaving && t >= 1) {
			restore();
		}
	}
}
