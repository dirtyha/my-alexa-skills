/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package time;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author harri
 */
public class TimeUtil {
    private static final Map<String, Integer> TIMES = new HashMap<>();

    static {
        // key: forecast time in spoken text
        // value: forecast time in clock hours
        TIMES.put("morning", 6);
        TIMES.put("noon", 12);
        TIMES.put("afternoon", 15);
        TIMES.put("evening", 18);
        TIMES.put("night", 21);
        TIMES.put("tomorrow morning", 30);
        TIMES.put("tomorrow noon", 36);
        TIMES.put("tomorrow afternoon", 39);
        TIMES.put("tomorrow evening", 42);
        TIMES.put("tomorrow night", 45);
    }
    
    private TimeUtil() {
    }
    
    public static ZonedDateTime getForecastTime(final String strForecastTime) {
        ZonedDateTime forecastTime = ZonedDateTime.now(ZoneId.of("Europe/Helsinki"));
        int addDays = 0;
        int hour = 0;
        if (strForecastTime == null) {
            // forecast time not specified, use next available 
            // forecast time counting from current time
            Iterator<Integer> iter = TIMES.values().iterator();
            while (iter.hasNext()) {
                hour = iter.next();
                if (hour > forecastTime.getHour()) {
                    if (hour > 24) {
                        hour -= 24;
                        addDays += 1;
                    }
                    break;
                }
            }
        } else {
            hour = TIMES.get(strForecastTime);
            if (hour > 24) {
                hour -= 24;
                addDays += 1;
            }
        }

        forecastTime = forecastTime.withHour(hour)
                .plusDays(addDays)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        return forecastTime.withZoneSameInstant(ZoneId.of("Z"));
    }
}
