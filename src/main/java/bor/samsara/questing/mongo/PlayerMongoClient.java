package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoPlayer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class PlayerMongoClient {

    private static final String PLAYER_COLLECTION = "playerCharacters";

    private static final MongoDatabase database = MongoDatabaseSingleton.getDatabase();

    public static void createPlayer(MongoPlayer player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document doc = player.toDocument();
        collection.insertOne(doc);
    }

    public static MongoPlayer getPlayerByUuid(String uuid) throws IllegalStateException {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return new MongoPlayer().fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(uuid));
    }

    public static void updatePlayer(MongoPlayer player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }
}
