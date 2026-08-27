package net.metastruct.metaconcord.fabric;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.metastruct.metaconcord.ChatRelayCore;
import net.metastruct.metaconcord.MetaconcordCommon;
import net.metastruct.metaconcord.payload.ModInventory;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.level.ServerPlayer;

public class MetaconcordFabric implements DedicatedServerModInitializer {
	@Override
	public void onInitializeServer() {
		ModInventory.setModListSupplier(FabricModList::collect);

		ServerLifecycleEvents.SERVER_STARTED.register(server ->
			MetaconcordCommon.start(server, FabricLoader.getInstance().getConfigDir()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> MetaconcordCommon.stop());

		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			ChatRelayCore relay = MetaconcordCommon.relay();
			if (relay != null) relay.chat(sender, message.decoratedContent().getString());
		});

		// /me: fires at broadcast time, unlike NeoForge's pre-execution command
		// sniffing, so it relays exactly what was announced. The EMOTE_COMMAND
		// chat type excludes /say.
		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, params) -> {
			ChatRelayCore relay = MetaconcordCommon.relay();
			if (relay == null || !params.chatType().is(ChatType.EMOTE_COMMAND)) return;
			ServerPlayer player = source.getPlayer();
			if (player != null) relay.emote(player, message.decoratedContent().getString());
		});

		// deaths come from ServerPlayerMixin, see the note there

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ChatRelayCore relay = MetaconcordCommon.relay();
			if (relay != null) relay.join(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ChatRelayCore relay = MetaconcordCommon.relay();
			if (relay != null) relay.leave(handler.player);
		});
	}
}
