package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoPlayer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

public class PlayerMongoClient {

    private static final String PLAYER_COLLECTION = "playerCharacters";
    private static final MongoDatabase database = MongoDatabaseSingleton.getDatabase();

    static {
        MongoCollection<Document> playerCollection = database.getCollection(PLAYER_COLLECTION);
        playerCollection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true).background(true));
        playerCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
    }

    private PlayerMongoClient() {}

    public static void createPlayer(MongoPlayer player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        collection.insertOne(player.toDocument());
    }

    public static MongoPlayer getPlayerByUuid(String uuid) throws IllegalStateException {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoPlayer.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(uuid));
    }

    public static void updatePlayer(MongoPlayer player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }

    public static MongoPlayer getPlayerByName(String targetPlayerName) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("name", targetPlayerName);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoPlayer.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(targetPlayerName));
    }
}
