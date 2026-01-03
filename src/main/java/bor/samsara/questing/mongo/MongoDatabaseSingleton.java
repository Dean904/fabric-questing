package bor.samsara.questing.mongo;

import bor.samsara.questing.settings.AppConfiguration;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import java.io.Closeable;
import java.io.IOException;

public class MongoDatabaseSingleton implements Closeable {

    private static MongoDatabaseSingleton singleton;

    private final MongoClient mongoClient = MongoClients.create(AppConfiguration.getConfiguration(AppConfiguration.MONGO_URI));
    private final MongoDatabase database = mongoClient.getDatabase(AppConfiguration.getConfiguration(AppConfiguration.DATABASE_NAME));

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
