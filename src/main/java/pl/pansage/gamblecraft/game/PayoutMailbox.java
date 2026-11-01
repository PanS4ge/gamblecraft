package pl.pansage.gamblecraft.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import pl.pansage.gamblecraft.GambleCraft;

/** Winnings and refunds for players who were offline when a table paid out; delivered when they join. */
public final class PayoutMailbox extends SavedData {
	private static final Codec<PayoutMailbox> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ItemStack.CODEC.listOf())
			.xmap(PayoutMailbox::new, m -> m.pending);
	public static final SavedDataType<PayoutMailbox> TYPE = new SavedDataType<>(GambleCraft.id("payout_mailbox"), PayoutMailbox::new, CODEC, null);

	private final Map<UUID, List<ItemStack>> pending;

	private PayoutMailbox() {
		this(Map.of());
	}

	private PayoutMailbox(Map<UUID, List<ItemStack>> pending) {
		this.pending = new HashMap<>();
		pending.forEach((k, v) -> this.pending.put(k, new ArrayList<>(v)));
	}

	public static PayoutMailbox get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> get(server).deliver(handler.player));
	}

	public void post(UUID player, List<ItemStack> stacks) {
		if (stacks.isEmpty()) {
			return;
		}
		pending.computeIfAbsent(player, k -> new ArrayList<>()).addAll(stacks);
		setDirty();
	}

	public void deliver(ServerPlayer player) {
		List<ItemStack> stacks = pending.remove(player.getUUID());
		if (stacks == null) {
			return;
		}
		for (ItemStack stack : stacks) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
		player.sendSystemMessage(Component.translatable("gamblecraft.mailbox.delivered"));
		setDirty();
	}
}
