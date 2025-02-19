package bor.samsara.questing.mongo.models;

import java.util.List;


@Deprecated(since = "0.0.1", forRemoval = true)
// TODO nest within MongoNpc.java
public class MongoQuest {

    private final String uuid;
    private List<String> stage;

    public MongoQuest(String uuid, String name) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

}
