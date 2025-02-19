package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class MongoPlayer {

    private final String uuid;
    private String name;

    Map<Integer, List<String>> questLog = new HashMap<>();


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
    public static MongoNpc fromDocument(Document document) {
        MongoNpc p = new MongoNpc(document.getString("uuid"), document.getString("name"));

        return p;
    }
}
