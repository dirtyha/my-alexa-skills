package storage;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the methods to interact with the persistence layer in DynamoDB.
 */
public class PlacesDao {
    private final DynamoDbClient dynamoDbClient;

    public PlacesDao(AmazonDynamoDBClient dynamoDbClient) {
        this.dynamoDbClient = new DynamoDbClient(dynamoDbClient);
    }

    public String getAddress(String userId, String placeName) {
        Place found = dynamoDbClient.loadPlace(userId, placeName);
        if(found != null) {
            return found.getAddress();
        } else {
            return null;
        }
    }

    public Map<String, String> getPlaces(String userId) {
        Map<String, String> ret = new HashMap<>();

        List<Place> places = dynamoDbClient.loadPlaces(userId);
        for(Place place : places) {
            ret.put(place.getPlaceName(), place.getAddress());
        }
        
        return ret;
    }

    public void savePlace(String userId, String placeName, String address) {
        Place place = new Place();
            place.setUserId(userId);
            place.setPlaceName(placeName);
            place.setAddress(address);
        
        dynamoDbClient.savePlace(place);
    }
}
