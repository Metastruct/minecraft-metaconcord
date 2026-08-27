package net.metastruct.metaconcord.payload;

import java.nio.file.Path;

/**
 * One loaded mod as reported by the loader. description, jarPath, sources and
 * issues may be null; jarPath is null for virtual or jar-in-jar mods that have
 * no hashable file on disk.
 */
public record ModEntry(
	String id,
	String displayName,
	String version,
	String description,
	Path jarPath,
	String sources,
	String issues) {
}
