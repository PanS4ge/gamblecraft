package pl.pansage.gamblecraft.sportsbook.feed;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Forgiving reads of feed JSON: a missing or odd field is a default, never an exception. */
final class Json {
	private Json() {
	}

	/** Walks {@code path} through nested objects; null where it breaks off. */
	static JsonElement at(JsonElement root, String... path) {
		JsonElement e = root;
		for (String key : path) {
			if (e == null || !e.isJsonObject()) {
				return null;
			}
			e = e.getAsJsonObject().get(key);
		}
		return e == null || e.isJsonNull() ? null : e;
	}

	static JsonObject obj(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	static List<JsonObject> objects(JsonElement root, String... path) {
		JsonElement e = path.length == 0 ? root : at(root, path);
		List<JsonObject> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement item : (JsonArray) e) {
				if (item.isJsonObject()) {
					out.add(item.getAsJsonObject());
				}
			}
		}
		return out;
	}

	static String str(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	static boolean bool(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
	}

	/** An int field, or one written as a string ("2"); -1 when absent. */
	static int integer(JsonElement root, String... path) {
		JsonElement e = at(root, path);
		if (e == null || !e.isJsonPrimitive()) {
			return -1;
		}
		try {
			return (int) Math.round(Double.parseDouble(e.getAsString().trim()));
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	/** "2026-11-02T20:00Z", "2026-11-02T20:00:00Z" or a zoneless UTC "2026-11-02T17:00:00"; 0 if unreadable. */
	static long time(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		String s = text.trim().replace(' ', 'T');
		if (s.matches(".*T\\d\\d:\\d\\dZ$")) {
			s = s.substring(0, s.length() - 1) + ":00Z";
		}
		try {
			return Instant.parse(s).toEpochMilli();
		} catch (DateTimeParseException e) {
			try {
				return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli();
			} catch (DateTimeParseException e2) {
				return 0;
			}
		}
	}
}
