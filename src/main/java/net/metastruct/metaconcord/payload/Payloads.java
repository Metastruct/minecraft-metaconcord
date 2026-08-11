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

	public static String chat(String nick, String uuid, String content) {
		JsonObject data = new JsonObject();
		data.add("player", player(nick, uuid));
		data.addProperty("content", content.length() > 2000 ? content.substring(0, 2000) : content);
		return frame("ChatPayload", data);
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
