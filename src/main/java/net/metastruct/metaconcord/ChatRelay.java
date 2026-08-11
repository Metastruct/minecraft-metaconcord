package net.metastruct.metaconcord;

import net.metastruct.metaconcord.payload.Payloads;
import net.metastruct.metaconcord.ws.MetaconcordSocket;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class ChatRelay {
	private final MetaconcordSocket socket;

	public ChatRelay(MetaconcordSocket socket) {
		this.socket = socket;
	}

	@SubscribeEvent
	public void onChat(ServerChatEvent event) {
		Player player = event.getPlayer();
		String content = event.getMessage().getString().trim();
		if (content.isEmpty()) return;

		socket.send(Payloads.chat(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			content));
	}

	@SubscribeEvent
	public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
		Player player = event.getEntity();
		socket.send(Payloads.join(
			player.getGameProfile().getName(),
			player.getUUID().toString()));
		socket.sendStatusSoon();
	}

	@SubscribeEvent
	public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
		Player player = event.getEntity();
		socket.send(Payloads.leave(
			player.getGameProfile().getName(),
			player.getUUID().toString()));
		socket.sendStatusSoon();
	}
}
