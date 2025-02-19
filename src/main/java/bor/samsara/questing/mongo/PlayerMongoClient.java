package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoNpc;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class PlayerMongoClient {
    private static final String PLAYER_COLLECTION = "playerCharacters";

    private final MongoDatabase database = MongoClientSingleton.getDatabase();

    public void createNpc(MongoNpc player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document doc = player.toDocument();
        collection.insertOne(doc);
    }

    public MongoNpc getFirstNpcByName(String name) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("name", name);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }

        throw new IllegalStateException("The player '%s' was not found".formatted(name));
    }

    public MongoNpc getNpc(String uuid) throws IllegalStateException {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(uuid));
    }

    public void updateNpc(MongoNpc player) {
        MongoCollection<Document> collection = database.getCollection(PLAYER_COLLECTION);
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }
}
