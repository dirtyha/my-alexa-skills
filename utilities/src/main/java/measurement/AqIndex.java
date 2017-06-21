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
public class AqIndex extends Value {

    private static final Map<String, Double> AQINDICES = new LinkedHashMap<>();

    static {
        AQINDICES.put("good", 1.0);
        AQINDICES.put("satisfactory", 2.0);
        AQINDICES.put("fair", 3.0);
        AQINDICES.put("poor", 4.0);
        AQINDICES.put("very poor", 5.0);
    }

    public AqIndex(Value windDirection) {
        super(windDirection.toString());
    }

    public AqIndex(String windDirection) {
        super(windDirection);
    }

    @Override
    public String toString() {
        String text = null;
        for (String index : AQINDICES.keySet()) {
            if (getValue() == AQINDICES.get(index)) {
                text = index;
                break;
            }
        }

        return text;
    }

}
