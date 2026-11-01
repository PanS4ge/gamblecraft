package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** One public sports feed. */
public interface FeedSource {
	/** Reads a page; the real one goes over HTTP, tests hand back fixture files. */
	interface Http {
		String get(String url) throws IOException;
	}

	Source id();

	/** A league's events from {@code from} to {@code to} (inclusive days); throws when nothing could be read at all. */
	List<SportEvent> fetch(League league, LocalDate from, LocalDate to, Http http, long now) throws IOException;

	static FeedSource of(Source source) {
		return switch (source) {
			case ESPN -> EspnFeed.INSTANCE;
			case NHL -> NhlFeed.INSTANCE;
			case OPENLIGADB -> OpenLigaFeed.INSTANCE;
			case THESPORTSDB -> SportsDbFeed.INSTANCE;
		};
	}
}
