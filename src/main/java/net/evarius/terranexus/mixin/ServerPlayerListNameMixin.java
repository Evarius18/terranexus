package net.evarius.terranexus.mixin;

import net.evarius.terranexus.config.ConfigManager;
import net.evarius.terranexus.identity.RoleplayNames;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerListNameMixin {
    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void terranexus$replaceAccountName(CallbackInfoReturnable<Text> callback) {
        if (!ConfigManager.general().hideMinecraftNamesInPlayerList) return;
        callback.setReturnValue(RoleplayNames.playerListName((ServerPlayerEntity) (Object) this));
    }
}
