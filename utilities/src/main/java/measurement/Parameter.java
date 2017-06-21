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
public class Parameter {

    private final String name;
    private final Value value;
    private final Unit unit;

    public Parameter(String name, String value, Unit unit) {
        this.name = name;
        this.unit = unit;

        if (null != unit) {
            switch (unit) {
                case WINDDIR:
                    this.value = new WindDirection(value);
                    break;
                case CLOUDCOVERAGE:
                    this.value = new CloudCoverage(toPrecision(value, 0));
                    break;
                case DEGREES:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                case HPA:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                case MMPH:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                case MPS:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                case PERCENT:
                    this.value = new Value(toPrecision(value, 0));
                    break;
                case PPM:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                case UGM3:
                    this.value = new Value(toPrecision(value, 1));
                    break;
                default:
                    this.value = new Value(value);
                    break;
            }
        } else {
            this.value = new Value(value);
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s %s", name, value, unit);
    }
    
    private static String toPrecision(String value, int precision) {
        return String.format("%." + precision + "f", Double.parseDouble(value));
    }
    
}
