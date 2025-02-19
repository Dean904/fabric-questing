package bor.samsara.questing.mongo;

import bor.samsara.questing.mongo.models.MongoNpc;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.Closeable;
import java.io.IOException;

public class MongoClientSingleton implements Closeable {

    private static MongoClientSingleton singleton;


    private static final String MONGO_URI = "mongodb://localhost:27017";
    private final MongoClient mongoClient = MongoClients.create(MONGO_URI);
    private final MongoDatabase database = mongoClient.getDatabase("samsara");

    private MongoClientSingleton() {}

    public static MongoClientSingleton getInstance() {
        if (singleton == null) {
            singleton = new MongoClientSingleton();
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
