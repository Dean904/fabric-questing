package bor.samsara.questing.events;

public class ActionSubscription {

    private final String playerUuid;
    private final String questUuid;
    private final String objectiveTarget;

    public ActionSubscription(String playerUuid, String questUuid, String objectiveTarget) {
        this.playerUuid = playerUuid;
        this.questUuid = questUuid;
        this.objectiveTarget = objectiveTarget;
    }

    public String getPlayerUuid() {
        return playerUuid;
    }

    public String getQuestUuid() {
        return questUuid;
    }

    public String getObjectiveTarget() {
        return objectiveTarget;
    }

    @Override
    public String toString() {
        return "QuestListener{" +
                "playerUuid='" + playerUuid + '\'' +
                ", questUuid='" + questUuid + '\'' +
                ", objectiveTarget=" + objectiveTarget +
                '}';
    }
}
