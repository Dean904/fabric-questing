package bor.samsara.questing.mixin;

import bor.samsara.questing.SamsaraFabricQuesting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotMixin {

    @Inject(method = "onTakeItem(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("TAIL"))
    private void samsara_onTakeItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player != null && stack != null && !stack.isEmpty()) {
            SamsaraFabricQuesting.collectItemSubject.processPlayerObtainedStack(player, stack);
        }
    }

}

