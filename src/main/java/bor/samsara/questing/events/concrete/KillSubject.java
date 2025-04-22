package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import bor.samsara.questing.mongo.models.MongoNpc;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;

import java.util.*;

public class KillSubject extends QuestEventSubject {

    Map<String, List<QuestListener>> playerSubscriberMap = new HashMap<>();

    @Override
    public void attach(QuestListener listener) {
        playerSubscriberMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubscriberMap.get(listener.getPlayerUuid()).add(listener);
    }

    @Override
    public void detach(QuestListener listener) {
        playerSubscriberMap.get(listener.getPlayerUuid()).remove(listener);
    }

    @Override
    public void detachPlayer(String playerUuid) {
        playerSubscriberMap.remove(playerUuid);
    }

    @Override
    public ServerEntityCombatEvents.AfterKilledOtherEntity hook() {
        return (world, killer, killedEntity) -> {
            String playerUuid = killer.getUuidAsString();
            if (!playerSubscriberMap.containsKey(playerUuid)) {
                return;
            }

            MongoNpc.Quest.Objective.Target killedType = switch (killedEntity) {
                case ZombieEntity z -> MongoNpc.Quest.Objective.Target.ZOMBIE;
                case SkeletonEntity s -> MongoNpc.Quest.Objective.Target.SKELETON;
                case CreeperEntity c -> MongoNpc.Quest.Objective.Target.CREEPER;
                default -> null;
            };

            List<QuestListener> questListeners = playerSubscriberMap.get(playerUuid);
            for (Iterator<QuestListener> ite = questListeners.iterator(); ite.hasNext(); ) {
                QuestListener listener = ite.next();
                if (killedType == listener.getObjective().getTarget()) {
                    QuestManager questManager = QuestManager.getInstance();
                    boolean isComplete = questManager.incrementQuestObjectiveCount(listener);
                    if (isComplete)
                        ite.remove();
                }
            }


        };

    }


}