package pl.pansage.gamblecraft.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pl.pansage.gamblecraft.entity.CasinoGuardEntity;
import pl.pansage.gamblecraft.network.CasinoPayloads.Shake;

/**
 * The guards' sidearm: a sonic shot along the line of sight, up to 16 blocks. Whatever it hits takes a little damage
 * and is slammed down from any height (an elytra flight ends there) and pushed away. Three seconds to recharge.
 */
public class ShockwavePistolItem extends Item {
	public static final double RANGE = 16;
	public static final int COOLDOWN = 60;
	public static final float DAMAGE = 4.0F;

	public ShockwavePistolItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel) {
			fire(serverLevel, player, stack, hand);
		}
		player.getCooldowns().addCooldown(stack, COOLDOWN);
		return InteractionResult.SUCCESS;
	}

	/** Fires once; returns what was hit, or null. */
	public static LivingEntity fire(ServerLevel level, Player player, ItemStack stack, InteractionHand hand) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(RANGE));
		BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
				entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator(), eye.distanceToSqr(end));
		Vec3 stop = hit != null ? hit.getLocation() : end;

		// the ring and the beam start a little way out, so they do not fill the shooter's own view
		Vec3 muzzle = eye.add(look.scale(1.8)).add(0, -0.2, 0);
		double length = muzzle.distanceTo(stop);
		for (double d = 0.8; d < length; d += 1.2) {
			Vec3 at = muzzle.add(look.scale(d));
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		ring(level, muzzle, look);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.6F);
		player.swing(hand, SwingAnimation.DEFAULT, true);
		if (player instanceof ServerPlayer serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new Shake(5, 0.6F));
		}
		stack.hurtAndBreak(1, player, hand);
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			target.hurtServer(level, level.damageSources().sonicBoom(player), DAMAGE);
			boolean airborne = !target.onGround();
			Vec3 push = new Vec3(look.x, 0, look.z).normalize().scale(0.8);
			target.setDeltaMovement(push.x, airborne ? -2.0 : 0.3, push.z);
			target.stopFallFlying();
			CasinoGuardEntity.sendMotion(target);
			return target;
		}
		return null;
	}

	/** A ring of sparks round the muzzle, square to the shot. */
	private static void ring(ServerLevel level, Vec3 centre, Vec3 look) {
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0E-4) {
			side = new Vec3(1, 0, 0);
		}
		side = side.normalize();
		Vec3 up = side.cross(look).normalize();
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			Vec3 at = centre.add(side.scale(Math.cos(a) * 0.35)).add(up.scale(Math.sin(a) * 0.35));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
	}
}
