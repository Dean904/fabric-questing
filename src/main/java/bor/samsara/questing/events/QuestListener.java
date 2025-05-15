package bor.samsara.questing.events;

import bor.samsara.questing.mongo.models.MongoNpc;

public class QuestListener {

    private final String playerUuid;
    private final String questUuid;
    private final MongoNpc.Quest.Objective objective;

    public QuestListener(String playerUuid, String questNpcUuid, MongoNpc.Quest.Objective objective) {
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

    public MongoNpc.Quest.Objective getObjective() {
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
