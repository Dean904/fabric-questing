package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.entity.Entity;
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

    public void talkedToQuestNpc(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubscriberMap.containsKey(playerUuid))
            return;

        List<QuestListener> questListeners = playerSubscriberMap.get(playerUuid);
        for (Iterator<QuestListener> ite = questListeners.iterator(); ite.hasNext(); ) {
            QuestListener listener = ite.next();
            MongoNpc npc = NpcMongoClient.getFirstNpcByName(entity.getName().getString());
            if (StringUtils.equalsAnyIgnoreCase(listener.getObjective().getTarget(), npc.getName(), npc.getDialogueType())) {
                boolean isComplete = false;

                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(listener.getPlayerUuid());
                MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getNpcActiveQuestMap().get(listener.getQuestUuid());
                int objectiveCount = activeQuestForNpc.getObjectiveCount() + 1;
                activeQuestForNpc.setObjectiveCount(objectiveCount);

                MongoQuest staticQuest = QuestMongoClient.getQuestByUuid(activeQuestForNpc.getQuestUuid());

                log.debug("Incrementing quest objective count of '{}#{}' for player {}", npc.getName(), activeQuestForNpc.getSequence(), playerState.getName());

                if (null != staticQuest && staticQuest.getObjective().getRequiredCount() <= objectiveCount) {
                    activeQuestForNpc.setComplete(true);
                    PlayerMongoClient.updatePlayer(playerState);
                    log.debug("Marking '{}#{}' quest complete for player {}", npc.getName(), activeQuestForNpc.getSequence(), playerState.getName());
                    isComplete = true;
                }

                PlayerMongoClient.updatePlayer(playerState);

                if (isComplete)
                    detach(listener, ite);
            }
        }
    }

}
