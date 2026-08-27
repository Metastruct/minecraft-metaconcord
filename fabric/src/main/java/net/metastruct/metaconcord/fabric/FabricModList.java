package net.metastruct.metaconcord.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.metastruct.metaconcord.payload.ModEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FabricModList {
	private FabricModList() {}

	public static List<ModEntry> collect() {
		List<ModEntry> entries = new ArrayList<>();
		for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
			ModMetadata meta = container.getMetadata();
			// the JVM pseudo-mod is not a mod
			if ("java".equals(meta.getId())) continue;

			// jar-in-jar (nested) mods have no hashable file on disk
			Path path = null;
			ModOrigin origin = container.getOrigin();
			if (origin.getKind() == ModOrigin.Kind.PATH) {
				for (Path candidate : origin.getPaths()) {
					if (Files.isRegularFile(candidate)) {
						path = candidate;
						break;
					}
				}
			}

			entries.add(new ModEntry(
				meta.getId(),
				meta.getName(),
				meta.getVersion().getFriendlyString(),
				meta.getDescription(),
				path,
				contact(meta, "sources"),
				contact(meta, "issues")));
		}
		return entries;
	}

	private static String contact(ModMetadata meta, String key) {
		return meta.getContact().get(key).filter(s -> !s.isBlank()).orElse(null);
	}
}
