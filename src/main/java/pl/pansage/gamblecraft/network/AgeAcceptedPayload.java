package pl.pansage.gamblecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pl.pansage.gamblecraft.GambleCraft;

/** Client to server: the player accepted the 18+ notice on this client. */
public record AgeAcceptedPayload() implements CustomPacketPayload {
	public static final AgeAcceptedPayload INSTANCE = new AgeAcceptedPayload();
	public static final Type<AgeAcceptedPayload> TYPE = new Type<>(GambleCraft.id("age_accepted"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AgeAcceptedPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<AgeAcceptedPayload> type() {
		return TYPE;
	}
}
