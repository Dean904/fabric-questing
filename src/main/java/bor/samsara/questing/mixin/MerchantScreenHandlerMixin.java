package bor.samsara.questing.mixin;

import bor.samsara.questing.SamsaraFabricQuesting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.MerchantScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreenHandler.class)
public class MerchantScreenHandlerMixin {

    @Inject(method = "onTakeOutput(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void samsara_onTradeComplete(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        // Notify collect subject that player obtained an item (trade output)
        if (player != null && stack != null && !stack.isEmpty()) {
            SamsaraFabricQuesting.collectItemSubject.processPlayerObtainedStack(player, stack);
        }
    }
}
