package bor.samsara.questing.mongo.models;

import java.util.List;


public class MongoQuest {
    private final String uuid;
    private List<String> stage;

    public MongoQuest(String uuid, String name) {
        this.uuid = uuid;
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

    public createStage(String name, String description, List<String> objectives) {
        return new Stage(name, description, objectives);
    }

    public class Stage {
        private String name;
        private String description;
        private List<String> objectives;
        public short stageNumber;

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getObjectives() {
            return objectives;
        }
    }
}
