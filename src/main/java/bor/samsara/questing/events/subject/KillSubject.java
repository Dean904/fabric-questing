package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.events.SamsaraNoteBlockTunes;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;

public class KillSubject extends QuestEventSubject {

    public ServerEntityCombatEvents.AfterKilledOtherEntity processEntityKill() {
        return (world, killer, killedEntity, damageSource) -> {
            String playerUuid = killer.getUuidAsString();
            if (!playerSubsriptionMap.containsKey(playerUuid))
                return;

            List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
            String entityTypeName = killedEntity.getType().getName().getString();
            log.debug("Tracking {}, killed a {}", killer.getName().getString(), entityTypeName);
            for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
                ActionSubscription subscription = ite.next();
                if (StringUtils.equalsIgnoreCase(entityTypeName, subscription.getObjectiveTarget())) {
                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.getPlayerUuid());
                    MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                    MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                            .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getObjective().getTarget(), entityTypeName)).findFirst().orElseThrow();

                    int objectiveCount = progress.getCurrentCount() + 1;
                    progress.setCurrentCount(objectiveCount);
                    MongoQuest.Objective objective = progress.getObjective();
                    log.debug("Incrementing quest '{}' objective KILL {} for player {}: {}/{}", activeQuestState.getQuestTitle(), objective.getTarget(), playerState.getName(), objectiveCount, objective.getRequiredCount());

                    boolean isAllComplete = false;
                    if (objective.getRequiredCount() <= objectiveCount) {
                        progress.setComplete(true);
                        log.debug("Marking quest '{}', objective KILL {}, complete for player {}", activeQuestState.getQuestTitle(), objective.getTarget(), playerState.getName());
                        isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                        detach(subscription, ite);
                    }

                    PlayerMongoClient.updatePlayer(playerState);
                    playCompletionSoundsToWorld(world, isAllComplete, progress, killedEntity.getAttackingPlayer());
                }
            }
        };
    }

    static void playCompletionSoundsToWorld(ServerWorld world, boolean isAllComplete, MongoPlayer.ActiveQuestState.ObjectiveProgress progress, PlayerEntity player) {
        Vec3d pos = player.getBlockPos().toCenterPos();
        if (isAllComplete) {
            world.playSound(
                    null, // `null` means only the player hears it; use `player` to make it audible to others too
                    pos.x, pos.y, pos.z,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.PLAYERS,
                    0.6f, // volume
                    1.0f  // pitch
            );
        } else if (progress.isComplete()) {
            world.playSound(
                    null, // `null` means only the player hears it; use `player` to make it audible to others too
                    pos.x, pos.y, pos.z,
                    SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS,
                    0.6f, // volume
                    1.0f  // pitch
            );
        } else {
            SamsaraNoteBlockTunes.playZeldaTune(player);
        }
    }
}