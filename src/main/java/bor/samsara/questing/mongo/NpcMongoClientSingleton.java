package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoNpc;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.Closeable;
import java.io.IOException;

public class NpcMongoClientSingleton implements Closeable {

    private static NpcMongoClientSingleton singleton;

    private static final String NPC_COLLECTION = "nonPlayerCharacters";

    private static final String MONGO_URI = "mongodb://admin:forgot12@192.168.50.77:27017/?authSource=admin";
    private final MongoClient mongoClient = MongoClients.create(MONGO_URI);
    private final MongoDatabase database = mongoClient.getDatabase("samsara");

    private NpcMongoClientSingleton() {}

    public static NpcMongoClientSingleton getInstance() {
        if (singleton == null) {
            singleton = new NpcMongoClientSingleton();
        }
        return singleton;
    }

    public void createNpc(MongoNpc player) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document doc = player.toDocument();
        collection.insertOne(doc);
    }

    public MongoNpc getFirstNpcByName(String name) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("name", name);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }

        throw new IllegalStateException("The player '%s' was not found".formatted(name));
    }

    public MongoNpc getNpc(String uuid) throws IllegalStateException {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(uuid));
    }

    public void updateNpc(MongoNpc player) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }

    @Override
    public void close() throws IOException {
        mongoClient.close();
    }
}
