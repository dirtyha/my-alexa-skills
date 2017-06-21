/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package measurement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author HHY
 */
public class WindDirection extends Value {

    private static final Map<String, Double> DIRECTIONS = new LinkedHashMap<>();

    static {
        DIRECTIONS.put("north", 22.5); // 0-22.5
        DIRECTIONS.put("north-east", 67.5); // 22.5-67.5
        DIRECTIONS.put("east", 112.5); // 67.5-112.5
        DIRECTIONS.put("south-east", 157.5); // 112.5-157.5
        DIRECTIONS.put("south", 202.5); // 157.5-202.5
        DIRECTIONS.put("south-west", 247.5); // 202.5-247
        DIRECTIONS.put("west", 292.5); // 247.5-292.5
        DIRECTIONS.put("north-west", 337.5); // 292.5-337.5
    }

    public WindDirection(Value windDirection) {
        super(windDirection.toString());
    }

    public WindDirection(String windDirection) {
        super(windDirection);
    }

    @Override
    public String toString() {
        String text = "north";
        double wd = getValue();
        for (String direction : DIRECTIONS.keySet()) {
            double dir = DIRECTIONS.get(direction);
            if (wd < dir) {
                text = direction;
                break;
            }
        }

        return text;
    }

}
