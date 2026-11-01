package pl.pansage.gamblecraft.sportsbook.feed;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Plain HTTPS reads for the feed thread, polite about it: an honest user agent, gzip, and a pause between requests
 * to the same host (three seconds for TheSportsDB's free tier, which also caps requests per hour).
 */
public final class WebHttp implements FeedSource.Http {
	private static final int MAX_BYTES = 6 << 20;
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	/** Per host: the time of the last request; the entry is also the lock that spaces requests to that host. */
	private final Map<String, long[]> lastRequest = new ConcurrentHashMap<>();
	private final String userAgent;

	public WebHttp(String version) {
		this.userAgent = "GambleCraft-Sportsbook/" + version + " (Minecraft mod)";
	}

	@Override
	public String get(String url) throws IOException {
		try {
			return read(url);
		} catch (BusyException busy) {
			// the feed asked for a pause (HTTP 429): wait what it asks, up to a minute, and try once more
			try {
				Thread.sleep(busy.seconds * 1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw busy;
			}
			return read(url);
		}
	}

	private static final class BusyException extends IOException {
		final int seconds;

		BusyException(int seconds) {
			super("HTTP 429");
			this.seconds = seconds;
		}
	}

	private String read(String url) throws IOException {
		URI uri = URI.create(url);
		pause(uri.getHost());
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent", userAgent)
				.header("Accept", "application/json").header("Accept-Encoding", "gzip").GET().build();
		HttpResponse<byte[]> response;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted");
		}
		if (response.statusCode() == 429) {
			int seconds = 30;
			try {
				seconds = Integer.parseInt(response.headers().firstValue("Retry-After").orElse("30").trim());
			} catch (NumberFormatException ignored) {
				// keep the default pause
			}
			throw new BusyException(Math.clamp(seconds, 1, 60));
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		byte[] body = response.body();
		if (response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip")) {
			try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
				body = in.readNBytes(MAX_BYTES);
			}
		}
		if (body.length >= MAX_BYTES) {
			throw new IOException("response too large");
		}
		return new String(body, StandardCharsets.UTF_8);
	}

	private void pause(String host) {
		long gap = host != null && host.contains("thesportsdb") ? 3000 : 300;
		long[] last = lastRequest.computeIfAbsent(host == null ? "" : host, h -> new long[1]);
		synchronized (last) {
			long wait = last[0] + gap - System.currentTimeMillis();
			if (wait > 0) {
				try {
					Thread.sleep(wait);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			last[0] = System.currentTimeMillis();
		}
	}
}
