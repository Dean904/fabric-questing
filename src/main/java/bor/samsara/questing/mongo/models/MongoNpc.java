package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.*;

public class MongoNpc implements MongoDao<MongoNpc> {

    private final String uuid;
    private String name;
    private Map<Integer, Quest> quests = new HashMap<>();

    public MongoNpc() {
        this.uuid = UUID.randomUUID().toString();
    }

    public MongoNpc(String uuid, String name) {
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

    public Map<Integer, Quest> getQuests() {
        return quests;
    }

    public void setQuests(Map<Integer, Quest> quests) {
        this.quests = quests;
    }

    public static class Quest {

        private Integer sequence;
        private List<String> dialogue = new ArrayList<>();
        private Objective objective;
        private Reward reward;

        public Integer getSequence() {
            return sequence;
        }

        public void setSequence(Integer sequence) {
            this.sequence = sequence;
        }

        public List<String> getDialogue() {
            return dialogue;
        }

        public void setDialogue(List<String> dialogue) {
            this.dialogue = dialogue;
        }

        public Objective getObjective() {
            return objective;
        }

        public void setObjective(Objective objective) {
            this.objective = objective;
        }

        public Reward getReward() {
            return reward;
        }

        public void setReward(Reward reward) {
            this.reward = reward;
        }

        public static class Objective {
            private Type type; // e.g., "kill"
            private String target; // e.g., "zombie"
            private int requiredCount; // e.g., 5

            public Objective() {}

            public Objective(Type type, String target, int requiredCount) {
                this.type = type;
                this.target = target;
                this.requiredCount = requiredCount;
            }

            public Type getType() {
                return type;
            }

            public void setType(Type type) {
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

            public static Objective fromDocument(Document document) {
                Objective o = new Objective();
                o.setType(Type.valueOf(document.getString("type")));
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

            public static Reward fromDocument(Document document) {
                Reward r = new Reward();
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

        public Document toDocument() {
            return new Document()
                    .append("dialogue", dialogue)
                    .append("order", sequence)
                    .append("objective", objective.toDocument())
                    .append("reward", reward.toDocument());
        }

        @SuppressWarnings("unchecked")
        public static Quest fromDocument(Document document) {
            Quest q = new Quest();
            q.setDialogue(document.getList("dialogue", String.class));
            q.setSequence(document.getInteger("order"));
            q.setObjective(Objective.fromDocument(document.get("objective", Document.class)));
            q.setReward(Reward.fromDocument(document.get("reward", Document.class)));
            return q;
        }

        @Override
        public String toString() {
            return "Quest{" +
                    "sequence=" + sequence +
                    ", dialogue=" + dialogue +
                    ", objective=" + objective +
                    ", reward=" + reward +
                    '}';
        }
    }

    public Document toDocument() {
        Map<String, Document> questDocs = new HashMap<>();
        for (Quest quest : quests.values()) {
            questDocs.put(String.valueOf(quest.getSequence()), quest.toDocument());
        }

        return new Document("uuid", uuid)
                .append("name", name)
                .append("quests", questDocs);
    }

    @SuppressWarnings("unchecked")
    public MongoNpc fromDocument(Document document) {
        MongoNpc p = new MongoNpc(document.getString("uuid"), document.getString("name"));

        Map<String, Document> questDocs = document.get("quests", Map.class);
        Map<Integer, Quest> questMap = new HashMap<>();
        for (Map.Entry<String, Document> entry : questDocs.entrySet()) {
            questMap.put(Integer.valueOf(entry.getKey()), Quest.fromDocument(entry.getValue()));
        }
        p.setQuests(questMap);

        return p;
    }

    @Override
    public String toString() {
        return "MongoNpc{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", quests=" + quests +
                '}';
    }
}
