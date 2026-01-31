package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.*;

public class MongoQuest {

    private final String uuid;
    private String title;
    private String summary;
    private String description;
    private String submissionTarget;
    private boolean rendersInQuestLog = true;
    private boolean providesQuestBook = true;
    private List<String> dialogue = new ArrayList<>();
    private List<Objective> objectives = new ArrayList<>();
    private MongoQuest.Reward reward;
    private EnumMap<EventTrigger, List<String>> triggers = new EnumMap<>(EventTrigger.class);
    private CategoryEnum category;

    public MongoQuest(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubmissionTarget() {
        return submissionTarget;
    }

    public void setSubmissionTarget(String submissionTarget) {
        this.submissionTarget = submissionTarget;
    }

    public boolean rendersInQuestLog() {
        return rendersInQuestLog;
    }

    public void setRendersInQuestLog(boolean rendersInQuestLog) {
        this.rendersInQuestLog = rendersInQuestLog;
    }

    public boolean doesProvideQuestBook() {
        return providesQuestBook;
    }

    public void setProvidesQuestBook(boolean providesQuestBook) {
        this.providesQuestBook = providesQuestBook;
    }

    public List<String> getDialogue() {
        return dialogue;
    }

    public void setDialogue(List<String> dialogue) {
        this.dialogue = dialogue;
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(List<Objective> objectives) {
        this.objectives = objectives;
    }

    public MongoQuest.Reward getReward() {
        return reward;
    }

    public void setReward(MongoQuest.Reward reward) {
        this.reward = reward;
    }

    public List<String> getTriggers(EventTrigger eventTrigger) {
        return triggers.getOrDefault(eventTrigger, Collections.emptyList());
    }

    public boolean addTriggers(EventTrigger trigger, List<String> commands) {
        triggers.putIfAbsent(trigger, new ArrayList<>());
        return triggers.get(trigger).addAll(commands);
    }

    private void setTriggers(EnumMap<EventTrigger, List<String>> triggers) {
        this.triggers = triggers;
    }

    public CategoryEnum getCategory() {
        return category;
    }

    public void setCategory(CategoryEnum categoryEnum) {
        this.category = categoryEnum;
    }

    public enum CategoryEnum {
        MAIN,
        SIDE,
        TUTORIAL,
        END
    }

    public static class Objective {
        private MongoQuest.Objective.Type type; // e.g., "kill"
        private String target; // e.g., "zombie"
        private int requiredCount; // e.g., 5

        public Objective() {}

        public Objective(MongoQuest.Objective.Type type, String target, int requiredCount) {
            this.type = type;
            this.target = target;
            this.requiredCount = requiredCount;
        }

        public MongoQuest.Objective.Type getType() {
            return type;
        }

        public void setType(MongoQuest.Objective.Type type) {
            this.type = type;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public int getRequiredCount() {
            return requiredCount;
        }

        public void setRequiredCount(int requiredCount) {
            this.requiredCount = requiredCount;
        }

        public enum Type {
            KILL,
            TALK,
            COLLECT,
            DO_QUEST,
            SET_SPAWN,
            BREAK_BLOCK
        }

        public Document toDocument() {
            return new Document()
                    .append("type", type.name())
                    .append("target", target)
                    .append("requiredCount", requiredCount);
        }

        public static MongoQuest.Objective fromDocument(Document document) {
            MongoQuest.Objective o = new MongoQuest.Objective();
            o.setType(MongoQuest.Objective.Type.valueOf(document.getString("type")));
            o.setTarget(document.getString("target"));
            o.setRequiredCount(document.getInteger("requiredCount"));
            return o;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Objective objective = (Objective) o;
            return requiredCount == objective.requiredCount && type == objective.type && Objects.equals(target, objective.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, target, requiredCount);
        }

        @Override
        public String toString() {
            return "Objective{" +
                    "type=" + type +
                    ", target='" + target + '\'' +
                    ", requiredCount=" + requiredCount +
                    '}';
        }
    }

    public static class Reward {
        private String itemName;
        private int count;
        private int xpValue;

        public Reward() {}

        public Reward(String itemName, int count, int xpValue) {
            this.itemName = itemName;
            this.count = count;
            this.xpValue = xpValue;
        }

        public String getItemName() {
            return itemName;
        }

        public void setItemName(String itemName) {
            this.itemName = itemName;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getXpValue() {
            return xpValue;
        }

        public void setXpValue(int xpValue) {
            this.xpValue = xpValue;
        }

        public Document toDocument() {
            return new Document()
                    .append("itemName", itemName)
                    .append("count", count)
                    .append("xpValue", xpValue);
        }

        public static MongoQuest.Reward fromDocument(Document document) {
            MongoQuest.Reward r = new MongoQuest.Reward();
            r.setItemName(document.getString("itemName"));
            r.setCount(document.getInteger("count"));
            r.setXpValue(document.getInteger("xpValue"));
            return r;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            Reward reward = (Reward) o;
            return count == reward.count && xpValue == reward.xpValue && Objects.equals(itemName, reward.itemName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemName, count, xpValue);
        }

        @Override
        public String toString() {
            return "Reward{" +
                    "itemName='" + itemName + '\'' +
                    ", count=" + count +
                    ", xpValue=" + xpValue +
                    '}';
        }
    }

    public enum EventTrigger {
        ON_INIT,
        ON_START, // TODO rename ON_BOOK_GRANT or similar
        ON_DIALOGUE_DONE, // Essentially same as on_book_grant but can happen every time
        ON_COMPLETE
    }

    public Document toDocument() {
        List<Document> objectiveDocs = new ArrayList<>();
        if (objectives != null) {
            for (Objective obj : objectives) {
                objectiveDocs.add(obj.toDocument());
            }
        }
        Document triggersDoc = new Document();
        if (triggers != null && !triggers.isEmpty()) {
            for (EventTrigger event : triggers.keySet()) {
                triggersDoc.append(event.name(), triggers.get(event));
            }
        }
        return new Document()
                .append("uuid", uuid)
                .append("title", title)
                .append("summary", summary)
                .append("description", description)
                .append("submissionTarget", submissionTarget)
                .append("rendersInQuestLog", rendersInQuestLog)
                .append("providesQuestBook", providesQuestBook)
                .append("dialogue", dialogue)
                .append("objectives", objectiveDocs)
                .append("reward", reward == null ? null : reward.toDocument())
                .append("triggers", triggersDoc.isEmpty() ? null : triggersDoc)
                .append("category", category == null ? CategoryEnum.MAIN : category.name());
    }

    @SuppressWarnings("unchecked")
    public MongoQuest fromDocument(Document document) {
        MongoQuest q = new MongoQuest(document.getString("uuid"));
        q.setTitle(document.getString("title"));
        q.setDialogue(document.getList("dialogue", String.class));
        q.setSummary(document.getString("summary"));
        q.setSubmissionTarget(document.getString("submissionTarget"));
        q.setRendersInQuestLog(document.getBoolean("rendersInQuestLog", true));
        q.setProvidesQuestBook(document.getBoolean("providesQuestBook", true));
        q.setDescription(document.getString("description"));
        List<Objective> objectives = new ArrayList<>();
        List<Document> objectiveDocuments = document.getList("objectives", Document.class);
        if (null != objectiveDocuments) {
            for (Document objDoc : objectiveDocuments) {
                objectives.add(Objective.fromDocument(objDoc));
            }
        }
        q.setObjectives(objectives);
        q.setReward(null == document.get("reward", Document.class) ? null : Reward.fromDocument(document.get("reward", Document.class)));

        // Parse triggers map
        EnumMap<EventTrigger, List<String>> triggersMap = new EnumMap<>(EventTrigger.class);
        Document triggersDoc = document.get("triggers", Document.class);
        if (null != triggersDoc) {
            for (String key : triggersDoc.keySet()) {
                EventTrigger event = EventTrigger.valueOf(key);
                List<String> commands = triggersDoc.getList(key, String.class);
                triggersMap.put(event, commands);
            }
        }
        q.setTriggers(triggersMap);

        q.setCategory(null == document.getString("category") ? CategoryEnum.MAIN : CategoryEnum.valueOf(document.getString("category")));
        return q;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MongoQuest that = (MongoQuest) o;
        return providesQuestBook == that.providesQuestBook && Objects.equals(uuid, that.uuid) && Objects.equals(title, that.title) && Objects.equals(summary, that.summary) && Objects.equals(description, that.description) && Objects.equals(submissionTarget, that.submissionTarget) && Objects.equals(dialogue, that.dialogue) && Objects.equals(objectives, that.objectives) && Objects.equals(reward, that.reward) && Objects.equals(triggers, that.triggers) && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, title, summary, description, submissionTarget, providesQuestBook, dialogue, objectives, reward, triggers, category);
    }

    @Override
    public String toString() {
        return "MongoQuest{" +
                "uuid='" + uuid + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", description='" + description + '\'' +
                ", submissionTarget='" + submissionTarget + '\'' +
                ", providesQuestBook=" + providesQuestBook +
                ", dialogue=" + dialogue +
                ", objectives=" + objectives +
                ", reward=" + reward +
                ", triggers=" + triggers +
                ", category=" + category +
                '}';
    }
}
