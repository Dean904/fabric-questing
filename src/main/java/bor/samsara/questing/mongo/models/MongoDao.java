package bor.samsara.questing.mongo.models;

import org.bson.Document;

public interface MongoDao<T> {

    public  T fromDocument(Document document);

    public Document toDocument();


}
