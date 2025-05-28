package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MongoPlayer implements MongoDao<MongoPlayer> {

    private final String uuid;
    private String name;
    private Map<String, QuestProgress> npcActiveQuestMap = new HashMap<>();

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

    public Map<String, QuestProgress> getNpcQuestProgressMap() {
        return npcActiveQuestMap;
    }

    public void setNpcActiveQuestMap(Map<String, QuestProgress> npcActiveQuestMap) {
        this.npcActiveQuestMap = npcActiveQuestMap;
    }

    public Document toDocument() {
        Map<String, Document> activeQuestDocs = new HashMap<>();
        for (Map.Entry<String, QuestProgress> entry : npcActiveQuestMap.entrySet()) {
            activeQuestDocs.put(entry.getKey(), entry.getValue().toDocument());
        }

        return new Document("uuid", uuid)
                .append("name", name)
                .append("npcActiveQuest", activeQuestDocs);
    }

    @SuppressWarnings("unchecked")
    public MongoPlayer fromDocument(Document document) {
        MongoPlayer player = new MongoPlayer(document.getString("uuid"), document.getString("name"));
        Map<String, Document> activeQuestDocs = document.get("npcActiveQuest", Map.class);
        Map<String, QuestProgress> activeQuestMap = new HashMap<>();
        for (Map.Entry<String, Document> entry : activeQuestDocs.entrySet()) {
            activeQuestMap.put(entry.getKey(), QuestProgress.fromDocument(entry.getValue()));
        }
        player.setNpcActiveQuestMap(activeQuestMap);
        return player;

    }

    public static class QuestProgress {
        private final String questUuid;
        private final String questTitle;
        @Deprecated
        private final int sequence;
        private long dialogueOffset = 0;
        private int objectiveCount = 0;
        private boolean isComplete = false;

        public QuestProgress(String questUuid, String questTitle, int sequence) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.sequence = sequence;
        }

        public String getQuestUuid() {
            return questUuid;
        }

        @Deprecated
        public int getSequence() {
            return sequence;
        }

        public long getDialogueOffset() {
            return dialogueOffset;
        }

        public void setDialogueOffset(long dialogueOffset) {
            this.dialogueOffset = dialogueOffset;
        }

        public int getObjectiveCount() {
            return objectiveCount;
        }

        public void setObjectiveCount(int objectiveCount) {
            this.objectiveCount = objectiveCount;
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
                    .append("isComplete", isComplete);
        }

        public static QuestProgress fromDocument(Document document) {
            QuestProgress aq = new QuestProgress(document.getString("questUuid"), document.getString("questTitle"), document.getInteger("sequence"));
            aq.setDialogueOffset(document.getLong("dialogueOffset"));
            aq.setObjectiveCount(document.getInteger("objectiveCount", 0));
            aq.setComplete(document.getBoolean("isComplete", false));
            return aq;
        }
    }
}
