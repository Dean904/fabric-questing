package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.events.SamsaraNoteBlockTunes;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;

public class KillSubject extends QuestEventSubject {

    @Override
    public ServerEntityCombatEvents.AfterKilledOtherEntity hook() {
        return (world, killer, killedEntity) -> {
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
                            .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), entityTypeName)).findFirst().orElseThrow();

                    int objectiveCount = progress.getCurrentCount() + 1;
                    progress.setCurrentCount(objectiveCount);
                    log.debug("Incrementing quest '{}' objective KILL {} for player {}: {}/{}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName(), objectiveCount, progress.getRequiredCount());

                    boolean isAllComplete = false;
                    if (progress.getRequiredCount() <= objectiveCount) {
                        progress.setComplete(true);
                        log.debug("Marking quest '{}', objective KILL {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName());
                        isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                        detach(subscription, ite);
                    }

                    // TODO refactor sound playing into its own method for reuse
                    PlayerMongoClient.updatePlayer(playerState);
                    Vec3d pos = killer.getPos();
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
                        SamsaraNoteBlockTunes.playZeldaTune(killedEntity.getAttackingPlayer());
                    }
                }
            }
        };
    }

}