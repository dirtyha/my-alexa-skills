package handler;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.Permissions;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.AskForPermissionsConsentCard;
import com.amazon.speech.ui.Card;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.amazonaws.util.json.JSONTokener;
import homecontrol.Responder;
import http.HttpClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import storage.PlacesDao;

/**
 * Utilities for place management in Alexa skill.
 * Provides a capability to enter places into Alexa to-do list and import
 * them into DynamoDb storage for later use.
 * 
 * @author harri
 */
public class PlacesHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PlacesHandler.class);
    private static final String URL_HOUSEHOLD_LIST = "https://api.amazonalexa.com/v2/householdlists/";
    private static final String SESSION_ISWHATNEXT = "isWhatNext";
    private final Responder responder;
    private final String cardTitle;
    private final PlacesDao dao;
    
    public PlacesHandler(String cardTitle, AmazonDynamoDBClient dbClient) {
        this.cardTitle = cardTitle;
        responder = new Responder(cardTitle);

        if (dbClient == null) {
            dbClient = new AmazonDynamoDBClient();
            dbClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        }
        dao = new PlacesDao(dbClient);
    }
    
    public SpeechletResponse handleSavePlacesRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);
        Card card = null;
        
        Permissions permissions = session.getUser().getPermissions();
        if (permissions != null && permissions.getConsentToken() != null) {
            String token = permissions.getConsentToken();
            String userId = session.getUser().getUserId();
            List<String> placeNames = addPlacesFromList(userId, token);
            if (placeNames.size() > 0) {
                sb.append("I saved the following places from your to-do list: ");
                for (int i = 0; i < placeNames.size(); i++) {
                    if (i > 0) {
                        if (i == placeNames.size() - 1) {
                            sb.append(" and ");
                        } else {
                            sb.append(", ");
                        }
                    }
                    sb.append(placeNames.get(i));
                }
                sb.append(".");
            } else {
                sb.append("There were no places defined in your to-do list.");
                isWhatNext = false;
            }
        } else {
            // permissions not yet granted
            sb.append("Plese grant list read and write permission in Alexa application.");
            AskForPermissionsConsentCard myCard = new AskForPermissionsConsentCard();
            myCard.setTitle(cardTitle);
            Set<String> set = new HashSet<>();
            set.add("read::alexa:household:list");
            set.add("write::alexa:household:list");
            myCard.setPermissions(set);
            isWhatNext = false;
            card = myCard;
        }

        return responder.respond(sb.toString(), isWhatNext, card);
    }
    
    public SpeechletResponse handleGetPlacesRequest(final Intent intent, final Session session) {
        StringBuilder sb = new StringBuilder();
        boolean isWhatNext = (boolean) session.getAttribute(SESSION_ISWHATNEXT);

        String userId = session.getUser().getUserId();
        List<String> placeNames = getPlaces(userId);
        if (placeNames.size() > 0) {
            sb.append("You have saved the following places: ");
            for (int i = 0; i < placeNames.size(); i++) {
                if (i > 0) {
                    if (i == placeNames.size() - 1) {
                        sb.append(" and ");
                    } else {
                        sb.append(", ");
                    }
                }
                sb.append(placeNames.get(i));
            }
            sb.append(".");
        } else {
            sb.append("You have saved no places. ");
            sb.append("You can add new places in Alexa to-do list and ask ");
            sb.append(cardTitle);
            sb.append(" to save them. ");
            sb.append("For example, you can say: Alexa, ask ");
            sb.append(cardTitle);
            sb.append(" to save places. ");
            isWhatNext = false;
        }

        return responder.respond(sb.toString(), isWhatNext);
    }

    public String getAddressForPlace(final String userId, final String placeName) {
        return dao.getAddress(userId, placeName);
    }

    private List<String> getPlaces(final String userId) {
        Map<String, String> map = dao.getPlaces(userId);
        return new ArrayList<>(map.keySet());
    }

    private List<String> addPlacesFromList(final String userId, final String token) {
        List<String> placeNames = new ArrayList<>();

        String listId = getListId(token);
        Map<String, String> items = getItemsFromList(token, listId);
        for (String id : items.keySet()) {
            String value = items.get(id);
            String parts[] = value.split(":");
            if (parts.length == 3) {
                String placeName = parts[1].toLowerCase();
                String address = parts[2];
                dao.savePlace(userId, placeName, address);
                placeNames.add(placeName);
            }
        }

        // delete places from list
        for (String id : items.keySet()) {
            String url = URL_HOUSEHOLD_LIST + listId + "/items/" + id;
            HttpClient.delete(url, token);
        }

        return placeNames;
    }
    
    private String getListId(final String token) {
        String id = null;

        StringBuffer response = HttpClient.getResponse(URL_HOUSEHOLD_LIST, token);
        if (response != null && response.length() != 0) {
            try {
                JSONObject responseObject = new JSONObject(new JSONTokener(response.toString()));

                JSONArray lists = responseObject.getJSONArray("lists");
                for (int i = 0; i < lists.length(); i++) {
                    JSONObject list = lists.getJSONObject(i);
                    String name = list.getString("name");
                    if (name.equals("Alexa to-do list")) {
                        id = list.getString("listId");
                        break;
                    }
                }
            } catch (JSONException ex) {
                LOG.error("Failed to parse service response.", ex);
            }
        }

        return id;
    }
    
    private Map<String, String> getItemsFromList(final String token, final String listId) {
        Map<String, String> places = new HashMap<>();

        if (token != null) {
            String url = URL_HOUSEHOLD_LIST + listId + "/active";
            StringBuffer response = HttpClient.getResponse(url, token);
            if (response != null && response.length() > 0) {
                try {
                    JSONObject responseObject = new JSONObject(new JSONTokener(response.toString()));

                    JSONArray items = responseObject.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String id = item.getString("id");
                        String value = item.getString("value");
                        if (value.toLowerCase().contains("place:")) {
                            places.put(id, value);
                        }
                    }
                } catch (JSONException ex) {
                    LOG.error("Failed to parse service response.", ex);
                }
            }
        }

        return places;
    }

}
