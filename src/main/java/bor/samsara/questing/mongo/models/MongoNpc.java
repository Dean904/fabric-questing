package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.*;

public class MongoNpc implements MongoDao<MongoNpc> {

    private final String uuid;
    private String name;
    // TODO look into JOML for vector3f @see https://github.com/JOML-CI/JOML/tree/main
    private int pos_x, pos_y, pos_z;

    Map<Integer, List<String>> stageConversationMap = new HashMap<>();

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

    public int getPos_x() {
        return pos_x;
    }

    public void setPos_x(int pos_x) {
        this.pos_x = pos_x;
    }

    public int getPos_y() {
        return pos_y;
    }

    public void setPos_y(int pos_y) {
        this.pos_y = pos_y;
    }

    public int getPos_z() {
        return pos_z;
    }

    public void setPos_z(int pos_z) {
        this.pos_z = pos_z;
    }

    public Map<Integer, List<String>> getStageConversationMap() {
        return stageConversationMap;
    }

    public void setStageConversationMap(Map<Integer, List<String>> stageConversationMap) {
        this.stageConversationMap = stageConversationMap;
    }

    public Document toDocument() {
        Document doc = new Document("uuid", uuid)
                .append("name", name)
                .append("pos_x", pos_x)
                .append("pos_y", pos_y)
                .append("pos_z", pos_z);

        // Convert the stageConversationMap into a list of { stage: X, lines: [ ... ] } documents
        List<Document> stages = new ArrayList<>();
        for (Map.Entry<Integer, List<String>> e : stageConversationMap.entrySet()) {
            Document stageDoc = new Document("stage", e.getKey());
            stageDoc.append("lines", e.getValue());
            stages.add(stageDoc);
        }
        doc.append("stageConversationMap", stages);

        return doc;
    }

    @SuppressWarnings("unchecked")
    public MongoNpc fromDocument(Document document) {
        MongoNpc p = new MongoNpc(document.getString("uuid"), document.getString("name"));
        p.setPos_x(document.getInteger("pos_x"));
        p.setPos_y(document.getInteger("pos_y"));
        p.setPos_z(document.getInteger("pos_z"));

        // Read the conversation map
        List<Document> stages = (List<Document>) document.get("stageConversationMap");
        if (stages != null) {
            for (Document stageDoc : stages) {
                int stageId = stageDoc.getInteger("stage");
                List<String> lines = (List<String>) stageDoc.get("lines");
                p.stageConversationMap.put(stageId, lines);
            }
        }
        return p;
    }

}
