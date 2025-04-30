package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoNpc;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class NpcMongoClient {

    private static final String NPC_COLLECTION = "nonPlayerCharacters";

    private static final MongoDatabase database = MongoClientSingleton.getDatabase();

    private NpcMongoClient() {
    }

    public static void createNpc(MongoNpc npc) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document doc = npc.toDocument();
        collection.insertOne(doc);
    }

    public static MongoNpc getFirstNpcByName(String name) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("name", name);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return new MongoNpc().fromDocument(doc);
        }

        throw new IllegalStateException("The player '%s' was not found".formatted(name));
    }

    public static MongoNpc getNpc(String uuid) throws IllegalStateException {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return new MongoNpc().fromDocument(doc);
        }
        throw new IllegalStateException("The MongoNpc '%s' was not found".formatted(uuid));
    }

    public static void updateNpc(MongoNpc player) {
        MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }
}
