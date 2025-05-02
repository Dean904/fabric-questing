package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
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
            if (StringUtils.equalsIgnoreCase(entity.getName().getString(), listener.getObjective().getTarget())) {
                QuestManager questManager = QuestManager.getInstance();
                boolean isComplete = questManager.incrementQuestObjectiveCount(listener);
                if (isComplete)
                    detach(listener, ite);
            }
        }
    }

}
