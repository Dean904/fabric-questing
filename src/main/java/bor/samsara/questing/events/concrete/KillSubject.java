package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
                    QuestManager questManager = QuestManager.getInstance();
                    boolean isComplete = questManager.incrementQuestObjectiveCount(listener);
                    killer.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
                    killer.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
                    if (isComplete) {
                        killer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        detach(listener, ite);
                    }
                }
            }
        };
    }

}