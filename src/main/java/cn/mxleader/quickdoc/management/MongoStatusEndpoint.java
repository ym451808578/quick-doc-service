package cn.mxleader.quickdoc.management;

import com.mongodb.CommandResult;
import com.mongodb.MongoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;

@Endpoint(id = "mongo-status")
@Component
public class MongoStatusEndpoint {

    @Value("${spring.data.mongodb.database}")
    private String database;

    private final MongoClient mongoClient;

    @Autowired
    public MongoStatusEndpoint(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    @ReadOperation
    public CommandResult getMongoDetail(@Selector String type) {
        if (type.toLowerCase().equalsIgnoreCase("more")) {
            return mongoClient.getDB(database).command("serverStatus");
        }
        return getMongo();
    }

    @ReadOperation
    public CommandResult getMongo() {
        return mongoClient.getDB(database).getStats();
    }
}