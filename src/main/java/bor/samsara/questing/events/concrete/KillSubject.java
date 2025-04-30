package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.EntityType;
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

//            MongoNpc.Quest.Objective.Target killedType = switch (killedEntity) {
//                case ZombieEntity z -> MongoNpc.Quest.Objective.Target.ZOMBIE;
//                case SkeletonEntity s -> MongoNpc.Quest.Objective.Target.SKELETON;
//                case CreeperEntity c -> MongoNpc.Quest.Objective.Target.CREEPER;
//                default -> null;
//            };

            List<QuestListener> questListeners = playerSubscriberMap.get(playerUuid);
            String entityTypeName = killedEntity.getType().getName().getString();

            for (Iterator<QuestListener> ite = questListeners.iterator(); ite.hasNext(); ) {
                QuestListener listener = ite.next();
                if (StringUtils.equalsIgnoreCase(entityTypeName, listener.getObjective().getTarget())) {
                    QuestManager questManager = QuestManager.getInstance();
                    boolean isComplete = questManager.incrementQuestObjectiveCount(listener);
                    if (isComplete)
                        ite.remove();
                }
            }
        };
    }

}