package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.QuestMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
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
            if (!playerSubscriberMap.containsKey(playerUuid))
                return;

            List<QuestListener> questListeners = playerSubscriberMap.get(playerUuid);
            String entityTypeName = killedEntity.getType().getName().getString();
            log.debug("Tracking {}, killed a {}", killer.getName().getString(), entityTypeName);
            for (Iterator<QuestListener> ite = questListeners.iterator(); ite.hasNext(); ) {
                QuestListener listener = ite.next();
                if (StringUtils.equalsIgnoreCase(entityTypeName, listener.getObjective().getTarget())) {
                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(listener.getPlayerUuid());
                    MongoPlayer.ActiveQuest activeQuestForNpc = playerState.getNpcActiveQuestMap().get(listener.getQuestUuid());
                    int objectiveCount = activeQuestForNpc.getObjectiveCount() + 1;
                    activeQuestForNpc.setObjectiveCount(objectiveCount);

                    MongoNpc npc = NpcMongoClient.getNpc(listener.getQuestUuid());
                    MongoQuest staticQuest = QuestMongoClient.getQuestByUuid(activeQuestForNpc.getQuestUuid());
                    log.debug("Incrementing quest objective count of '{}#{}' for player {}", npc.getName(), activeQuestForNpc.getSequence(), playerState.getName());

                    boolean isComplete = false;
                    if (null != staticQuest && staticQuest.getObjective().getRequiredCount() <= objectiveCount) {
                        activeQuestForNpc.setComplete(true);
                        PlayerMongoClient.updatePlayer(playerState);
                        log.debug("Marking '{}#{}' quest complete for player {}", npc.getName(), activeQuestForNpc.getSequence(), playerState.getName());
                        isComplete = true;
                    }

                    PlayerMongoClient.updatePlayer(playerState);
                    Vec3d pos = killer.getPos();

                    if (isComplete) {
                        world.playSound(
                                null, // `null` means only the player hears it; use `player` to make it audible to others too
                                pos.x, pos.y, pos.z,
                                SoundEvents.ENTITY_PLAYER_LEVELUP,
                                SoundCategory.PLAYERS,
                                0.6f, // volume
                                1.0f  // pitch
                        );
                        detach(listener, ite);
                    } else {
                        world.playSound(
                                null, // `null` means only the player hears it; use `player` to make it audible to others too
                                pos.x, pos.y, pos.z,
                                SoundEvents.BLOCK_ANVIL_USE,
                                SoundCategory.PLAYERS,
                                0.4f, // volume
                                0.6f  // pitch
                        );
                    }
                }
            }
        };
    }

}