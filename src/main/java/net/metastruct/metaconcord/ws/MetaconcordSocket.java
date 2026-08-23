package net.metastruct.metaconcord.ws;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import net.metastruct.metaconcord.console.ConsoleRelay;
import net.metastruct.metaconcord.payload.ModInventory;
import net.metastruct.metaconcord.payload.Payloads;
import net.metastruct.metaconcord.stats.StatsCollector;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetaconcordSocket implements WebSocket.Listener {
	public static final Logger LOGGER = LogUtils.getLogger();

	private static final int DISCORD_BLURPLE = 0x5865F2;
	private static final long HEARTBEAT_SECONDS = 10;
	private static final long MAX_BACKOFF_SECONDS = 300;
	private static final long STATS_SECONDS = 5;
	private static final long CONSOLE_FLUSH_MILLIS = 250;

	private final MinecraftServer server;
	private final URI endpoint;
	private final String token;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ScheduledExecutorService scheduler =
		Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "metaconcord-socket");
			t.setDaemon(true);
			return t;
		});

	private final StringBuilder partial = new StringBuilder();
	private final Object sendLock = new Object();
	private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);

	private volatile WebSocket webSocket;
	private volatile boolean shuttingDown = false;
	private volatile boolean reconnectScheduled = false;
	private final AtomicBoolean statusQueued = new AtomicBoolean(false);
	private int backoff = 0;
	private ScheduledFuture<?> heartbeat;
	private ScheduledFuture<?> statsTick;
	private ScheduledFuture<?> consoleTick;
	private final ConsoleRelay console;
	private final StatsCollector stats;

	public MetaconcordSocket(MinecraftServer server, String endpoint, String token) {
		this.server = server;
		this.endpoint = URI.create(endpoint);
		this.token = token;
		this.stats = new StatsCollector(server);
		this.console = new ConsoleRelay();
		this.console.attach(this::send);
	}

	public void connect() {
		if (shuttingDown) return;
		httpClient.newWebSocketBuilder()
			.header("X-Auth-Token", token)
			.buildAsync(endpoint, this)
			.whenComplete((ws, err) -> {
				if (err != null) {
					LOGGER.warn("metaconcord connection failed: {}", err.getMessage());
					scheduleReconnect();
				}
			});
	}

	public void shutdown() {
		shuttingDown = true;
		console.detach();
		scheduler.shutdownNow();
		WebSocket ws = webSocket;
		if (ws != null) {
			ws.sendClose(WebSocket.NORMAL_CLOSURE, "server stopping");
			ws.abort();
		}
	}

	public void send(String text) {
		WebSocket ws = webSocket;
		if (ws == null || shuttingDown) return;
		synchronized (sendLock) {
			// java.net.http.WebSocket forbids overlapping sends
			sendChain = sendChain
				.thenCompose(ignored -> ws.sendText(text, true))
				.exceptionally(err -> null);
		}
	}

	/**
	 * Sends a StatusPayload snapshot after a short debounce, so bursts of
	 * joins/leaves collapse into one update. The snapshot is taken on the
	 * server thread.
	 */
	public void sendStatusSoon() {
		if (shuttingDown || !statusQueued.compareAndSet(false, true)) return;
		try {
			scheduler.schedule(() -> {
				statusQueued.set(false);
				server.execute(() -> send(Payloads.status(server)));
			}, 1, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			statusQueued.set(false);
		}
	}

	/**
	 * Sends the AddonsPayload once the status is through. Hashing every jar is
	 * slow the first time, so it runs on the socket thread, never the server thread.
	 */
	private void sendAddonsSoon() {
		if (shuttingDown) return;
		try {
			scheduler.schedule(() -> {
				try {
					send(ModInventory.frame());
				} catch (Exception e) {
					LOGGER.warn("could not build AddonsPayload", e);
				}
			}, 5, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			// scheduler shut down
		}
	}

	private void scheduleReconnect() {
		if (shuttingDown || reconnectScheduled) return;
		reconnectScheduled = true;
		webSocket = null;
		cancelTimers();
		long delay = Math.min((long) Math.pow(2, backoff), MAX_BACKOFF_SECONDS);
		backoff++;
		LOGGER.info("metaconcord reconnecting in {}s", delay);
		try {
			scheduler.schedule(() -> {
				reconnectScheduled = false;
				connect();
			}, delay, TimeUnit.SECONDS);
		} catch (Exception ignored) {
			// scheduler shut down
		}
	}

	private void cancelTimers() {
		if (heartbeat != null) {
			heartbeat.cancel(false);
			heartbeat = null;
		}
		if (statsTick != null) {
			statsTick.cancel(false);
			statsTick = null;
		}
		if (consoleTick != null) {
			consoleTick.cancel(false);
			consoleTick = null;
		}
	}

	private void sendStats() {
		try {
			send(stats.frame());
		} catch (Exception e) {
			LOGGER.warn("could not build StatsPayload: {}", e.getMessage());
		}
	}

	@Override
	public void onOpen(WebSocket ws) {
		webSocket = ws;
		backoff = 0;
		synchronized (sendLock) {
			sendChain = CompletableFuture.completedFuture(null);
		}
		// the bridge re-subscribes on every fresh connection when it still has viewers
		console.setSubscribed(false);
		cancelTimers();
		heartbeat = scheduler.scheduleAtFixedRate(
			() -> send(""), HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		statsTick = scheduler.scheduleAtFixedRate(
			this::sendStats, 1, STATS_SECONDS, TimeUnit.SECONDS);
		consoleTick = scheduler.scheduleAtFixedRate(
			console::flush, CONSOLE_FLUSH_MILLIS, CONSOLE_FLUSH_MILLIS, TimeUnit.MILLISECONDS);
		LOGGER.info("metaconcord connected to {}", endpoint);
		sendStatusSoon();
		sendAddonsSoon();
		ws.request(1);
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		partial.append(data);
		if (last) {
			String message = partial.toString();
			partial.setLength(0);
			try {
				handleMessage(message);
			} catch (Exception err) {
				LOGGER.warn("metaconcord failed to handle message", err);
			}
		}
		ws.request(1);
		return null;
	}

	@Override
	public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
		LOGGER.info("metaconcord disconnected - [{}] {}", statusCode, reason);
		scheduleReconnect();
		return null;
	}

	@Override
	public void onError(WebSocket ws, Throwable error) {
		LOGGER.warn("metaconcord socket error: {}", error.getMessage());
		scheduleReconnect();
	}

	private void handleMessage(String message) {
		JsonObject root = JsonParser.parseString(message).getAsJsonObject();
		if (!root.has("payload")) return;
		JsonObject payload = root.getAsJsonObject("payload");
		String name = payload.get("name").getAsString();
		JsonObject data = payload.getAsJsonObject("data");

		if ("ChatPayload".equals(name)) {
			handleChat(data);
		} else if ("ConsolePayload".equals(name)) {
			handleConsole(data);
		}
		// unknown payloads are ignored on purpose
	}

	private void handleConsole(JsonObject data) {
		String action = data.get("action").getAsString();
		switch (action) {
			case "subscribe" -> {
				console.setSubscribed(true);
				console.sendReplay();
			}
			case "unsubscribe" -> console.setSubscribed(false);
			case "command" -> {
				String command = data.get("command").getAsString();
				LOGGER.info("metaconcord console: {}", command);
				server.execute(() -> server.getCommands()
					.performPrefixedCommand(server.createCommandSourceStack(), command));
			}
			default -> {}
		}
	}

	private void handleChat(JsonObject data) {
		JsonObject user = data.getAsJsonObject("user");
		String username = user.get("username").getAsString();
		String nick = user.has("nick") ? user.get("nick").getAsString() : "";
		String displayName = nick.isEmpty() ? username : nick;
		int color = user.get("color").getAsInt();
		String content = data.get("content").getAsString();

		Style nameStyle = Style.EMPTY.withColor(TextColor.fromRgb(color != 0 ? color : 0xFFFFFF));
		// unstyled root: appended children inherit the root's style, so every
		// part must carry its own color instead of the root carrying blurple
		MutableComponent component = Component.empty()
			.append(Component.literal("[Discord] ")
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(DISCORD_BLURPLE))));

		if (data.has("replied_message")) {
			JsonObject reply = data.getAsJsonObject("replied_message");
			String replyName = reply.get("ingameName").getAsString();
			String replyContent = reply.get("content").getAsString();
			String replyLine = replyName.isEmpty()
				? "-> replying to: " + replyContent
				: "-> replying to " + replyName + ": " + replyContent;
			component.append(Component.literal(replyLine + "\n")
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x999999))));
		}

		component
			.append(Component.literal(displayName).withStyle(nameStyle))
			.append(Component.literal(": " + content));

		server.execute(() -> server.getPlayerList().broadcastSystemMessage(component, false));
	}
}
