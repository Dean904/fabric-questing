package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MongoQuest implements MongoDao<MongoQuest> {

    private final String uuid;
    private String title;
    private Integer sequence;
    private String summary;
    private String description;
    private boolean providesQuestBook = true;
    private List<String> dialogue = new ArrayList<>();
    private MongoQuest.Objective objective;
    private MongoQuest.Reward reward;
    private MongoQuest.Trigger trigger;

    public MongoQuest() {
        this.uuid = UUID.randomUUID().toString();
    }

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

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
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

    public MongoQuest.Objective getObjective() {
        return objective;
    }

    public void setObjective(MongoQuest.Objective objective) {
        this.objective = objective;
    }

    public MongoQuest.Reward getReward() {
        return reward;
    }

    public void setReward(MongoQuest.Reward reward) {
        this.reward = reward;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
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
            FIN
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
        public String toString() {
            return "Reward{" +
                    "itemName='" + itemName + '\'' +
                    ", count=" + count +
                    ", xpValue=" + xpValue +
                    '}';
        }
    }

    public static class Trigger {
        Event event; // e.g., "onStart", "onComplete"
        String command; // e.g., "/give @p minecraft:diamond 1"

        public enum Event {
            ON_START,
            ON_COMPLETE;
        }

        public Event getEvent() {
            return event;
        }

        public void setEvent(Event event) {
            this.event = event;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public Document toDocument() {
            return new Document()
                    .append("event", event.name())
                    .append("command", command);
        }

        public static MongoQuest.Trigger fromDocument(Document document) {
            MongoQuest.Trigger t = new MongoQuest.Trigger();
            t.setEvent(Event.valueOf(document.getString("event")));
            t.setCommand(document.getString("command"));
            return t;
        }
    }

    public Document toDocument() {
        return new Document()
                .append("uuid", uuid)
                .append("title", title)
                .append("summary", summary)
                .append("description", description)
                .append("providesQuestBook", providesQuestBook)
                .append("dialogue", dialogue)
                .append("order", sequence)
                .append("objective", objective == null ? null : objective.toDocument())
                .append("reward", reward == null ? null : reward.toDocument())
                .append("trigger", trigger == null ? null : trigger.toDocument());
    }

    @SuppressWarnings("unchecked")
    public MongoQuest fromDocument(Document document) {
        MongoQuest q = new MongoQuest(document.getString("uuid"));
        q.setTitle(document.getString("title"));
        q.setDialogue(document.getList("dialogue", String.class));
        q.setSequence(document.getInteger("order"));
        q.setSummary(document.getString("summary"));
        q.setProvidesQuestBook(document.getBoolean("providesQuestBook", true));
        q.setDescription(document.getString("description"));
        q.setObjective(MongoQuest.Objective.fromDocument(document.get("objective", Document.class)));
        q.setReward(MongoQuest.Reward.fromDocument(document.get("reward", Document.class)));
        q.setTrigger(null == document.get("trigger", Document.class) ? null : MongoQuest.Trigger.fromDocument(document.get("trigger", Document.class)));
        return q;
    }

    @Override
    public String toString() {
        return "MongoQuest{" +
                "uuid='" + uuid + '\'' +
                ", title='" + title + '\'' +
                ", sequence=" + sequence +
                ", summary='" + summary + '\'' +
                ", description='" + description + '\'' +
                ", providesQuestBook=" + providesQuestBook +
                ", dialogue=" + dialogue +
                ", objective=" + objective +
                ", reward=" + reward +
                ", trigger=" + trigger +
                '}';
    }
}

