package bor.samsara.questing.mixin;

import bor.samsara.questing.events.QuestNpcs;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Mixin(VillagerEntity.class)
public class VillagerEntityMixin {

    private static final Logger log = LoggerFactory.getLogger(MOD_ID);

    static {
        log.info("Loading VillagerEntityMixin...");
    }

    @Inject(method = "onStruckByLightning", at = @At("HEAD"), cancellable = true)
    private void samsaraPreventQuestNpcWitchTransformation(ServerWorld world, LightningEntity lightning, CallbackInfo ci) {
        VillagerEntity villager = (VillagerEntity) (Object) this;
        if (villager.getCommandTags().contains(QuestNpcs.QUEST_NPC)) {
            log.info("Preventing quest NPC {} from transforming into a witch due to lightning strike.", villager.getName().getString());
            ci.cancel();
        }
    }
}

