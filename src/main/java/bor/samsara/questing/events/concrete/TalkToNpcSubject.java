package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;

import java.util.Iterator;
import java.util.List;

public class TalkToNpcSubject extends QuestEventSubject {

    @Override
    public Object hook() {
        // TODO whats the 'talk' workflow? Quest to Talk to NPC = COmpletion, opens dialogue on target ?
        return null;
    }

    public void talkedToQuestNpc(PlayerEntity player, World world, Hand hand, EntityHitResult hitResult, MongoPlayer playerState, MongoNpc mongoNpc) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsAnyIgnoreCase(subscription.getObjective().getTarget(), mongoNpc.getName(), mongoNpc.getDialogueType())) {
                boolean isComplete = false;

                MongoPlayer.QuestProgress questProgress = playerState.getQuestPlayerProgressMap().get(subscription.getQuestUuid());
                int objectiveCount = questProgress.getObjectiveCount() + 1;
                questProgress.setObjectiveCount(objectiveCount);

                MongoQuest staticQuest = QuestMongoClient.getQuestByUuid(questProgress.getQuestUuid());

                log.debug("Incrementing quest objective count of '{}#{}' for player {}", mongoNpc.getName(), questProgress.getSequence(), playerState.getName());

                if (null != staticQuest && staticQuest.getObjective().getRequiredCount() <= objectiveCount) {
                    questProgress.setComplete(true);
                    PlayerMongoClient.updatePlayer(playerState);
                    log.debug("Marking '{}#{}' quest complete for player {}", mongoNpc.getName(), questProgress.getSequence(), playerState.getName());
                    isComplete = true;
                }

                PlayerMongoClient.updatePlayer(playerState);

                if (isComplete)
                    detach(subscription, ite);
            }
        }
    }

}
