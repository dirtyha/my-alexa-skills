/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package measurement;

/**
 *
 * @author HHY
 */
public enum Unit {
    DEGREES("degrees"),
    PERCENT("percents"),
    MMPH("milli meters per hour"),
    CLOUDCOVERAGE("percent"),
    HPA("hecto pascals"),
    MPS("meters per second"),
    PPM("parts per million"),
    UGM3("micro grams per cubic meter"),
    AQINDEX(""),
    WINDDIR("");

    private final String str;
    
    private Unit(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return str;
    }
}
