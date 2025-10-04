package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
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

    @Override
    public Object hook() {
        // TODO whats the 'talk' workflow? Quest to Talk to NPC = COmpletion, opens dialogue on target ?
        return null;
    }

    public void talkedToQuestNpc(PlayerEntity player, World world, Hand hand, EntityHitResult hitResult, MongoPlayer playerState, MongoNpc mongoNpc) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsAnyIgnoreCase(subscription.getObjectiveTarget(), mongoNpc.getName(), mongoNpc.getDialogueType())) {
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = getObjectiveProgressForNpc(mongoNpc, activeQuestState);
                int objectiveCount = progress.getCurrentCount() + 1;
                progress.setCurrentCount(objectiveCount);
                log.debug("Incrementing quest '{}' objective TALK {} for player {}: {}/{}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName(), objectiveCount, progress.getRequiredCount());

                boolean isAllComplete = false;
                if (progress.getRequiredCount() <= objectiveCount) {
                    progress.setComplete(true);
                    log.debug("Marking quest '{}', objective TALK {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getTarget(), playerState.getName());
                    isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                    activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                    detach(subscription, ite);
                }

                PlayerMongoClient.updatePlayer(playerState);

                if (isAllComplete) {
                    player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.0f);
                } else if (progress.isComplete()) {
                    player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.0f);
                } else {
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.0f);
                    player.playSoundToPlayer(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0f, 1.9f);
                }
            }
        }
    }

    private static MongoPlayer.ActiveQuestState.@NotNull ObjectiveProgress getObjectiveProgressForNpc(MongoNpc mongoNpc, MongoPlayer.ActiveQuestState activeQuestState) {
        Optional<MongoPlayer.ActiveQuestState.ObjectiveProgress> first = activeQuestState.getObjectiveProgressions().stream()
                .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), mongoNpc.getName()))
                .findFirst();

        if (first.isEmpty()) {
            return activeQuestState.getObjectiveProgressions().stream()
                    .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), mongoNpc.getDialogueType()))
                    .findFirst().orElseThrow();
        }

        return first.orElseThrow();
    }

}
