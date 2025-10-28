// java
package bor.samsara.questing.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

@Deprecated
public final class MongoIndexUtils {

    private MongoIndexUtils() {}

    /**
     * Ensure an index exists on a single field with the desired uniqueness.
     * If an index on the same key exists but has different uniqueness, it is dropped and recreated.
     */
    public static void ensureUniqueIndexByField(MongoCollection<Document> collection, String field, boolean unique, IndexOptions options) {
        for (Document idxDoc : collection.listIndexes()) {
            Document key = idxDoc.get("key", Document.class);
            if (key != null && key.size() == 1 && key.containsKey(field)) {
                boolean existingUnique = idxDoc.getBoolean("unique", false);
                String name = idxDoc.getString("name");
                if (existingUnique == unique) {
                    // index already matches requested uniqueness - nothing to do
                    return;
                } else {
                    // conflicting index - drop it before creating the new one
                    collection.dropIndex(name);
                    break;
                }
            }
        }
        IndexOptions opts = options == null ? new IndexOptions() : options;
        opts.unique(unique);
        opts.background(true);
        collection.createIndex(Indexes.ascending(field), opts);
    }
}
