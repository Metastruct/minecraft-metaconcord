package net.metastruct.metaconcord;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.metastruct.metaconcord.payload.Payloads;
import net.metastruct.metaconcord.ws.MetaconcordSocket;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

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
			content,
			false));
	}

	/**
	 * /me never goes through ServerChatEvent, so it is picked up from the parsed
	 * command instead. LOWEST priority so mods that cancel the command win: a
	 * cancelled event is not delivered here.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onCommand(CommandEvent event) {
		ParseResults<CommandSourceStack> parse = event.getParseResults();
		List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
		// [me, action] - anything else (including a bare /me) is not an emote
		if (nodes.size() < 2 || !"me".equals(nodes.get(0).getNode().getName())) return;
		if (!(parse.getContext().getSource().getEntity() instanceof ServerPlayer player)) return;

		String action = nodes.get(1).getRange().get(parse.getReader()).trim();
		if (action.isEmpty()) return;

		socket.send(Payloads.chat(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			action,
			true));
	}

	/**
	 * The combat tracker already holds the fatal entry here, so the message
	 * matches the one vanilla broadcasts right after.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		if (!player.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) return;

		socket.send(Payloads.death(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			player.getCombatTracker().getDeathMessage().getString()));
	}

	@SubscribeEvent
	public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;

		// the event fires for every earned advancement that has a display, even the
		// ones vanilla stays quiet about, so re-apply its announce conditions
		DisplayInfo display = event.getAdvancement().value().display().orElse(null);
		if (display == null || !display.shouldAnnounceChat()) return;
		if (!player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) return;

		socket.send(Payloads.advancement(
			player.getGameProfile().getName(),
			player.getUUID().toString(),
			display.getTitle().getString(),
			display.getDescription().getString(),
			display.getType().getSerializedName()));
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
