package bor.samsara.questing.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.io.Closeable;
import java.io.IOException;

public class MongoDatabaseSingleton implements Closeable {

    private static MongoDatabaseSingleton singleton;

    private static final String MONGO_URI = "mongodb://admin:forgot12@192.168.50.77:27017/?authSource=admin";
    private final MongoClient mongoClient = MongoClients.create(MONGO_URI);
    private final MongoDatabase database = mongoClient.getDatabase("samsara");

    private MongoDatabaseSingleton() {}

    public static MongoDatabaseSingleton getInstance() {
        if (singleton == null) {
            singleton = new MongoDatabaseSingleton();
        }
        return singleton;
    }

    public static MongoDatabase getDatabase() {
        return getInstance().database;
    }

    @Override
    public void close() throws IOException {
        mongoClient.close();
    }
}
