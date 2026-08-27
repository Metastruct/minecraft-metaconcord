package net.metastruct.metaconcord.neoforge;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import net.metastruct.metaconcord.ChatRelayCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/** NeoForge event shells; the payload logic lives in {@link ChatRelayCore}. */
public class NeoForgeEventAdapter {
	private final ChatRelayCore relay;

	public NeoForgeEventAdapter(ChatRelayCore relay) {
		this.relay = relay;
	}

	@SubscribeEvent
	public void onChat(ServerChatEvent event) {
		relay.chat(event.getPlayer(), event.getMessage().getString());
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

		relay.emote(player, nodes.get(1).getRange().get(parse.getReader()));
	}

	/**
	 * LOWEST priority: the combat tracker already holds the fatal entry here,
	 * so the message matches the one vanilla broadcasts right after.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			relay.death(player);
		}
	}

	@SubscribeEvent
	public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			relay.advancement(player, event.getAdvancement());
		}
	}

	@SubscribeEvent
	public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			relay.join(player);
		}
	}

	@SubscribeEvent
	public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			relay.leave(player);
		}
	}
}
