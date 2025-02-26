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
        // TODO add objective
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

        public Document toDocument() {
            return new Document()
                    .append("dialogue", dialogue)
                    .append("order", sequence);
        }

        @SuppressWarnings("unchecked")
        public static Quest fromDocument(Document document) {
            Quest q = new Quest();
            q.setDialogue(document.getList("dialogue", String.class));
            q.setSequence(document.getInteger("order"));
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
