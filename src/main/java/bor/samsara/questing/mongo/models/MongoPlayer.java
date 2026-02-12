package bor.samsara.questing.mongo.models;

import net.minecraft.util.math.BlockPos;
import org.bson.Document;

import java.util.*;
import java.util.stream.Collectors;

public class MongoPlayer {

    private final String uuid;
    private String name;

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

        return new Document("uuid", uuid)
                .append("name", name)
                .append("activeQuestStates", activeQuestDocs)
                .append("completedQuestIds", completedQuestIds)
                .append("npcActiveQuest", npcActiveQuestMap);
    }

    @SuppressWarnings("unchecked")
    public static MongoPlayer fromDocument(Document document) {
        MongoPlayer player = new MongoPlayer(document.getString("uuid"), document.getString("name"));
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
        private final boolean rendersInQuestLog;
        private Map<UUID, ObjectiveProgress> objectiveProgressions = new HashMap<>();
        private boolean areAllObjectivesComplete = false;
        private boolean receivedQuestBook = false;

        public ActiveQuestState(String questUuid, String questTitle, MongoQuest.CategoryEnum categoryEnum, boolean rendersInQuestLog) {
            this.questUuid = questUuid;
            this.questTitle = questTitle;
            this.categoryEnum = categoryEnum;
            this.rendersInQuestLog = rendersInQuestLog;
        }

        public ActiveQuestState(MongoQuest quest) {
            this.questUuid = quest.getUuid();
            this.questTitle = quest.getTitle();
            this.categoryEnum = quest.getCategory();
            this.rendersInQuestLog = quest.rendersInQuestLog();
            this.objectiveProgressions.putAll(quest.getObjectives().stream().collect(Collectors.toMap(MongoQuest.Objective::getUuid, ObjectiveProgress::new)));
        }

        public boolean isObjectiveComplete(MongoQuest.Objective objective) {
            return objectiveProgressions.get(objective.getUuid()).isComplete();
        }

        public ObjectiveProgress getProgress(MongoQuest.Objective objective) {
            return objectiveProgressions.get(objective.getUuid());
        }

        public ObjectiveProgress getProgress(UUID objectiveUuid) {
            return objectiveProgressions.get(objectiveUuid);
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

        public Map<UUID, ObjectiveProgress> getObjectiveProgressions() {
            return objectiveProgressions;
        }

        public void setObjectiveProgressions(Map<UUID, ObjectiveProgress> objectiveProgressions) {
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

        public boolean rendersInQuestLog() {
            return rendersInQuestLog;
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
            for (ObjectiveProgress entry : objectiveProgressions.values()) {
                objectiveProgressDocs.add(entry.toDocument());
            }

            return new Document("questUuid", questUuid)
                    .append("questTitle", questTitle)
                    .append("category", categoryEnum.name())
                    .append("rendersInQuestLog", rendersInQuestLog)
                    .append("objectiveProgressions", objectiveProgressDocs)
                    .append("receivedQuestBook", receivedQuestBook)
                    .append("areAllObjectivesComplete", areAllObjectivesComplete);
        }

        public static ActiveQuestState fromDocument(Document document) {
            ActiveQuestState q = new ActiveQuestState(
                    document.getString("questUuid"),
                    document.getString("questTitle"),
                    MongoQuest.CategoryEnum.valueOf(document.getString("category")),
                    document.getBoolean("rendersInQuestLog")
            );
            q.setReceivedQuestBook(document.getBoolean("receivedQuestBook", false));
            q.setAreAllObjectivesComplete(document.getBoolean("areAllObjectivesComplete", false));
            List<Document> progressList = document.getList("objectiveProgressions", Document.class);
            Map<UUID, ObjectiveProgress> progressions = HashMap.newHashMap(progressList.size());
            for (Document objDoc : progressList) {
                ObjectiveProgress objectiveProgress = ObjectiveProgress.fromDocument(objDoc);
                progressions.put(objectiveProgress.getObjective().getUuid(), objectiveProgress);
            }
            q.setObjectiveProgressions(progressions);
            return q;
        }

        public static class ObjectiveProgress {
            private int currentCount = 0;
            private boolean isComplete = false;
            private MongoQuest.Objective objective;

            public ObjectiveProgress(MongoQuest.Objective objective) {
                this.objective = objective;
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

            public MongoQuest.Objective getObjective() {
                return objective;
            }

            public void setObjective(MongoQuest.Objective objective) {
                this.objective = objective;
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) return false;
                ObjectiveProgress that = (ObjectiveProgress) o;
                return currentCount == that.currentCount && isComplete == that.isComplete && Objects.equals(objective, that.objective);
            }

            @Override
            public int hashCode() {
                return Objects.hash(currentCount, isComplete, objective);
            }

            @Override
            public String toString() {
                return "ObjectiveProgress{" +
                        "currentCount=" + currentCount +
                        ", isComplete=" + isComplete +
                        ", objective=" + objective +
                        '}';
            }

            public Document toDocument() {
                return new Document("currentCount", currentCount)
                        .append("isComplete", isComplete)
                        .append("objective", objective.toDocument());
            }

            public static ObjectiveProgress fromDocument(Document document) {
                Document objectiveDoc = document.get("objective", Document.class);
                ObjectiveProgress op = new ObjectiveProgress(MongoQuest.Objective.fromDocument(objectiveDoc));
                op.setCurrentCount(document.getInteger("currentCount", 0));
                op.setComplete(document.getBoolean("isComplete", false));
                return op;
            }

        }
    }
}
