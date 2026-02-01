package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BreakBlockSubject extends QuestEventSubject {

    Set<String> pertinentBlockNames = new HashSet<>();

    public void attach(ActionSubscription listener) {
        log.debug("{} Attached for {} ", this.getClass().getSimpleName(), listener);
        playerSubsriptionMap.putIfAbsent(listener.playerUuid(), new ArrayList<>());
        playerSubsriptionMap.get(listener.playerUuid()).add(listener);
        pertinentBlockNames.add(listener.objectiveTarget().toLowerCase());
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
                if (Strings.CI.equals(blockName, subscription.objectiveTarget())) {
                    MongoPlayer playerState = PlayerMongoClient.getPlayerByUuid(subscription.playerUuid());
                    MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.questUuid());
                    MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getProgress(subscription.objectiveUuid());

                    progress.setCurrentCount(progress.getCurrentCount() + 1);

                    processIncrementedObjective(player, progress, activeQuestState, playerState, subscription, ite);

                    PlayerMongoClient.updatePlayer(playerState);
                }
            }
        };
    }

    private void processIncrementedObjective(PlayerEntity player, MongoPlayer.ActiveQuestState.ObjectiveProgress progress,
                                             MongoPlayer.ActiveQuestState activeQuestState, MongoPlayer playerState,
                                             ActionSubscription subscription, Iterator<ActionSubscription> ite) {
        if (progress.getCurrentCount() >= progress.getObjective().getRequiredCount()) {
            log.debug("Signalling quest '{}', objective BREAK_BLOCK {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName());
            this.detach(subscription, ite);
            progress.setComplete(true);
            boolean isAllComplete = activeQuestState.getObjectiveProgressions().values().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
            activeQuestState.setAreAllObjectivesComplete(isAllComplete);
            if (isAllComplete) {
                Sounds.aroundPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, .6f, 1.0f);
            } else {
                Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_LEVELUP);
            }
        } else {
            log.debug("Incrementing quest objective count to {} for player {}", progress.getCurrentCount(), playerState.getName());
            Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
            Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
        }
    }

}
