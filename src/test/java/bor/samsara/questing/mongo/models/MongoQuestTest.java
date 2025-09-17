package bor.samsara.questing.mongo.models;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MongoQuestTest {

    @Test
    void convertQuestToDocumentAndBack_ShouldPreserveAllFields() {
        // Arrange
        MongoQuest originalQuest = new MongoQuest();
        originalQuest.setTitle("Test Quest");
        originalQuest.setSequence(1);
        originalQuest.setSummary("Test Summary");
        originalQuest.setDescription("Test Description");
        originalQuest.setProvidesQuestBook(true);

        MongoQuest.Objective objective = new MongoQuest.Objective();
        objective.setType(MongoQuest.Objective.Type.KILL);
        objective.setTarget("zombie");
        objective.setRequiredCount(5);
        originalQuest.setObjective(objective);

        MongoQuest.Reward reward = new MongoQuest.Reward();
        reward.setItemName("diamond");
        reward.setCount(3);
        reward.setXpValue(100);
        originalQuest.setReward(reward);

        // Act
        Document doc = originalQuest.toDocument();
        MongoQuest convertedQuest = new MongoQuest(originalQuest.getUuid()).fromDocument(doc);

        // Assert
        assertEquals(originalQuest.getUuid(), convertedQuest.getUuid());
        assertEquals(originalQuest.getTitle(), convertedQuest.getTitle());
        assertEquals(originalQuest.getSequence(), convertedQuest.getSequence());
        assertEquals(originalQuest.getSummary(), convertedQuest.getSummary());
        assertEquals(originalQuest.getDescription(), convertedQuest.getDescription());
        assertEquals(originalQuest.doesProvideQuestBook(), convertedQuest.doesProvideQuestBook());

        // Verify objective
        assertEquals(originalQuest.getObjective().getType(), convertedQuest.getObjective().getType());
        assertEquals(originalQuest.getObjective().getTarget(), convertedQuest.getObjective().getTarget());
        assertEquals(originalQuest.getObjective().getRequiredCount(), convertedQuest.getObjective().getRequiredCount());

        // Verify reward
        assertEquals(originalQuest.getReward().getItemName(), convertedQuest.getReward().getItemName());
        assertEquals(originalQuest.getReward().getCount(), convertedQuest.getReward().getCount());
        assertEquals(originalQuest.getReward().getXpValue(), convertedQuest.getReward().getXpValue());

        assertEquals(originalQuest, convertedQuest);
    }

    @Test
    void convertQuestWithTriggerToDocumentAndBack_ShouldPreserveTrigger() {
        // Arrange
        MongoQuest originalQuest = new MongoQuest();
        MongoQuest.Trigger trigger = new MongoQuest.Trigger();
        trigger.setEvent(MongoQuest.Trigger.Event.ON_COMPLETE);
        trigger.setCommand("/give @p diamond");
        originalQuest.setTrigger(trigger);

        // Act
        Document doc = originalQuest.toDocument();
        MongoQuest convertedQuest = new MongoQuest(originalQuest.getUuid()).fromDocument(doc);

        // Assert
        assertNotNull(convertedQuest.getTrigger());
        assertEquals(trigger.getEvent(), convertedQuest.getTrigger().getEvent());
        assertEquals(trigger.getCommand(), convertedQuest.getTrigger().getCommand());
        assertEquals(originalQuest, convertedQuest);
    }
}
