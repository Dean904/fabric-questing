package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MongoPlayer implements MongoDao<MongoPlayer> {

    // TODO update schema and finish to/from doc
    private final String uuid;
    private String name;

    Map<Integer, List<String>> questLog = new HashMap<>();

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

    public Document toDocument() {
        Document doc = new Document("uuid", uuid)
                .append("name", name);

        return doc;
    }

    @SuppressWarnings("unchecked")
    public MongoPlayer fromDocument(Document document) {
        MongoPlayer p = new MongoPlayer(document.getString("uuid"), document.getString("name"));

        return p;
    }
}
