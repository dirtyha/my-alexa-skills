package myweather.storage;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for DynamoDB persistance layer.
 */
public class DynamoDbClient {

    private final AmazonDynamoDBClient dynamoDBClient;

    public DynamoDbClient(final AmazonDynamoDBClient dynamoDBClient) {
        this.dynamoDBClient = dynamoDBClient;
    }

    /**
     * Loads a place from DynamoDB by primary Hash Key. Callers of this method
     * should pass in an object which represents an item in the DynamoDB table
     * item with the primary key populated.
     *
     * @param userId
     * @param placeName
     * @return
     */
    public Place loadPlace(String userId, String placeName) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        Place toFind = new Place();
        toFind.setUserId(userId);
        toFind.setPlaceName(placeName);
        Place found = mapper.load(toFind);
        return found;
    }

    public List<Place> loadPlaces(String userId) {
        DynamoDBMapper mapper = createDynamoDBMapper();

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":userId", new AttributeValue().withS(userId));
        DynamoDBQueryExpression<Place> queryExpression = new DynamoDBQueryExpression<Place>()
                .withKeyConditionExpression("UserId = :userId")
                .withExpressionAttributeValues(eav);
        List<Place> places = mapper.query(Place.class, queryExpression);

        return places;
    }

    /**
     * Stores an item to DynamoDB.
     *
     * @param place
     */
    public void savePlace(final Place place) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        mapper.save(place);
    }

    /**
     * Creates a {@link DynamoDBMapper} using the default configurations.
     *
     * @return
     */
    private DynamoDBMapper createDynamoDBMapper() {
        return new DynamoDBMapper(dynamoDBClient);
    }
}
