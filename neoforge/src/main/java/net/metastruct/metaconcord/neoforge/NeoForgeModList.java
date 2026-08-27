package net.metastruct.metaconcord.neoforge;

import net.metastruct.metaconcord.payload.ModEntry;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NeoForgeModList {
	private NeoForgeModList() {}

	public static List<ModEntry> collect() {
		List<ModEntry> entries = new ArrayList<>();
		for (IModInfo info : ModList.get().getMods()) {
			Path path = null;
			try {
				path = info.getOwningFile().getFile().getFilePath();
			} catch (Exception ignored) {
				// virtual or jar-in-jar files have no usable path
			}

			entries.add(new ModEntry(
				info.getModId(),
				info.getDisplayName(),
				info.getVersion().toString(),
				info.getDescription(),
				path,
				configString(info, "sources")
					.or(() -> info.getModURL().map(Object::toString))
					.orElse(null),
				configString(info, "issueTrackerURL").orElse(null)));
		}
		return entries;
	}

	private static Optional<String> configString(IModInfo info, String key) {
		try {
			Optional<Object> value = info.getConfig().getConfigElement(key);
			return value.map(Object::toString).filter(s -> !s.isBlank());
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
