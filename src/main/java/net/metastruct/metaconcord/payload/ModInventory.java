package net.metastruct.metaconcord.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the AddonsPayload frame: every loaded mod with the identifiers the
 * bridge needs to look it up (sha512 for Modrinth, murmur2 for CurseForge).
 * The mod list cannot change at runtime, so the frame is computed once.
 */
public final class ModInventory {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static volatile String cachedFrame;

	private ModInventory() {}

	public static String frame() {
		String frame = cachedFrame;
		if (frame == null) {
			synchronized (ModInventory.class) {
				frame = cachedFrame;
				if (frame == null) {
					frame = build();
					cachedFrame = frame;
				}
			}
		}
		return frame;
	}

	private static String build() {
		Map<Path, Fingerprints> hashed = new HashMap<>();
		JsonArray mods = new JsonArray();

		for (IModInfo info : ModList.get().getMods()) {
			JsonObject mod = new JsonObject();
			mod.addProperty("modId", info.getModId());
			mod.addProperty("displayName", info.getDisplayName());
			mod.addProperty("version", info.getVersion().toString());
			String description = info.getDescription();
			if (description != null && !description.isBlank()) {
				mod.addProperty("description", description.trim());
			}

			Path path = null;
			try {
				path = info.getOwningFile().getFile().getFilePath();
			} catch (Exception ignored) {
				// virtual or jar-in-jar files have no usable path
			}
			if (path != null && Files.isRegularFile(path)) {
				Fingerprints fp = hashed.computeIfAbsent(path, Fingerprints::of);
				if (fp != null) {
					mod.addProperty("sha512", fp.sha512);
					mod.addProperty("fingerprint", fp.murmur2);
				}
			}

			configString(info, "sources").or(() -> info.getModURL().map(Object::toString))
				.ifPresent(url -> mod.addProperty("sources", url));
			configString(info, "issueTrackerURL").ifPresent(url -> mod.addProperty("issues", url));

			mods.add(mod);
		}

		JsonObject data = new JsonObject();
		data.add("mods", mods);
		return Payloads.frame("AddonsPayload", data);
	}

	private static Optional<String> configString(IModInfo info, String key) {
		try {
			Optional<Object> value = info.getConfig().getConfigElement(key);
			return value.map(Object::toString).filter(s -> !s.isBlank());
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private record Fingerprints(String sha512, long murmur2) {
		static Fingerprints of(Path path) {
			try {
				byte[] bytes = Files.readAllBytes(path);
				return new Fingerprints(sha512Hex(bytes), curseforgeFingerprint(bytes));
			} catch (IOException | NoSuchAlgorithmException e) {
				LOGGER.warn("could not hash {}: {}", path, e.toString());
				return null;
			}
		}
	}

	private static String sha512Hex(byte[] bytes) throws NoSuchAlgorithmException {
		byte[] digest = MessageDigest.getInstance("SHA-512").digest(bytes);
		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest) sb.append(String.format("%02x", b));
		return sb.toString();
	}

	/**
	 * CurseForge fingerprint: MurmurHash2 (seed 1) over the file with
	 * whitespace bytes (tab, LF, CR, space) removed, as an unsigned 32-bit value.
	 */
	static long curseforgeFingerprint(byte[] raw) {
		byte[] data = new byte[raw.length];
		int length = 0;
		for (byte b : raw) {
			if (b != 9 && b != 10 && b != 13 && b != 32) data[length++] = b;
		}

		final int m = 0x5bd1e995;
		final int r = 24;
		int h = 1 ^ length;
		int i = 0;
		while (length - i >= 4) {
			int k = (data[i] & 0xff)
				| ((data[i + 1] & 0xff) << 8)
				| ((data[i + 2] & 0xff) << 16)
				| ((data[i + 3] & 0xff) << 24);
			k *= m;
			k ^= k >>> r;
			k *= m;
			h *= m;
			h ^= k;
			i += 4;
		}
		switch (length - i) {
			case 3: h ^= (data[i + 2] & 0xff) << 16;
			case 2: h ^= (data[i + 1] & 0xff) << 8;
			case 1: h ^= (data[i] & 0xff); h *= m;
			default:
		}
		h ^= h >>> 13;
		h *= m;
		h ^= h >>> 15;
		return h & 0xffffffffL;
	}
}
