package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MongoPlayer implements MongoDao<MongoPlayer> {

    private final String uuid;
    private String name;
    private Map<String, Integer> npcActiveQuest = new HashMap<>();

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

    public Integer getActiveQuestForNpc(String npcUuid) {
        npcActiveQuest.putIfAbsent(npcUuid, 0);
        return npcActiveQuest.get(npcUuid);
    }

    public void setActiveQuestForNpc(String npcUuid, Integer activeQuest) {
        npcActiveQuest.put(npcUuid, activeQuest);
    }

    public Document toDocument() {
        return new Document("uuid", uuid)
                .append("name", name)
                .append("npcActiveQuest", npcActiveQuest);
    }

    @SuppressWarnings("unchecked")
    public MongoPlayer fromDocument(Document document) {
        MongoPlayer player = new MongoPlayer(document.getString("uuid"), document.getString("name"));
        npcActiveQuest = document.get("npcActiveQuest", Map.class);

        return player;
    }
}
