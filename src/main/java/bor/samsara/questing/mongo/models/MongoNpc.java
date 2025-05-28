package bor.samsara.questing.mongo.models;

import org.bson.Document;

import java.util.*;

public class MongoNpc implements MongoDao<MongoNpc> {

    private final String uuid;
    private String name;
    private String dialogueType;
    private List<String> questIds = new ArrayList<>();
    private boolean isStartNode;

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

    public String getDialogueType() {
        return dialogueType;
    }

    public void setDialogueType(String dialogueType) {
        this.dialogueType = dialogueType;
    }

    public List<String> getQuestIds() {
        return questIds;
    }

    public void setQuestIds(List<String> questIds) {
        this.questIds = questIds;
    }

    public boolean isStartNode() {
        return isStartNode;
    }

    public void setStartNode(boolean startNode) {
        isStartNode = startNode;
    }

    public Document toDocument() {
        return new Document("uuid", uuid)
                .append("name", name)
                .append("dialogueType", dialogueType)
                .append("questIds", questIds)
                .append("isStartNode", isStartNode);
    }

    @SuppressWarnings("unchecked")
    public MongoNpc fromDocument(Document document) {
        MongoNpc p = new MongoNpc(document.getString("uuid"), document.getString("name"));
        p.setDialogueType(document.getString("dialogueType"));
        p.setQuestIds(document.getList("questIds", String.class));
        p.isStartNode = document.getBoolean("isStartNode", false);
        return p;
    }

    @Override
    public String toString() {
        return "MongoNpc{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", dialogueType='" + dialogueType + '\'' +
                ", isStartNode=" + isStartNode +
                ", questIds=" + questIds +
                '}';
    }
}
