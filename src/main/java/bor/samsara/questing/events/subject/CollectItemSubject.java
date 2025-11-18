package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class CollectItemSubject extends QuestEventSubject {

    Set<String> pertinentItemNames = new HashSet<>();

    @Override
    public void attach(ActionSubscription listener) {
        playerSubsriptionMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.getPlayerUuid()).add(listener);
        pertinentItemNames.add(listener.getObjectiveTarget());
    }

    public void processAddStack(PlayerEntity player, int slot, ItemStack stack) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        String itemName = stack.getItem().toString();
        if (!pertinentItemNames.contains(itemName))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsIgnoreCase(itemName, subscription.getObjectiveTarget())) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.getPlayerUuid());
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                        .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), itemName)).findFirst().orElseThrow();

                int totalStackSize = player.getInventory().getStack(slot).getCount();
                boolean doesStackSizeProgressObjective = totalStackSize > progress.getCurrentCount();

                if (totalStackSize >= progress.getRequiredCount()) {
                    // Do not mark complete, need to submit to quest giver for completion
                    log.debug("Signalling quest '{}', objective COLLECT {}, fulfilled for player {}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName());
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.0f);
                    player.sendMessage(Text.literal("Return to the quest giver with " + progress.getRequiredCount() + " [" + itemName + "]!"), false);
                    this.detach(subscription, ite);
                }

                if (doesStackSizeProgressObjective) {
                    log.debug("Incrementing quest objective count to {} for player {}", totalStackSize, playerState.getName());
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.9f);
                }


                if (progress.isComplete() || doesStackSizeProgressObjective) {
                    progress.setCurrentCount(totalStackSize);
                    PlayerMongoClient.updatePlayer(playerState);
                }
            }
        }
    }

    public void processPlayerObtainedStack(PlayerEntity player, ItemStack stack) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        String itemName = stack.getItem().toString();
        if (!pertinentItemNames.contains(itemName))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsIgnoreCase(itemName, subscription.getObjectiveTarget())) {
                MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.getPlayerUuid());
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                        .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), itemName)).findFirst().orElseThrow();

                // compute player's total count for that item across inventory
                int totalCount = 0;
                for (int i = 0; i < player.getInventory().size(); i++) {
                    totalCount += player.getInventory().getStack(i).getCount();
                }

                boolean doesStackSizeProgressObjective = totalCount > progress.getCurrentCount();

                if (totalCount >= progress.getRequiredCount()) {
                    log.debug("Signalling quest '{}', objective COLLECT {}, fulfilled for player {}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName());
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.0f);
                    player.sendMessage(Text.literal("Return to the quest giver with " + progress.getRequiredCount() + " [" + itemName + "]!"), false);
                    this.detach(subscription, ite);
                }

                if (doesStackSizeProgressObjective) {
                    log.debug("Incrementing quest objective count to {} for player {}", totalCount, playerState.getName());
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.9f);
                }

                if (progress.isComplete() || doesStackSizeProgressObjective) {
                    progress.setCurrentCount(totalCount);
                    PlayerMongoClient.updatePlayer(playerState);
                }
            }
        }
    }

}
