package bor.samsara.questing.events;

import bor.samsara.questing.mongo.models.MongoQuest;

public class ActionSubscription {

    private final String playerUuid;
    private final String questNpcUuid;
    private final MongoQuest.Objective objective;

    public ActionSubscription(String playerUuid, String questNpcUuid, MongoQuest.Objective objective) {
        this.playerUuid = playerUuid;
        this.questNpcUuid = questNpcUuid;
        this.objective = objective;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getQuestNpcUuid() {
        return questNpcUuid;
    }

    public MongoQuest.Objective getObjective() {
        return objective;
    }

    @Override
    public String toString() {
        return "QuestListener{" +
                "playerUuid='" + playerUuid + '\'' +
                ", questUuid='" + questNpcUuid + '\'' +
                ", objective=" + objective +
                '}';
    }
}
