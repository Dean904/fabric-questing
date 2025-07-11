package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MongoPlayer implements MongoDao<MongoPlayer> {

    private final String uuid;
    private String name;
    private boolean hasReceivedSpawnHengeHearthStone = false;

    private Map<String, String> npcActiveQuestMap = new HashMap<>();
    private Map<String, QuestProgress> questPlayerProgressMap = new HashMap<>();

    public MongoPlayer() {
        this.uuid = UUID.randomUUID().toString();
    }

    public MongoPlayer(String uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean hasReceivedSpawnHengeHearthStone() {
        return hasReceivedSpawnHengeHearthStone;
    }

    public void setHasReceivedSpawnHengeHearthStone(boolean hasReceivedSpawnHengeHearthStone) {
        this.hasReceivedSpawnHengeHearthStone = hasReceivedSpawnHengeHearthStone;
    }

    public boolean hasPlayerProgressedNpc(String npcUuid) {
        return npcActiveQuestMap.containsKey(npcUuid);
    }

    public QuestProgress getProgressForNpc(String npcUuid) {
        String questUuid = npcActiveQuestMap.get(npcUuid);
        return questPlayerProgressMap.get(questUuid);
    }

    public void setActiveQuest(String npcUuid, String questUuid, QuestProgress progress) {
        npcActiveQuestMap.put(npcUuid, questUuid);
        questPlayerProgressMap.put(questUuid, progress);
    }

    protected Map<String, String> getNpcActiveQuestUuidMap() {
        return npcActiveQuestMap;
    }

    protected void setNpcActiveQuestMap(Map<String, String> npcActiveQuestMap) {
        this.npcActiveQuestMap = npcActiveQuestMap;
    }

    /**
     * keyed by quest uuid, returns the players quest progress
     *
     * @return QuestProgress
     */
    public Map<String, QuestProgress> getQuestPlayerProgressMap() {
        return questPlayerProgressMap;
    }

    public void setQuestPlayerProgressMap(Map<String, QuestProgress> questPlayerProgressMap) {
        this.questPlayerProgressMap = questPlayerProgressMap;
    }

    public Document toDocument() {
        Map<String, Document> activeQuestDocs = new HashMap<>();
        for (Map.Entry<String, QuestProgress> entry : questPlayerProgressMap.entrySet()) {
            activeQuestDocs.put(entry.getKey(), entry.getValue().toDocument());
        }

        return new Document("uuid", uuid)
                .append("name", name)
                .append("hasReceivedSpawnHengeHearthStone", hasReceivedSpawnHengeHearthStone)
                .append("questPlayerProgress", activeQuestDocs)
                .append("npcActiveQuest", npcActiveQuestMap);
    }

    @SuppressWarnings("unchecked")
    public MongoPlayer fromDocument(Document document) {
        MongoPlayer player = new MongoPlayer(document.getString("uuid"), document.getString("name"));
        player.setHasReceivedSpawnHengeHearthStone(document.getBoolean("hasReceivedSpawnHengeHearthStone", false));
        player.setNpcActiveQuestMap(document.get("npcActiveQuest", Map.class));
        Map<String, Document> questPlayerProgressDocs = document.get("questPlayerProgress", Map.class);
        Map<String, QuestProgress> activeQuestMap = new HashMap<>();
        for (Map.Entry<String, Document> entry : questPlayerProgressDocs.entrySet()) {
            activeQuestMap.put(entry.getKey(), QuestProgress.fromDocument(entry.getValue()));
        }
        player.setQuestPlayerProgressMap(activeQuestMap);
        return player;

    }

    public static class QuestProgress {
        private final String questUuid;
        private final String questTitle;
        @Deprecated
        private final int sequence;
        private int dialogueOffset = 0;
        private int objectiveCount = 0;
        private boolean receivedQuestBook = false;
        private boolean isComplete = false;

        public QuestProgress(String questUuid, String questTitle, int sequence) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.sequence = sequence;
        }

        public String getQuestUuid() {
            return questUuid;
        }

        public String getQuestTitle() {
            return questTitle;
        }

        @Deprecated
        public int getSequence() {
            return sequence;
        }

        public int getDialogueOffset() {
            return dialogueOffset;
        }

        public void setDialogueOffset(int dialogueOffset) {
            this.dialogueOffset = dialogueOffset;
        }

        public int getObjectiveCount() {
            return objectiveCount;
        }

        public void setObjectiveCount(int objectiveCount) {
            this.objectiveCount = objectiveCount;
        }

        public boolean hasReceivedQuestBook() {
            return receivedQuestBook;
        }

        public void setReceivedQuestBook(boolean receivedQuestBook) {
            this.receivedQuestBook = receivedQuestBook;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public void setComplete(boolean complete) {
            isComplete = complete;
        }

        public Document toDocument() {
            return new Document("questUuid", questUuid)
                    .append("questTitle", questTitle)
                    .append("sequence", sequence)
                    .append("dialogueOffset", dialogueOffset)
                    .append("objectiveCount", objectiveCount)
                    .append("receivedQuestBook", receivedQuestBook)
                    .append("isComplete", isComplete);
        }

        public static QuestProgress fromDocument(Document document) {
            QuestProgress q = new QuestProgress(document.getString("questUuid"), document.getString("questTitle"), document.getInteger("sequence"));
            q.setDialogueOffset(document.getInteger("dialogueOffset"));
            q.setObjectiveCount(document.getInteger("objectiveCount", 0));
            q.setReceivedQuestBook(document.getBoolean("receivedQuestBook", false));
            q.setComplete(document.getBoolean("isComplete", false));
            return q;
        }
    }
}
