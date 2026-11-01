package pl.pansage.gamblecraft.core.table;

import java.util.ArrayList;
import java.util.List;

import pl.pansage.gamblecraft.core.cards.Card;
import pl.pansage.gamblecraft.core.table.TableTypes.HandView;
import pl.pansage.gamblecraft.core.table.TableTypes.Msg;
import pl.pansage.gamblecraft.core.table.TableTypes.PlacedCard;

/** A row of cards on the table: a player's hand, the dealer's hand or the community cards. */
public final class TableHand {
	public final String id;
	public final int owner;
	public String label;
	/** Only the owner sees face-up cards (Hold'em hole cards). */
	public final boolean privateToOwner;
	public final List<PlacedCard> cards = new ArrayList<>();
	public Msg value;
	public String result;
	public boolean active;
	/** Bit i set = card i is highlighted (held in video poker, part of the winning hand at showdown). */
	public int marks;

	public TableHand(String id, int owner, String label, boolean privateToOwner) {
		this.id = id;
		this.owner = owner;
		this.label = label;
		this.privateToOwner = privateToOwner;
	}

	public void add(Card card, boolean faceUp) {
		cards.add(new PlacedCard(card, faceUp));
	}

	public void revealAll() {
		cards.replaceAll(c -> new PlacedCard(c.card(), true));
	}

	public List<Card> cardList() {
		return cards.stream().map(PlacedCard::card).toList();
	}

	HandView view(int viewerSeat, boolean showdown) {
		List<Integer> ids = new ArrayList<>(cards.size());
		for (PlacedCard placed : cards) {
			boolean visible = placed.faceUp() && (!privateToOwner || owner == viewerSeat || showdown);
			ids.add(visible ? placed.card().id() : -1);
		}
		boolean showValue = !privateToOwner || owner == viewerSeat || showdown;
		return new HandView(id, owner, label, ids, showValue ? value : null, result, active, marks);
	}
}
