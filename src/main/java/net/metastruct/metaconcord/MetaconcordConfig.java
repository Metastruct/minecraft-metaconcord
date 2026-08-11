package net.metastruct.metaconcord;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MetaconcordConfig {
	public static final ModConfigSpec SPEC;
	public static final ModConfigSpec.ConfigValue<String> ENDPOINT;
	public static final ModConfigSpec.ConfigValue<String> TOKEN;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

		ENDPOINT = builder
			.comment("Full websocket URI of the metaconcord bridge, e.g. wss://example.com/minecraft/ws")
			.define("endpoint", "");
		TOKEN = builder
			.comment("Shared auth token, sent as the X-Auth-Token header")
			.define("token", "");

		SPEC = builder.build();
	}
}
