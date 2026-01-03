package bor.samsara.questing.mongo.models;

import net.minecraft.util.math.BlockPos;
import org.bson.Document;

import java.util.*;

public class MongoPlayer {

    private final String uuid;
    private String name;
    private BlockPos deathPosition;
    private String deathDimension;

    private Map<String, String> npcActiveQuestMap = new HashMap<>();
    private Map<String, ActiveQuestState> activeQuestProgressionMap = new HashMap<>();
    private List<String> completedQuestIds = new ArrayList<>();

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

    public BlockPos getDeathPosition() {
        return deathPosition;
    }

    public void setDeathPosition(BlockPos deathPosition) {
        this.deathPosition = deathPosition;
    }

    public String getDeathDimension() {
        return deathDimension;
    }

    public void setDeathDimension(String deathDimension) {
        this.deathDimension = deathDimension;
    }

    public boolean hasPlayerProgressedNpc(String npcUuid) {
        return npcActiveQuestMap.containsKey(npcUuid);
    }

    public String getCurrentQuestForNpc(String npcUuid) {
        return npcActiveQuestMap.get(npcUuid);
    }

    public ActiveQuestState getProgressForQuest(String questUuid) {
        return activeQuestProgressionMap.get(questUuid);
    }

    public void setCurrentQuestForNpc(String npcUuid, String questUuid) {
        npcActiveQuestMap.put(npcUuid, questUuid);
    }

    public void removeActiveQuestForNpc(String npcUuid) {
        npcActiveQuestMap.remove(npcUuid);
    }

    public void attachActiveQuestState(ActiveQuestState progress) {
        activeQuestProgressionMap.put(progress.getQuestUuid(), progress);
    }

    protected void setNpcActiveQuestMap(Map<String, String> npcActiveQuestMap) {
        this.npcActiveQuestMap = npcActiveQuestMap;
    }

    /**
     * keyed by quest uuid, returns the players quest progress
     *
     * @return QuestProgress
     */
    public Map<String, ActiveQuestState> getActiveQuestProgressionMap() {
        return activeQuestProgressionMap;
    }

    private void setActiveQuestProgressionMap(Map<String, ActiveQuestState> questPlayerProgressMap) {
        this.activeQuestProgressionMap = questPlayerProgressMap;
    }

    private void setCompletedQuestIds(List<String> completedQuestIds) {
        this.completedQuestIds = completedQuestIds;
    }

    public void markQuestComplete(String questUuid) {
        completedQuestIds.add(questUuid);
        activeQuestProgressionMap.remove(questUuid);
    }

    public boolean isQuestComplete(String questUuid) {
        return completedQuestIds.contains(questUuid);
    }


    public Document toDocument() {
        Map<String, Document> activeQuestDocs = new HashMap<>();
        for (Map.Entry<String, ActiveQuestState> entry : activeQuestProgressionMap.entrySet()) {
            activeQuestDocs.put(entry.getKey(), entry.getValue().toDocument());
        }

        Document doc = new Document("uuid", uuid)
                .append("name", name)
                .append("deathDimension", deathDimension)
                .append("activeQuestStates", activeQuestDocs)
                .append("completedQuestIds", completedQuestIds)
                .append("npcActiveQuest", npcActiveQuestMap);

        if (deathPosition != null) {
            doc.append("deathPosition", deathPosition.asLong());
        }
        return doc;
    }

    @SuppressWarnings("unchecked")
    public static MongoPlayer fromDocument(Document document) {
        MongoPlayer player = new MongoPlayer(document.getString("uuid"), document.getString("name"));
        player.setDeathPosition(document.containsKey("deathPosition") ? BlockPos.fromLong(document.getLong("deathPosition")) : null);
        player.setDeathDimension(document.getString("deathDimension"));
        player.setNpcActiveQuestMap(document.get("npcActiveQuest", Map.class));
        player.setCompletedQuestIds(document.getList("completedQuestIds", String.class));

        Map<String, ActiveQuestState> questProgressMap = new HashMap<>();
        Map<String, Document> questProgressionMap = document.get("activeQuestStates", Map.class);
        for (Map.Entry<String, Document> entry : questProgressionMap.entrySet()) {
            questProgressMap.put(entry.getKey(), ActiveQuestState.fromDocument(entry.getValue()));
        }
        player.setActiveQuestProgressionMap(questProgressMap);
        return player;
    }

    public static class ActiveQuestState {
        private final String questUuid;
        private final String questTitle;
        private final MongoQuest.CategoryEnum categoryEnum;
        private final boolean isSubmissionExpected;
        private List<ObjectiveProgress> objectiveProgressions = new ArrayList<>();
        private boolean areAllObjectivesComplete = false;
        private boolean receivedQuestBook = false;

        public ActiveQuestState(String questUuid, String questTitle, MongoQuest.CategoryEnum categoryEnum, boolean isSubmissionExpected) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.categoryEnum = categoryEnum;
            this.isSubmissionExpected = isSubmissionExpected;
        }

        public ActiveQuestState(MongoQuest quest) {
            this.questUuid = quest.getUuid();
            this.questTitle = quest.getTitle();
            this.categoryEnum = quest.getCategory();
            this.isSubmissionExpected = quest.getReward() != null || quest.getTrigger() != null;
            this.objectiveProgressions.addAll(quest.getObjectives().stream().map(o -> new ObjectiveProgress(o.getRequiredCount(), o.getTarget())).toList());
        }

        public String getQuestUuid() {
            return questUuid;
        }

        public String getQuestTitle() {
            return questTitle;
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

        public MongoQuest.CategoryEnum getCategory() {
            return categoryEnum;
        }

        public boolean isSubmissionExpected() {
            return isSubmissionExpected;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ActiveQuestState that = (ActiveQuestState) o;
            return areAllObjectivesComplete == that.areAllObjectivesComplete && receivedQuestBook == that.receivedQuestBook && Objects.equals(questUuid, that.questUuid) && Objects.equals(questTitle, that.questTitle) && Objects.equals(objectiveProgressions, that.objectiveProgressions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questUuid, questTitle, objectiveProgressions, areAllObjectivesComplete, receivedQuestBook);
        }

        public Document toDocument() {

            List<Document> objectiveProgressDocs = new ArrayList<>();
            for (ObjectiveProgress entry : objectiveProgressions) {
                objectiveProgressDocs.add(entry.toDocument());
            }

            return new Document("questUuid", questUuid)
                    .append("questTitle", questTitle)
                    .append("category", categoryEnum.name())
                    .append("isSubmissionExpected", isSubmissionExpected)
                    .append("objectiveProgressions", objectiveProgressDocs)
                    .append("receivedQuestBook", receivedQuestBook)
                    .append("areAllObjectivesComplete", areAllObjectivesComplete);
        }

        public static ActiveQuestState fromDocument(Document document) {
            ActiveQuestState q = new ActiveQuestState(
                    document.getString("questUuid"),
                    document.getString("questTitle"),
                    MongoQuest.CategoryEnum.valueOf(document.getString("category")),
                    document.getBoolean("isSubmissionExpected")
            );
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
