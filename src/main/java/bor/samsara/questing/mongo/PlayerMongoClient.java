package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoPlayer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bor.samsara.questing.SamsaraFabricQuesting.MOD_ID;

public class PlayerMongoClient {

    public static final Logger log = LoggerFactory.getLogger(MOD_ID);

    private static final String PLAYER_COLLECTION = "playerCharacters";
    private static final MongoDatabase database = MongoDatabaseSingleton.getDatabase();
    private static final MongoCollection<Document> playerCollection = database.getCollection(PLAYER_COLLECTION);

    static {
        try {
            playerCollection.createIndex(Indexes.ascending("name"), new IndexOptions().unique(true).background(true));
            playerCollection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private PlayerMongoClient() {}

    public static void createPlayer(MongoPlayer player) {
        playerCollection.insertOne(player.toDocument());
    }

    public static MongoPlayer getPlayerByUuid(String uuid) throws IllegalStateException {
        Document query = new Document("uuid", uuid);
        Document doc = playerCollection.find(query).first();

        if (doc != null) {
            return MongoPlayer.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(uuid));
    }

    public static void updatePlayer(MongoPlayer player) {
        Document query = new Document("uuid", player.getUuid());
        Document update = new Document("$set", player.toDocument());
        playerCollection.updateOne(query, update);
    }

    public static MongoPlayer getPlayerByName(String targetPlayerName) {
        Document query = new Document("name", targetPlayerName);
        Document doc = playerCollection.find(query).first();

        if (doc != null) {
            return MongoPlayer.fromDocument(doc);
        }
        throw new IllegalStateException("The player '%s' was not found".formatted(targetPlayerName));
    }
}
