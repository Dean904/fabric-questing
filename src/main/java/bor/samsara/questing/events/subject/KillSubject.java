package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
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
import org.apache.commons.lang3.Strings;

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
                if (Strings.CI.equals(entityTypeName, subscription.objectiveTarget())) {
                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.playerUuid());
                    MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.questUuid());
                    MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getProgress(subscription.objectiveUuid());

                    int objectiveCount = progress.getCurrentCount() + 1;
                    progress.setCurrentCount(objectiveCount);
                    MongoQuest.Objective objective = progress.getObjective();
                    log.debug("Incrementing quest '{}' objective KILL {} for player {}: {}/{}", activeQuestState.getQuestTitle(), objective.getTarget(), playerState.getName(), objectiveCount, objective.getRequiredCount());

                    boolean isAllComplete = false;
                    if (objective.getRequiredCount() <= objectiveCount) {
                        progress.setComplete(true);
                        log.debug("Marking quest '{}', objective KILL {}, complete for player {}", activeQuestState.getQuestTitle(), objective.getTarget(), playerState.getName());
                        isAllComplete = activeQuestState.getObjectiveProgressions().values().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                        detach(subscription, ite);
                    }

                    PlayerMongoClient.updatePlayer(playerState);

                    if (isAllComplete) {
                        Sounds.aroundPlayer(killedEntity.getAttackingPlayer(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, .6f, 1.0f);
                    } else if (progress.isComplete()) {
                        Sounds.aroundPlayer(killedEntity.getAttackingPlayer(), SoundEvents.ENTITY_PLAYER_LEVELUP);
                    } else {
                        SamsaraNoteBlockTunes.playZeldaTune(killedEntity.getAttackingPlayer());
                    }
                }
            }
        };
    }

}