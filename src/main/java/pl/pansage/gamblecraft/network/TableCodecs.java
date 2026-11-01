package pl.pansage.gamblecraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import pl.pansage.gamblecraft.core.table.Act;
import pl.pansage.gamblecraft.core.table.TableTypes.ActionOption;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.Phase;
import pl.pansage.gamblecraft.core.table.TableTypes.SeatView;
import pl.pansage.gamblecraft.core.table.TableTypes.TableSnapshot;

/** Wire format of {@link TableSnapshot}. Hidden cards are already {@code -1} in the snapshot the server builds per viewer. */
public final class TableCodecs {
	public static final StreamCodec<RegistryFriendlyByteBuf, TableSnapshot> SNAPSHOT = StreamCodec.of(TableCodecs::write, TableCodecs::read);

	private TableCodecs() {
	}

	private static void writeMsg(FriendlyByteBuf buf, Msg msg) {
		buf.writeBoolean(msg != null);
		if (msg != null) {
			buf.writeUtf(msg.key());
			buf.writeVarInt(msg.args().size());
			for (String arg : msg.args()) {
				buf.writeUtf(arg);
			}
		}
	}

	private static Msg readMsg(FriendlyByteBuf buf) {
		if (!buf.readBoolean()) {
			return null;
		}
		String key = buf.readUtf();
		int n = buf.readVarInt();
		List<String> args = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			args.add(buf.readUtf());
		}
		return new Msg(key, args);
	}

	private static void writeNullableUtf(FriendlyByteBuf buf, String s) {
		buf.writeBoolean(s != null);
		if (s != null) {
			buf.writeUtf(s);
		}
	}

	private static String readNullableUtf(FriendlyByteBuf buf) {
		return buf.readBoolean() ? buf.readUtf() : null;
	}

	private static void write(RegistryFriendlyByteBuf buf, TableSnapshot s) {
		buf.writeUtf(s.game());
		buf.writeEnum(s.phase());
		buf.writeVarInt(s.activeSeat() + 1);
		buf.writeVarInt(s.round());
		buf.writeVarInt(s.seats().size());
		for (SeatView seat : s.seats()) {
			buf.writeVarInt(seat.index());
			buf.writeUUID(seat.player());
			buf.writeUtf(seat.name() == null ? "" : seat.name());
			buf.writeVarLong(seat.bet());
			buf.writeVarLong(seat.side());
			buf.writeVarInt(seat.option());
			buf.writeVarLong(seat.stack());
			buf.writeVarLong(seat.lastPayout());
			writeNullableUtf(buf, seat.status());
		}
		buf.writeVarInt(s.hands().size());
		for (HandView hand : s.hands()) {
			buf.writeUtf(hand.id());
			buf.writeVarInt(hand.owner() + 2);
			buf.writeUtf(hand.label());
			buf.writeVarInt(hand.cards().size());
			for (int card : hand.cards()) {
				buf.writeVarInt(card + 1);
			}
			writeMsg(buf, hand.value());
			writeNullableUtf(buf, hand.result());
			buf.writeBoolean(hand.active());
			buf.writeVarInt(hand.marks());
		}
		buf.writeVarLong(s.pot());
		buf.writeVarInt(s.actions().size());
		for (ActionOption option : s.actions()) {
			buf.writeEnum(option.act());
			buf.writeVarLong(option.minCost());
			buf.writeVarLong(option.maxCost());
		}
		buf.writeVarInt(s.log().size());
		for (Msg msg : s.log()) {
			writeMsg(buf, msg);
		}
	}

	private static TableSnapshot read(RegistryFriendlyByteBuf buf) {
		String game = buf.readUtf();
		Phase phase = buf.readEnum(Phase.class);
		int active = buf.readVarInt() - 1;
		int round = buf.readVarInt();
		int seatCount = buf.readVarInt();
		List<SeatView> seats = new ArrayList<>(seatCount);
		for (int i = 0; i < seatCount; i++) {
			int index = buf.readVarInt();
			UUID player = buf.readUUID();
			String name = buf.readUtf();
			seats.add(new SeatView(index, player, name, buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(),
					buf.readVarLong(), readNullableUtf(buf)));
		}
		int handCount = buf.readVarInt();
		List<HandView> hands = new ArrayList<>(handCount);
		for (int i = 0; i < handCount; i++) {
			String id = buf.readUtf();
			int owner = buf.readVarInt() - 2;
			String label = buf.readUtf();
			int cardCount = buf.readVarInt();
			List<Integer> cards = new ArrayList<>(cardCount);
			for (int c = 0; c < cardCount; c++) {
				cards.add(buf.readVarInt() - 1);
			}
			hands.add(new HandView(id, owner, label, cards, readMsg(buf), readNullableUtf(buf), buf.readBoolean(), buf.readVarInt()));
		}
		long pot = buf.readVarLong();
		int actionCount = buf.readVarInt();
		List<ActionOption> actions = new ArrayList<>(actionCount);
		for (int i = 0; i < actionCount; i++) {
			actions.add(new ActionOption(buf.readEnum(Act.class), buf.readVarLong(), buf.readVarLong()));
		}
		int logCount = buf.readVarInt();
		List<Msg> log = new ArrayList<>(logCount);
		for (int i = 0; i < logCount; i++) {
			log.add(readMsg(buf));
		}
		return new TableSnapshot(game, phase, active, round, seats, hands, pot, actions, log);
	}
}
