package net.metastruct.metaconcord;

import net.metastruct.metaconcord.ws.MetaconcordSocket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Two-key config read from config/metaconcord-common.toml on both loaders.
 * The format (flat {@code key = "value"} lines with # comments) matches what
 * the old NeoForge ModConfigSpec generated, so existing files keep working.
 */
public record MetaconcordConfig(String endpoint, String token) {
	public static final String FILE_NAME = "metaconcord-common.toml";

	private static final List<String> TEMPLATE = List.of(
		"#Full websocket URI of the metaconcord bridge, e.g. wss://example.com/minecraft/ws",
		"endpoint = \"\"",
		"#Shared auth token, sent as the X-Auth-Token header",
		"token = \"\"");

	public static MetaconcordConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		String endpoint = "";
		String token = "";

		try {
			if (!Files.exists(file)) {
				Files.createDirectories(configDir);
				Files.write(file, TEMPLATE);
				return new MetaconcordConfig(endpoint, token);
			}
			for (String line : Files.readAllLines(file)) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) continue;
				int eq = line.indexOf('=');
				if (eq < 0) continue;
				String key = line.substring(0, eq).trim();
				String value = unquote(line.substring(eq + 1).trim());
				switch (key) {
					case "endpoint" -> endpoint = value;
					case "token" -> token = value;
					default -> {}
				}
			}
		} catch (IOException e) {
			MetaconcordSocket.LOGGER.warn("could not read {}: {}", file, e.toString());
		}
		return new MetaconcordConfig(endpoint, token);
	}

	private static String unquote(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}
}
