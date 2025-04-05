package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MongoPlayer implements MongoDao<MongoPlayer> {

    private final String uuid;
    private String name;
    private Map<String, ActiveQuest> npcActiveQuest = new HashMap<>();

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

    public Map<String, ActiveQuest> getNpcActiveQuest() {
        return npcActiveQuest;
    }

    public void setNpcActiveQuest(Map<String, ActiveQuest> npcActiveQuest) {
        this.npcActiveQuest = npcActiveQuest;
    }

    public ActiveQuest getActiveQuestForNpc(String npcUuid) {
        npcActiveQuest.putIfAbsent(npcUuid, new ActiveQuest(0));
        return npcActiveQuest.get(npcUuid);
    }

    public void advanceActiveQuestForNpc(String npcUuid) {
        ActiveQuest q = npcActiveQuest.get(npcUuid);
        npcActiveQuest.put(npcUuid, new ActiveQuest(q.getSequence() + 1));
    }

    public Document toDocument() {
        Map<String, Document> activeQuestDocs = new HashMap<>();
        for (Map.Entry<String, ActiveQuest> entry : npcActiveQuest.entrySet()) {
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
        Map<String, ActiveQuest> activeQuestMap = new HashMap<>();
        for (Map.Entry<String, Document> entry : activeQuestDocs.entrySet()) {
            activeQuestMap.put(entry.getKey(), ActiveQuest.fromDocument(entry.getValue()));
        }
        player.setNpcActiveQuest(activeQuestMap);
        return player;

    }

    public static class ActiveQuest {
        private final int sequence;
        private long dialogueOffset = 0;
        private int objectiveCount = 0;
        private boolean isComplete = false;

        public ActiveQuest(int sequence) {
            this.sequence = sequence;
        }

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
            return new Document("sequence", sequence)
                    .append("dialogueOffset", dialogueOffset)
                    .append("objectiveCount", objectiveCount)
                    .append("isComplete", isComplete);
        }

        public static ActiveQuest fromDocument(Document document) {
            ActiveQuest aq = new ActiveQuest(document.getInteger("sequence"));
            aq.setDialogueOffset(document.getLong("dialogueOffset"));
            aq.setObjectiveCount(document.getInteger("objectiveCount", 0));
            aq.setComplete(document.getBoolean("isComplete", false));
            return aq;
        }
    }
}
