package net.metastruct.metaconcord.fabric.mixin;

import net.metastruct.metaconcord.ChatRelayCore;
import net.metastruct.metaconcord.MetaconcordCommon;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API has no advancement-earned event. AdvancementRewards#grant is
 * called exactly once in award(), in the branch where the advancement just
 * completed: the same spot NeoForge fires AdvancementEarnEvent. The announce
 * filtering (display, shouldAnnounceChat, gamerule) lives in ChatRelayCore.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(
		method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
	private void metaconcord$onEarned(AdvancementHolder advancement, String criterion,
			CallbackInfoReturnable<Boolean> cir) {
		ChatRelayCore relay = MetaconcordCommon.relay();
		if (relay != null) relay.advancement(this.player, advancement);
	}
}
