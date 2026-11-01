package pl.pansage.gamblecraft.item;

import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import pl.pansage.gamblecraft.registry.ModComponents;

/** The loan shark's IOU: what is owed and the day it falls due (the shark's own book is what counts). */
public class PromissoryNoteItem extends Item {
	public record Note(long owed, long due) {
		public static final Codec<Note> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("owed").forGetter(Note::owed),
				Codec.LONG.fieldOf("due").forGetter(Note::due)
		).apply(i, Note::new));
		public static final StreamCodec<ByteBuf, Note> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Note::owed,
				ByteBufCodecs.VAR_LONG, Note::due,
				Note::new);
	}

	public PromissoryNoteItem(Properties properties) {
		super(properties);
	}

	public static ItemStack of(long owed, long due) {
		ItemStack stack = new ItemStack(pl.pansage.gamblecraft.registry.ModItems.PROMISSORY_NOTE);
		stack.set(ModComponents.LOAN_NOTE, new Note(owed, due));
		return stack;
	}

	/** The in-game day a game time falls on, counting from one. */
	public static long day(long gameTime) {
		return gameTime / 24000 + 1;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		Note note = stack.get(ModComponents.LOAN_NOTE);
		if (note != null) {
			lines.accept(Component.translatable("gamblecraft.loan.note_owed", note.owed()).withStyle(ChatFormatting.GOLD));
			lines.accept(Component.translatable("gamblecraft.loan.note_due", day(note.due())).withStyle(ChatFormatting.GRAY));
		}
	}
}
