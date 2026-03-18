package johnnewto.world2;

public class World2DataPoint {
    private final double time;
    private final double population;
    private final double pollutionRatio;
    private final double capitalInvestment;
    private final double qualityOfLife;
    private final double naturalResources;

    public World2DataPoint(double time, double population, double pollutionRatio,
                           double capitalInvestment, double qualityOfLife, double naturalResources) {
        this.time = time;
        this.population = population;
        this.pollutionRatio = pollutionRatio;
        this.capitalInvestment = capitalInvestment;
        this.qualityOfLife = qualityOfLife;
        this.naturalResources = naturalResources;
    }

    public double getTime() {
        return time;
    }

    public double getPopulation() {
        return population;
    }

    public double getPollutionRatio() {
        return pollutionRatio;
    }

    public double getCapitalInvestment() {
        return capitalInvestment;
    }

    public double getQualityOfLife() {
        return qualityOfLife;
    }

    public double getNaturalResources() {
        return naturalResources;
    }
}
