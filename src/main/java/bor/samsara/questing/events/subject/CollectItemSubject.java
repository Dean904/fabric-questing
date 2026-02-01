package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.apache.commons.lang3.Strings;

import java.util.*;

public class CollectItemSubject extends QuestEventSubject {

    Set<String> pertinentItemNames = new HashSet<>();

    @Override
    public void attach(ActionSubscription listener) {
        log.debug("{} Attached for {} ", this.getClass().getSimpleName(), listener);
        playerSubsriptionMap.putIfAbsent(listener.playerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.playerUuid()).add(listener);
        pertinentItemNames.add(listener.objectiveTarget());
    }

    public void processAddStackFromGround(PlayerEntity player, int slot, ItemStack stack) {
        extracted(player, stack, player.getInventory().count(stack.getItem()));
    }

    public void processPlayerClickedUpStack(PlayerEntity player, ItemStack stack) {
        extracted(player, stack, stack.getCount() + player.getInventory().count(stack.getItem()));
    }

    private void extracted(PlayerEntity player, ItemStack stack, int totalStackSize) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        String itemName = stack.getItem().toString();
        if (!pertinentItemNames.contains(itemName))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (Strings.CI.equals(itemName, subscription.objectiveTarget())) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.playerUuid());
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.questUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getProgress(subscription.objectiveUuid());

                if (totalStackSize >= progress.getObjective().getRequiredCount()) {
                    // Do not mark complete, need to submit to quest giver for completion
                    player.sendMessage(Text.literal("Return to the quest giver with " + progress.getObjective().getRequiredCount() + " [" + itemName + "]!"), false);
                    log.debug("Signalling quest '{}', objective COLLECT {}, fulfilled for player {}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName());
                    progress.setCurrentCount(totalStackSize);
                    PlayerMongoClient.updatePlayer(playerState);
                    Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_LEVELUP);
                    this.detach(subscription, ite);
                } else if (totalStackSize > progress.getCurrentCount()) {
                    log.debug("Incrementing quest objective count to {} for player {}", totalStackSize, playerState.getName());
                    progress.setCurrentCount(totalStackSize);
                    PlayerMongoClient.updatePlayer(playerState);
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                }
            }
        }
    }


}
