package bor.samsara.questing.events.subject;

import bor.samsara.questing.Sounds;
import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.NpcMongoClient;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoNpc;
import bor.samsara.questing.mongo.models.MongoPlayer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import org.apache.commons.lang3.Strings;

import java.util.Iterator;
import java.util.List;

public class TalkToNpcSubject extends QuestEventSubject {

    public void talkedToQuestNpc(PlayerEntity player, World world, Hand hand, EntityHitResult hitResult, MongoPlayer playerState, String npcUuid) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerUuid))
            return;

        MongoNpc mongoNpc = NpcMongoClient.getNpc(npcUuid);
        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (Strings.CI.equalsAny(subscription.objectiveTarget(), mongoNpc.getName(), mongoNpc.getDialogueType())) {
                MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.questUuid());
                MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getProgress(subscription.objectiveUuid());
                int objectiveCount = progress.getCurrentCount() + 1;
                progress.setCurrentCount(objectiveCount);
                log.debug("Incrementing quest '{}' objective TALK {} for player {}: {}/{}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName(), objectiveCount, progress.getObjective().getRequiredCount());

                boolean isAllComplete = false;
                if (progress.getObjective().getRequiredCount() <= objectiveCount) {
                    progress.setComplete(true);
                    log.debug("Marking quest '{}', objective TALK {}, complete for player {}", activeQuestState.getQuestTitle(), progress.getObjective().getTarget(), playerState.getName());
                    isAllComplete = activeQuestState.getObjectiveProgressions().values().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
                    activeQuestState.setAreAllObjectivesComplete(isAllComplete);
                    detach(subscription, ite);
                }

                PlayerMongoClient.updatePlayer(playerState);

                if (isAllComplete) {
                    Sounds.aroundPlayer(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, .6f, 1.0f);
                } else if (progress.isComplete()) {
                    Sounds.aroundPlayer(player, SoundEvents.ENTITY_PLAYER_LEVELUP);
                } else {
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                    Sounds.aroundPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME);
                }
            }
        }
    }

}
