package pl.pansage.gamblecraft.registry;

import java.util.List;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import pl.pansage.gamblecraft.GambleCraft;
import pl.pansage.gamblecraft.item.ScratchData;

public final class ModComponents {
	/** Card id 0..51 (see {@link pl.pansage.gamblecraft.core.cards.Card#id()}). */
	public static final DataComponentType<Integer> CARD = register("card", DataComponentType.<Integer>builder()
			.persistent(Codec.intRange(0, 51))
			.networkSynchronized(ByteBufCodecs.VAR_INT));

	/** Cards held by a deck, top of the deck last. */
	public static final DataComponentType<List<Integer>> DECK_CARDS = register("deck_cards", DataComponentType.<List<Integer>>builder()
			.persistent(Codec.intRange(0, 51).listOf())
			.networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
			.cacheEncoding());

	/** Scratch card state: seed, issuing village, scratched result. */
	public static final DataComponentType<ScratchData> SCRATCH = register("scratch", DataComponentType.<ScratchData>builder()
			.persistent(ScratchData.CODEC)
			.networkSynchronized(ScratchData.STREAM_CODEC));

	/** A promissory note's debt and due date. */
	public static final DataComponentType<pl.pansage.gamblecraft.item.PromissoryNoteItem.Note> LOAN_NOTE = register("loan_note",
			DataComponentType.<pl.pansage.gamblecraft.item.PromissoryNoteItem.Note>builder()
					.persistent(pl.pansage.gamblecraft.item.PromissoryNoteItem.Note.CODEC)
					.networkSynchronized(pl.pansage.gamblecraft.item.PromissoryNoteItem.Note.STREAM_CODEC));

	private ModComponents() {
	}

	private static <T> DataComponentType<T> register(String name, DataComponentType.Builder<T> builder) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, GambleCraft.id(name), builder.build());
	}

	public static void init() {
	}
}
