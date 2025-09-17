package bor.samsara.questing.mongo.models;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MongoPlayerTest {

    @Test
    void newPlayer_ShouldHaveEmptyQuestProgress() {
        // Arrange & Act
        MongoPlayer player = new MongoPlayer("test-uuid", "TestPlayer");

        // Assert
        assertFalse(player.hasReceivedSpawnHengeHearthStone());
        assertTrue(player.getQuestPlayerProgressMap().isEmpty());
        assertFalse(player.hasPlayerProgressedNpc("any-npc"));
    }

    @Test
    void setActiveQuest_ShouldUpdateBothMaps() {
        // Arrange
        MongoPlayer player = new MongoPlayer("test-uuid", "TestPlayer");
        String npcUuid = "npc-123";
        String questUuid = "quest-456";
        MongoPlayer.QuestProgress progress = new MongoPlayer.QuestProgress(questUuid, "Test Quest", 0);

        // Act
        player.setActiveQuest(npcUuid, questUuid, progress);

        // Assert
        assertTrue(player.hasPlayerProgressedNpc(npcUuid));
        assertNotNull(player.getProgressForNpc(npcUuid));
        assertEquals(questUuid, player.getProgressForNpc(npcUuid).getQuestUuid());
        assertEquals("Test Quest", player.getProgressForNpc(npcUuid).getQuestTitle());
    }

    @Test
    void convertPlayerToDocumentAndBack_ShouldPreserveAllFields() {
        // Arrange
        MongoPlayer originalPlayer = new MongoPlayer("test-uuid", "TestPlayer");
        originalPlayer.setHasReceivedSpawnHengeHearthStone(true);

        String npcUuid = "npc-123";
        String questUuid = "quest-456";
        MongoPlayer.QuestProgress progress = new MongoPlayer.QuestProgress(questUuid, "Test Quest", 1);
        progress.setObjectiveCount(5);
        progress.setComplete(true);
        progress.setReceivedQuestBook(true);
        progress.setDialogueOffset(2);

        originalPlayer.setActiveQuest(npcUuid, questUuid, progress);

        // Act
        Document doc = originalPlayer.toDocument();
        MongoPlayer convertedPlayer = new MongoPlayer().fromDocument(doc);

        // Assert
        assertEquals(originalPlayer.getUuid(), convertedPlayer.getUuid());
        assertEquals(originalPlayer.getName(), convertedPlayer.getName());
        assertEquals(originalPlayer.hasReceivedSpawnHengeHearthStone(), convertedPlayer.hasReceivedSpawnHengeHearthStone());

        // Verify quest progress
        MongoPlayer.QuestProgress convertedProgress = convertedPlayer.getProgressForNpc(npcUuid);
        assertNotNull(convertedProgress);
        assertEquals(progress.getQuestUuid(), convertedProgress.getQuestUuid());
        assertEquals(progress.getQuestTitle(), convertedProgress.getQuestTitle());
        assertEquals(progress.getObjectiveCount(), convertedProgress.getObjectiveCount());
        assertEquals(progress.isComplete(), convertedProgress.isComplete());
        assertEquals(progress.hasReceivedQuestBook(), convertedProgress.hasReceivedQuestBook());
        assertEquals(progress.getDialogueOffset(), convertedProgress.getDialogueOffset());
    }

    @Test
    void questProgress_ShouldImplementEqualsAndHashCodeCorrectly() {
        // Arrange
        MongoPlayer.QuestProgress progress1 = new MongoPlayer.QuestProgress("quest-1", "Quest Title", 0);
        MongoPlayer.QuestProgress progress2 = new MongoPlayer.QuestProgress("quest-1", "Quest Title", 0);
        MongoPlayer.QuestProgress differentProgress = new MongoPlayer.QuestProgress("quest-2", "Different Quest", 1);

        // Assert
        assertEquals(progress1, progress2);
        assertEquals(progress1.hashCode(), progress2.hashCode());
        assertNotEquals(progress1, differentProgress);
        assertNotEquals(progress1.hashCode(), differentProgress.hashCode());
    }
}
