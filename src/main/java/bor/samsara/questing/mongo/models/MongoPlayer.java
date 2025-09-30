package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.*;

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
        // TODO rename to QuestState or PlauyerQuestState or QuestCompletion
        private final String questUuid;
        private final String questTitle;
        @Deprecated
        private final int sequence;
        private int dialogueOffset = 0;
        private List<ObjectiveProgress> objectiveProgressions = new ArrayList<>();
        private boolean areAllObjectivesComplete = false;
        private boolean receivedQuestBook = false;

        public QuestProgress(String questUuid, String questTitle, int sequence) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.sequence = sequence;
        }

        public QuestProgress(String questUuid, String questTitle, int sequence, List<MongoQuest.Objective> objectives) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.sequence = sequence;
            objectiveProgressions.addAll(objectives.stream().map(o -> new ObjectiveProgress(o.getRequiredCount(), o.getTarget())).toList());
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

        public boolean hasReceivedQuestBook() {
            return receivedQuestBook;
        }

        public void setReceivedQuestBook(boolean receivedQuestBook) {
            this.receivedQuestBook = receivedQuestBook;
        }

        public List<ObjectiveProgress> getObjectiveProgressions() {
            return objectiveProgressions;
        }

        public void setObjectiveProgressions(List<ObjectiveProgress> objectiveProgressions) {
            this.objectiveProgressions = objectiveProgressions;
        }

        public boolean areAllObjectivesComplete() {
            return areAllObjectivesComplete;
        }

        public void setAreAllObjectivesComplete(boolean areAllObjectivesComplete) {
            this.areAllObjectivesComplete = areAllObjectivesComplete;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            QuestProgress that = (QuestProgress) o;
            return sequence == that.sequence && dialogueOffset == that.dialogueOffset && areAllObjectivesComplete == that.areAllObjectivesComplete && receivedQuestBook == that.receivedQuestBook && Objects.equals(questUuid, that.questUuid) && Objects.equals(questTitle, that.questTitle) && Objects.equals(objectiveProgressions, that.objectiveProgressions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questUuid, questTitle, sequence, dialogueOffset, objectiveProgressions, areAllObjectivesComplete, receivedQuestBook);
        }

        public Document toDocument() {

            List<Document> objectiveProgressDocs = new ArrayList<>();
            for (ObjectiveProgress entry : objectiveProgressions) {
                objectiveProgressDocs.add(entry.toDocument());
            }

            return new Document("questUuid", questUuid)
                    .append("questTitle", questTitle)
                    .append("sequence", sequence)
                    .append("dialogueOffset", dialogueOffset)
                    .append("objectiveProgressions", objectiveProgressDocs)
                    .append("receivedQuestBook", receivedQuestBook)
                    .append("areAllObjectivesComplete", areAllObjectivesComplete);
        }

        public static QuestProgress fromDocument(Document document) {
            QuestProgress q = new QuestProgress(document.getString("questUuid"), document.getString("questTitle"), document.getInteger("sequence"));
            q.setDialogueOffset(document.getInteger("dialogueOffset"));
            q.setReceivedQuestBook(document.getBoolean("receivedQuestBook", false));
            q.setAreAllObjectivesComplete(document.getBoolean("areAllObjectivesComplete", false));
            List<ObjectiveProgress> progressions = new ArrayList<>();
            for (Document objDoc : document.getList("objectiveProgressions", Document.class)) {
                progressions.add(ObjectiveProgress.fromDocument(objDoc));
            }
            q.setObjectiveProgressions(progressions);

            return q;
        }

        public static class ObjectiveProgress {

            private int currentCount = 0;
            private boolean isComplete = false;

            private final int requiredCount;
            private final String target;

            public ObjectiveProgress(int requiredCount, String target) {
                this.requiredCount = requiredCount;
                this.target = target;
            }

            public int getCurrentCount() {
                return currentCount;
            }

            public void setCurrentCount(int currentCount) {
                this.currentCount = currentCount;
            }

            public boolean isComplete() {
                return isComplete;
            }

            public void setComplete(boolean complete) {
                isComplete = complete;
            }

            public int getRequiredCount() {
                return requiredCount;
            }

            public String getTarget() {
                return target;
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) return false;
                ObjectiveProgress that = (ObjectiveProgress) o;
                return currentCount == that.currentCount && isComplete == that.isComplete && requiredCount == that.requiredCount && Objects.equals(target, that.target);
            }

            @Override
            public int hashCode() {
                return Objects.hash(currentCount, isComplete, requiredCount, target);
            }

            @Override
            public String toString() {
                return "ObjectiveProgress{" +
                        "currentCount=" + currentCount +
                        ", isComplete=" + isComplete +
                        ", requiredCount=" + requiredCount +
                        ", target='" + target + '\'' +
                        '}';
            }

            public Document toDocument() {
                return new Document("currentCount", currentCount)
                        .append("requiredCount", requiredCount)
                        .append("target", target)
                        .append("isComplete", isComplete);
            }

            public static ObjectiveProgress fromDocument(Document document) {
                ObjectiveProgress op = new ObjectiveProgress(
                        document.getInteger("requiredCount", 0),
                        document.getString("target")
                );
                op.setCurrentCount(document.getInteger("currentCount", 0));
                op.setComplete(document.getBoolean("isComplete", false));
                return op;
            }

        }
    }
}
