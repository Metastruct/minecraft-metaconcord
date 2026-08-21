package net.metastruct.metaconcord.payload;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wire frames for the metaconcord bridge.
 * Outbound: {"name": "<PayloadName>", "data": {...}}
 * Inbound: {"payload": {"name": "<PayloadName>", "data": {...}}}
 */
public final class Payloads {
	private static final Gson GSON = new Gson();

	private Payloads() {}

	private static JsonObject player(String nick, String uuid) {
		JsonObject player = new JsonObject();
		player.addProperty("nick", nick);
		player.addProperty("uuid", uuid);
		return player;
	}

	private static String frame(String name, JsonObject data) {
		JsonObject frame = new JsonObject();
		frame.addProperty("name", name);
		frame.add("data", data);
		return GSON.toJson(frame);
	}

	/** Limits mirror Discord's own field limits, so the bridge can relay verbatim. */
	private static String truncate(String value, int max) {
		return value.length() > max ? value.substring(0, max) : value;
	}

	/** {@code emote} marks a /me action, rendered italic instead of as a normal line. */
	public static String chat(String nick, String uuid, String content, boolean emote) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		data.addProperty("content", truncate(content, 2000));
		if (emote) data.addProperty("emote", true);
		return frame("ChatPayload", data);
	}

	/** {@code message} is the vanilla death message, e.g. "Nick was slain by Zombie". */
	public static String death(String nick, String uuid, String message) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		data.addProperty("message", truncate(message, 256));
		return frame("DeathPayload", data);
	}

	/** {@code type} is the advancement frame: task, goal or challenge. */
	public static String advancement(
		String nick, String uuid, String title, String description, String type) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		data.addProperty("title", truncate(title, 256));
		data.addProperty("description", truncate(description, 1000));
		data.addProperty("type", type);
		return frame("AdvancementPayload", data);
	}

	public static String join(String nick, String uuid) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		data.addProperty("spawned", true);
		return frame("JoinLeavePayload", data);
	}

	public static String leave(String nick, String uuid) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		return frame("JoinLeavePayload", data);
	}

	/** Must be called on the server thread. */
	public static String status(MinecraftServer server) {
		JsonObject data = new JsonObject();
		data.addProperty("hostname", server.getMotd());
		data.addProperty("version", server.getServerVersion());
		data.addProperty("maxPlayers", server.getMaxPlayers());
		data.addProperty("uptime", server.getTickCount() / 20);
		JsonArray players = new JsonArray();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			players.add(player(p.getGameProfile().getName(), p.getUUID().toString()));
		}
		data.add("players", players);
		return frame("StatusPayload", data);
	}
}
