package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BreakBlockSubject extends QuestEventSubject {

    Set<String> pertinentBlockNames = new HashSet<>();

    public void attach(ActionSubscription listener) {
        log.debug("{} Attached for {} ", this.getClass().getSimpleName(), listener);
        playerSubsriptionMap.putIfAbsent(listener.getPlayerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.getPlayerUuid()).add(listener);
        pertinentBlockNames.add(listener.getObjectiveTarget().toLowerCase());
    }

    public PlayerBlockBreakEvents.@NotNull After processAfterBlockBreak() {
        return (world, player, pos, state, blockEntity) -> {

            String playerUuid = player.getUuidAsString();
            if (!playerSubsriptionMap.containsKey(playerUuid))
                return;

            String blockName = state.getBlock().getName().getString();
            if (!pertinentBlockNames.contains(blockName.toLowerCase()))
                return;

            List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
            for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
                ActionSubscription subscription = ite.next();
                if (StringUtils.equalsIgnoreCase(blockName, subscription.getObjectiveTarget())) {
                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.getPlayerUuid());
                    MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                    MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                            .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getObjective().getTarget(), blockName)).findFirst().orElseThrow();

                    progress.setCurrentCount(progress.getCurrentCount() + 1);

                    if (progress.getCurrentCount() >= progress.getObjective().getRequiredCount()) {
                        log.debug("Signalling quest '{}', objective BREAK_BLOCK {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName());
                        progress.setComplete(true);
                        boolean isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                        this.detach(subscription, ite);
                        if (isAllComplete) {
                            player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.4f, 1.0f);
                        } else {
                            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.0f);
                        }
                    } else {
                        log.debug("Incrementing quest objective count to {} for player {}", progress.getCurrentCount(), playerState.getName());
                        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                        player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.9f);
                    }

                    PlayerMongoClient.updatePlayer(playerState);
                }
            }
        };
    }

}
