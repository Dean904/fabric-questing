package bor.samsara.questing.events.concrete;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.events.QuestEventSubject;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
    public void attach(ActionSubscription listener) {
        playerSubsriptionMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.getPlayerUuid()).add(listener);
        pertinentItemNames.add(listener.getObjective().getTarget());
    }

    public void processAddStack(PlayerEntity player, int slot, ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        String itemName = stack.getItem().toString();
        if (!pertinentItemNames.contains(itemName))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsIgnoreCase(itemName, subscription.getObjective().getTarget())) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.getPlayerUuid());
                MongoPlayer.QuestProgress questProgress = playerState.getNpcQuestProgressMap().get(subscription.getQuestNpcUuid());

                int totalStackSize = player.getInventory().getStack(slot).getCount();
                boolean doesStackSizeCompleteObjective = totalStackSize >= subscription.getObjective().getRequiredCount();
                boolean doesStackSizeProgressObjective = totalStackSize > questProgress.getObjectiveCount();

                if (doesStackSizeCompleteObjective) {
                    log.debug("Marking {} quest complete for player {}", subscription.getObjective().getType(), playerState.getName());
                    questProgress.setComplete(true);
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    this.detach(subscription, ite);
                } else if (doesStackSizeProgressObjective) {
                    log.debug("Incrementing quest objective count to {} for player {}", totalStackSize, playerState.getName());
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.9f);
                }

                if (doesStackSizeCompleteObjective || doesStackSizeProgressObjective) {
                    questProgress.setObjectiveCount(totalStackSize);
                    PlayerMongoClient.updatePlayer(playerState);
                }
            }
        }
    }

}
