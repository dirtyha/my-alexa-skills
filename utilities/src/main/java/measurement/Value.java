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
public class Value {

    private final String strValue;

    public Value(String value) {
        strValue = value;
    }

    double getValue() {
        return Double.parseDouble(strValue);
    }

    @Override
    public String toString() {
        return strValue;
    }
}
