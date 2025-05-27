package bor.samsara.questing.events;

import bor.samsara.questing.mongo.models.MongoQuest;

public class QuestListener {

    private final String playerUuid;
    private final String questUuid;
    private final MongoQuest.Objective objective;

    public QuestListener(String playerUuid, String questNpcUuid, MongoQuest.Objective objective) {
        this.playerUuid = playerUuid;
        this.questUuid = questNpcUuid;
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
