package pl.pansage.gamblecraft.game;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side record of which connected players accepted the 18+ notice.
 * A client only sends the acceptance after the player clicked "Accept",
 * so every gambling interaction checks {@link #require(ServerPlayer)} first.
 */
public final class AgeGate {
	private static final Set<UUID> ACCEPTED = ConcurrentHashMap.newKeySet();

	private AgeGate() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACCEPTED.remove(handler.player.getUUID()));
	}

	public static void markAccepted(ServerPlayer player) {
		ACCEPTED.add(player.getUUID());
	}

	public static boolean isAccepted(ServerPlayer player) {
		return ACCEPTED.contains(player.getUUID());
	}

	/** Returns true if the player may gamble; otherwise tells them why not. */
	public static boolean require(ServerPlayer player) {
		if (isAccepted(player)) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("gamblecraft.age_gate.required"));
		return false;
	}
}
