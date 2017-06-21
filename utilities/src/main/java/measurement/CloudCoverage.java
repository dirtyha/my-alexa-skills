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
public class CloudCoverage extends Value {

    public CloudCoverage(Value cloudCoverage) {
        super(cloudCoverage.toString());
    }

    public CloudCoverage(String cloudCoverage) {
        super(cloudCoverage);
    }

    @Override
    public String toString() {
        double value = getValue();
        return String.format("%d", (int)(value / 8 * 100));
    }

}
