package bor.samsara.questing.events.subject;

import bor.samsara.questing.events.ActionSubscription;
import bor.samsara.questing.mongo.PlayerMongoClient;
import bor.samsara.questing.mongo.models.MongoPlayer;
import bor.samsara.questing.mongo.models.MongoQuest;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DoQuestSubject extends QuestEventSubject {

    @Override
    public void attach(ActionSubscription listener) {
        throw new UnsupportedOperationException("Use the attach method overloaded with MongoPlayer parameter for DoQuestSubjects");
    }

    public void attach(ActionSubscription subscription, MongoPlayer playerState) {
        if (playerState.isQuestComplete(subscription.getObjectiveTarget())) {
            MongoPlayer.ActiveQuestState activeQuestState = markActiveQuestStateComplete(playerState, subscription);
            log.debug("Objective DO_QUEST {} already accomplished by player {}", activeQuestState.getQuestTitle(),  playerState.getName());
        } else {
            playerSubsriptionMap.putIfAbsent(subscription.getPlayerUuid(), new ArrayList<>());
            playerSubsriptionMap.get(subscription.getPlayerUuid()).add(subscription);
            log.debug("{} Attached for {} ", this.getClass().getSimpleName(), subscription);
        }
    }

    public void processQuestCompletion(PlayerEntity player, MongoPlayer playerState, MongoQuest completedQuest) {
        String playerUuid = player.getUuidAsString();
        if (!playerSubsriptionMap.containsKey(playerState.getUuid()))
            return;

        List<ActionSubscription> actionSubscriptions = playerSubsriptionMap.get(playerUuid);
        for (Iterator<ActionSubscription> ite = actionSubscriptions.iterator(); ite.hasNext(); ) {
            ActionSubscription subscription = ite.next();
            if (StringUtils.equalsIgnoreCase(subscription.getObjectiveTarget(), completedQuest.getUuid())) {
                MongoPlayer.ActiveQuestState activeQuestState = markActiveQuestStateComplete(playerState, subscription);
                log.debug("Marking DO_QUEST {} as completed by player {}", activeQuestState.getQuestTitle(),  playerState.getName());
                this.detach(subscription, ite);
                player.sendMessage(Text.literal("You can now progress the [" + activeQuestState.getQuestTitle() + "] quest line!")
                        .styled(style -> style.withColor(Formatting.GRAY).withBold(true)), false);

            }

        }

    }

    private static MongoPlayer.@NotNull ActiveQuestState markActiveQuestStateComplete(MongoPlayer playerState, ActionSubscription subscription) {
        MongoPlayer.ActiveQuestState activeQuestState = playerState.getActiveQuestProgressionMap().get(subscription.getQuestUuid());
        MongoPlayer.ActiveQuestState.ObjectiveProgress progress = activeQuestState.getObjectiveProgressions().stream()
                .filter(op -> StringUtils.equalsAnyIgnoreCase(op.getTarget(), subscription.getObjectiveTarget())).findFirst().orElseThrow();
        progress.setComplete(true);
        progress.setCurrentCount(1);
        boolean isAllComplete = activeQuestState.getObjectiveProgressions().stream().allMatch(MongoPlayer.ActiveQuestState.ObjectiveProgress::isComplete);
        activeQuestState.setAreAllObjectivesComplete(isAllComplete);
        PlayerMongoClient.updatePlayer(playerState);
        return activeQuestState;
    }


}
