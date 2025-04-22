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
        // TODO add reward

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

        public static class Objective {
            private Type type; // e.g., "kill"
            private Target target; // e.g., "zombie"
            private int requiredCount; // e.g., 5

            public Objective() {}

            public Objective(Type type, Target target, int requiredCount) {
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

            public Target getTarget() {
                return target;
            }

            public void setTarget(Target target) {
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
                COLLECT
            }

            public enum Target {
                ZOMBIE,
                SKELETON,
                CREEPER
            }

            public Document toDocument() {
                return new Document()
                        .append("type", type.name())
                        .append("target", target.name())
                        .append("requiredCount", requiredCount);
            }

            public Objective fromDocument(Document document) {
                Objective o = new Objective();
                o.setType(Type.valueOf(document.getString("type")));
                o.setTarget(Target.valueOf(document.getString("target")));
                o.setRequiredCount(document.getInteger("requiredCount"));
                return o;
            }
        }


        public Document toDocument() {
            return new Document()
                    .append("dialogue", dialogue)
                    .append("order", sequence)
                    .append("objective", objective.toDocument());

        }

        @SuppressWarnings("unchecked")
        public static Quest fromDocument(Document document) {
            Quest q = new Quest();
            q.setDialogue(document.getList("dialogue", String.class));
            q.setSequence(document.getInteger("order"));
            q.setObjective(new Objective().fromDocument(document.get("objective", Document.class)));
            return q;
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
}
