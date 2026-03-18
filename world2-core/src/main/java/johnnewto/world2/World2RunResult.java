package johnnewto.world2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class World2RunResult {
    private final World2Scenario scenario;
    private final double dt;
    private final List<World2DataPoint> series;

    public World2RunResult(World2Scenario scenario, double dt, List<World2DataPoint> series) {
        this.scenario = scenario;
        this.dt = dt;
        this.series = Collections.unmodifiableList(new ArrayList<World2DataPoint>(series));
    }

    public World2Scenario getScenario() {
        return scenario;
    }

    public double getDt() {
        return dt;
    }

    public List<World2DataPoint> getSeries() {
        return series;
    }

    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("t,P,POLR,CI,QL,NR\n");
        for (int index = 0; index < series.size(); index++) {
            World2DataPoint dataPoint = series.get(index);
            csv.append(formatNumber(dataPoint.getTime())).append(',')
               .append(formatNumber(dataPoint.getPopulation())).append(',')
               .append(formatNumber(dataPoint.getPollutionRatio())).append(',')
               .append(formatNumber(dataPoint.getCapitalInvestment())).append(',')
               .append(formatNumber(dataPoint.getQualityOfLife())).append(',')
               .append(formatNumber(dataPoint.getNaturalResources())).append('\n');
        }
        return csv.toString();
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%.8f", value);
    }
}
