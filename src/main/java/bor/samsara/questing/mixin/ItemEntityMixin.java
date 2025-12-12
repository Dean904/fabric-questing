package bor.samsara.questing.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Deprecated
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    static {
        log.info("Loading ItemEntityMixin...");
    }

    @Inject(method = "onPlayerCollision", at = @At("TAIL"))
    private void onPickup(PlayerEntity player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getStack().isOf(Items.ROTTEN_FLESH)) {
            log.info("ZombieFlesh picked up by {}", player.getName());
        }
    }
}

