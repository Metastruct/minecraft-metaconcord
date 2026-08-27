package net.metastruct.metaconcord.neoforge;

import net.metastruct.metaconcord.ChatRelayCore;
import net.metastruct.metaconcord.MetaconcordCommon;
import net.metastruct.metaconcord.payload.ModInventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(MetaconcordCommon.MOD_ID)
public class Metaconcord {
	public Metaconcord(IEventBus modEventBus, ModContainer modContainer) {
		ModInventory.setModListSupplier(NeoForgeModList::collect);
		NeoForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onServerStarted(ServerStartedEvent event) {
		ChatRelayCore relay = MetaconcordCommon.start(event.getServer(), FMLPaths.CONFIGDIR.get());
		if (relay != null) {
			NeoForge.EVENT_BUS.register(new NeoForgeEventAdapter(relay));
		}
	}

	@SubscribeEvent
	public void onServerStopping(ServerStoppingEvent event) {
		MetaconcordCommon.stop();
	}
}
