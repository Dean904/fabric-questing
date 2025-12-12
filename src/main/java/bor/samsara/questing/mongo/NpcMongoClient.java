package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoNpc;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class NpcMongoClient {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String NPC_COLLECTION = "nonPlayerCharacters";
    private static final MongoDatabase database = MongoDatabaseSingleton.getDatabase();
    public static final MongoCollection<Document> collection = database.getCollection(NPC_COLLECTION);

    static {
        try {
            collection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true).background(true));
            collection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private NpcMongoClient() {}

    public static void createNpc(MongoNpc npc) {
        Document doc = npc.toDocument();
        collection.insertOne(doc);
    }

    public static MongoNpc getFirstNpcByName(String name) {
        Document query = new Document("name", name);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }

        throw new IllegalStateException("The npc '%s' was not found".formatted(name));
    }

    public static MongoNpc getNpc(String uuid) throws IllegalStateException {
        Document query = new Document("uuid", uuid);
        Document doc = collection.find(query).first();

        if (doc != null) {
            return MongoNpc.fromDocument(doc);
        }
        throw new IllegalStateException("The MongoNpc for UUID '%s' was not found".formatted(uuid));
    }

    public static void updateNpc(MongoNpc player) {
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        collection.updateOne(query, update);
    }

    public static DeleteResult deleteNpc(String s) {
        return collection.deleteOne(new Document("uuid", s));
    }

    public static MongoNpc getNpcByName(String name) {
        Document query = new Document("name", name);
        Document doc = collection.find(query).first();

        if (null != doc && null != doc.getString("uuid")) {
            return new MongoNpc(doc.getString("uuid"), doc.getString("name")).fromDocument(doc);
        }
        throw new IllegalStateException("The MongoNpc named '%s' was not found".formatted(name));
    }
}