package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.events.QuestListener;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

public class CollectItemSubject extends QuestEventSubject {

    Set<String> pertinentItemNames = new HashSet<>();

    @Override
    public Object hook() {
        return null;
    }

    @Override
    public void attach(QuestListener listener) {
        playerSubscriberMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubscriberMap.get(listener.getPlayerUuid()).add(listener);
        pertinentItemNames.add(listener.getObjective().getTarget());
    }

    public void processAddStack(PlayerEntity player, int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubscriberMap.containsKey(playerUuid))
            return;

        String itemName = stack.getItem().toString();
        if (!pertinentItemNames.contains(itemName))
            return;

        List<QuestListener> questListeners = playerSubscriberMap.get(playerUuid);
        for (Iterator<QuestListener> ite = questListeners.iterator(); ite.hasNext(); ) {
            QuestListener listener = ite.next();
            if (StringUtils.equalsIgnoreCase(itemName, listener.getObjective().getTarget())) {

                QuestManager questManager = QuestManager.getInstance();
                // TODO set objective count to count picked up, not just 1
                if (player.getInventory().getStack(slot).getCount() + stack.getCount() > questManager.getQuestObjectiveCount(listener)) {
                    boolean isComplete = questManager.incrementQuestObjectiveCount(listener);
                    if (isComplete) {
                        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                        detach(listener, ite);
                    } else {
                        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    }
                }
            }
        }

    }

}
