package bor.samsara.questing.mixin;

import bor.samsara.questing.SamsaraFabricQuesting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {

    @Unique
    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    static {
        log.info("Loading PlayerInventoryMixin...");
    }

    @Final
    @Shadow
    public PlayerEntity player;            // directly grab the field

    @Inject(method = "addStack(ILnet/minecraft/item/ItemStack;)I", at = @At("TAIL"))
    private void onAddStack(int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {

        SamsaraFabricQuesting.collectItemSubject.processAddStackFromGround(player, slot, stack);

        if (stack.isOf(Items.ROTTEN_FLESH)) {
            log.info("ZombieFlesh added to inventory of {}", player.getName());
        }
    }
}
