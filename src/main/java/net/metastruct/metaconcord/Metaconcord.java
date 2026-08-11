package net.metastruct.metaconcord;

import net.metastruct.metaconcord.ws.MetaconcordSocket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(Metaconcord.MOD_ID)
public class Metaconcord {
	public static final String MOD_ID = "metaconcord";

	private MetaconcordSocket socket;

	public Metaconcord(IEventBus modEventBus, ModContainer modContainer) {
		modContainer.registerConfig(ModConfig.Type.COMMON, MetaconcordConfig.SPEC);
		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		String endpoint = MetaconcordConfig.ENDPOINT.get();
		String token = MetaconcordConfig.TOKEN.get();
		if (endpoint.isBlank()) {
			MetaconcordSocket.LOGGER.warn(
				"metaconcord endpoint not configured, relay disabled (see config/metaconcord-common.toml)");
			return;
		}
		if (token.isBlank()) {
			MetaconcordSocket.LOGGER.warn(
				"metaconcord token is empty, relying on the bridge's IP allowlist only");
		}

		socket = new MetaconcordSocket(event.getServer(), endpoint, token);
		NeoForge.EVENT_BUS.register(new ChatRelay(socket));
		socket.connect();
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		if (socket != null) {
			socket.shutdown();
			socket = null;
		}
	}
}
