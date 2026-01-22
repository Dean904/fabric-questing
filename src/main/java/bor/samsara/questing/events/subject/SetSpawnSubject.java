package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.Iterator;
import java.util.List;

public class SetSpawnSubject extends QuestEventSubject {

    public void onSetSpawnPoint(ServerPlayerEntity player, ServerPlayerEntity.Respawn respawn) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(playerUuid);
            MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
            MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                    .filter(op -> op.getObjective().getType() == MongoQuest.Objective.Type.SET_SPAWN).findFirst().orElseThrow();
            log.debug("Marking quest '{}', objective SET_SPAWN, complete for player {}", activeQuestState.getQuestTitle(), playerState.getName());
            progress.setComplete(true);
            progress.setCurrentCount(progress.getObjective().getRequiredCount());
            boolean isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
            activeQuestState.setAreAllObjectivesComplete(isAllComplete);
            PlayerMongoClient.updatePlayer(playerState);
            detach(subscription, ite);

            if (isAllComplete) {
                Sounds.aroundPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
            } else {
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_LEVELUP);
            }
        }

    }

}
