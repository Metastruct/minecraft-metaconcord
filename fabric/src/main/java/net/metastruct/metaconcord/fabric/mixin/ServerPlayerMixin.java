package net.metastruct.metaconcord.fabric.mixin;

import net.metastruct.metaconcord.ChatRelayCore;
import net.metastruct.metaconcord.MetaconcordCommon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API's AFTER_DEATH fires at the end of die(), after
 * getCombatTracker().recheckStatus() has cleared the fatal entry, which leaves
 * the death message as the generic "<player> died". Injecting at the head keeps
 * the tracker intact, matching what vanilla broadcasts and what NeoForge's
 * LivingDeathEvent sees.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"))
	private void metaconcord$onDeath(DamageSource source, CallbackInfo ci) {
		ChatRelayCore relay = MetaconcordCommon.relay();
		if (relay != null) relay.death((ServerPlayer) (Object) this);
	}
}
