package bor.samsara.questing;

import bor.samsara.questing.entity.QuestVillager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

@Deprecated
public class GlowingLogic {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);


    public static void init() {
        // Run this code each server tick
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            server.getPlayerManager().getPlayerList().forEach(player -> {
                applyGlowingIfLookingAtQuestVillager(player);
            });
        });
    }

    private static void applyGlowingIfLookingAtQuestVillager(ServerPlayerEntity player) {
        World world = player.getWorld();

        // Perform a raycast for interaction distance
        double reachDistance = 5.0D; // typical block reach
        HitResult hitResult = player.raycast(reachDistance, 0.0F, false);
        log.info("{}", hitResult);

        if (hitResult.getType() == HitResult.Type.ENTITY) {
            log.info("entity hit");
            if (hitResult instanceof EntityHitResult entityHitResult) {
                log.info("{}", entityHitResult);
                if (entityHitResult.getEntity() instanceof VillagerEntity villager) {
                    if (entityHitResult.getEntity().getCommandTags().contains("questNPC")) {
                        villager.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.GLOWING, 10, 0, false, false)
                        );
                    }
                }
            }
        }
    }
}
