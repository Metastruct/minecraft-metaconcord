package net.metastruct.metaconcord;

import net.metastruct.metaconcord.ws.MetaconcordSocket;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

/**
 * Loader-independent lifecycle: each loader's server-started hook calls
 * {@link #start}, its server-stopping hook calls {@link #stop}.
 */
public final class MetaconcordCommon {
	public static final String MOD_ID = "metaconcord";

	private static MetaconcordSocket socket;
	private static ChatRelayCore relay;

	private MetaconcordCommon() {}

	/**
	 * Loads the config, connects the socket and returns the relay the loader's
	 * event adapters should feed. Returns null (relay disabled) when no
	 * endpoint is configured.
	 */
	public static ChatRelayCore start(MinecraftServer server, Path configDir) {
		MetaconcordConfig config = MetaconcordConfig.load(configDir);
		if (config.endpoint().isBlank()) {
			MetaconcordSocket.LOGGER.warn(
				"metaconcord endpoint not configured, relay disabled (see {})",
				configDir.resolve(MetaconcordConfig.FILE_NAME));
			return null;
		}
		if (config.token().isBlank()) {
			MetaconcordSocket.LOGGER.warn(
				"metaconcord token is empty, relying on the bridge's IP allowlist only");
		}

		socket = new MetaconcordSocket(server, config.endpoint(), config.token());
		relay = new ChatRelayCore(socket);
		socket.connect();
		return relay;
	}

	/** The active relay, or null when disabled or the server is not started. */
	public static ChatRelayCore relay() {
		return relay;
	}

	public static void stop() {
		if (socket != null) {
			socket.shutdown();
			socket = null;
			relay = null;
		}
	}
}
