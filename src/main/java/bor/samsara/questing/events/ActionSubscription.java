package bor.samsara.questing.events;

import bor.samsara.questing.mongo.models.MongoQuest;

public class ActionSubscription {

    private final String playerUuid;
    private final String questUuid; // TODO does not need to be npc uuid - replace with quest uuid
    private final MongoQuest.Objective objective;

    public ActionSubscription(String playerUuid, String questUuid, MongoQuest.Objective objective) {
        this.playerUuid = playerUuid;
        this.questUuid = questUuid;
        this.objective = objective;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getQuestUuid() {
        return questUuid;
    }

    public MongoQuest.Objective getObjective() {
        return objective;
    }

    @Override
    public String toString() {
        return "QuestListener{" +
                "playerUuid='" + playerUuid + '\'' +
                ", questUuid='" + questUuid + '\'' +
                ", objective=" + objective +
                '}';
    }
}
