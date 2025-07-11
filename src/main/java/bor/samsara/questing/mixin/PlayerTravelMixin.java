package bor.samsara.questing.mixin;

import bor.samsara.questing.hearth.HearthStoneEventRegisters;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(PlayerEntity.class)
public class PlayerTravelMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    static {
        log.info("Loading PlayerTickMovementMixin...");
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovement(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (HearthStoneEventRegisters.isTeleporting(player.getUuidAsString()) && HearthStoneEventRegisters.hasPlayerMovedFromStartPos(player)) {
            HearthStoneEventRegisters.cancelTeleport(player);
        }
    }

}
