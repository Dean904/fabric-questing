package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class TalkToNpcSubject extends QuestEventSubject {

    public void talkedToQuestNpc(PlayerEntity player, World world, Hand hand, EntityHitResult hitResult, MongoPlayer playerState, String npcUuid) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        MongoNpc mongoNpc = NpcMongoClient.getNpc(npcUuid);
        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsAnyIgnoreCase(subscription.getObjectiveTarget(), mongoNpc.getName(), mongoNpc.getDialogueType())) {
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = getObjectiveProgressForNpc(mongoNpc, activeQuestState);
                int objectiveCount = progress.getCurrentCount() + 1;
                progress.setCurrentCount(objectiveCount);
                log.debug("Incrementing quest '{}' objective TALK {} for player {}: {}/{}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName(), objectiveCount, progress.getObjective().getRequiredCount());

                boolean isAllComplete = false;
                if (progress.getObjective().getRequiredCount() <= objectiveCount) {
                    progress.setComplete(true);
                    log.debug("Marking quest '{}', objective TALK {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName());
                    isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                    activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                    detach(subscription, ite);
                }

                PlayerMongoClient.updatePlayer(playerState);

                if (isAllComplete) {
                    Sounds.aroundPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);
                } else if (progress.isComplete()) {
                    Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_LEVELUP);
                } else {
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                }
            }
        }
    }

    private static MongoPlayer.ActiveQuestState.@NotNull ObjectiveProgress getObjectiveProgressForNpc(MongoNpc mongoNpc, MongoPlayer.ActiveQuestState activeQuestState) {
        Optional<MongoPlayer.ActiveQuestState.ObjectiveProgress> first = activeQuestState.getObjectiveProgressions().stream()
                .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getObjective().getTarget(), mongoNpc.getName()))
                .findFirst();

        if (first.isEmpty()) {
            return activeQuestState.getObjectiveProgressions().stream()
                    .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getObjective().getTarget(), mongoNpc.getDialogueType()))
                    .findFirst().orElseThrow();
        }

        return first.orElseThrow();
    }

}
