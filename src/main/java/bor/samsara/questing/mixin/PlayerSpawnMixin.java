package bor.samsara.questing.mixin;

import bor.samsara.questing.SamsaraFabricQuesting;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(ServerPlayerEntity.class)
public class PlayerSpawnMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    @Inject(method = "setSpawnPoint", at = @At("TAIL"))
    private void onSetSpawnPoint(ServerPlayerEntity.Respawn respawn, boolean sendMessage, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        SamsaraFabricQuesting.setSpawnSubject.onSetSpawnPoint(player, respawn);
    }

}
