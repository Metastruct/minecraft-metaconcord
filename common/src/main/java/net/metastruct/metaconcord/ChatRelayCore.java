package net.metastruct.metaconcord;

import net.metastruct.metaconcord.payload.Payloads;
import net.metastruct.metaconcord.ws.MetaconcordSocket;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;

/**
 * Turns game events into outbound payloads. Loader event adapters call these
 * methods with vanilla types; the vanilla announce rules (gamerules, whether
 * an advancement announces in chat) are applied here so both loaders filter
 * identically.
 */
public class ChatRelayCore {
	private final MetaconcordSocket socket;

	public ChatRelayCore(MetaconcordSocket socket) {
		this.socket = socket;
	}

	public void chat(ServerPlayer player, String content) {
		content = content.trim();
		if (content.isEmpty()) return;

		socket.send(Payloads.chat(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			content,
			false));
	}

	public void emote(ServerPlayer player, String action) {
		action = action.trim();
		if (action.isEmpty()) return;

		socket.send(Payloads.chat(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			action,
			true));
	}

	/**
	 * Call after the combat tracker holds the fatal entry, so the message
	 * matches the one vanilla broadcasts.
	 */
	public void death(ServerPlayer player) {
		if (!player.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) return;

		socket.send(Payloads.death(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			player.getCombatTracker().getDeathMessage().getString()));
	}

	public void advancement(ServerPlayer player, AdvancementHolder holder) {
		// loaders report every earned advancement that has a display, even the
		// ones vanilla stays quiet about, so re-apply its announce conditions
		DisplayInfo display = holder.value().display().orElse(null);
		if (display == null || !display.shouldAnnounceChat()) return;
		if (!player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) return;

		socket.send(Payloads.advancement(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			display.getTitle().getString(),
			display.getDescription().getString(),
			display.getType().getSerializedName()));
	}

	public void join(ServerPlayer player) {
		socket.send(Payloads.join(
			player.getGameProfile().getName(),
			player.getUUID().toString()));
		socket.sendStatusSoon();
	}

	public void leave(ServerPlayer player) {
		socket.send(Payloads.leave(
			player.getGameProfile().getName(),
			player.getUUID().toString()));
		socket.sendStatusSoon();
	}
}
