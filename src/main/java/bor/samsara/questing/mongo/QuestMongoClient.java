package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoQuest;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;


public class QuestMongoClient {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String QUEST_COLLECTION = "quests";
    private static final MongoDatabase database = MongoDatabaseSingleton.getDatabase();
    private static final MongoCollection<Document> collection = database.getCollection(QUEST_COLLECTION);

    private QuestMongoClient() {}

    static {
        log.info("Creating ID index for MongoDB collection '{}'", QUEST_COLLECTION);
        collection.createIndex(Indexes.ascending("uuid"));
    }

    public static void createQuest(MongoQuest quest) {
        Document doc = quest.toDocument();
        collection.insertOne(doc);
    }

    public static MongoQuest getQuestByUuid(String questUuid) {
        Document query = new Document("uuid", questUuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return new MongoQuest(questUuid).fromDocument(doc);
        }
        throw new IllegalStateException("The MongoQuest '%s' was not found".formatted(questUuid));
    }

    public static void updateQuest(MongoQuest quest) {
        Document query = new Document("uuid", quest.getUuid());
        Document update = new Document("$set", quest.toDocument());
        collection.updateOne(query, update);
    }


    public static MongoQuest getQuestByTitle(String questTitle) {
        Document query = new Document("title", questTitle);
        Document doc = collection.find(query).first();

        if (null != doc && null != doc.getString("uuid")) {
            return new MongoQuest(doc.getString("uuid")).fromDocument(doc);
        }
        throw new IllegalStateException("The MongoQuest titled '%s' was not found".formatted(questTitle));
    }
}
