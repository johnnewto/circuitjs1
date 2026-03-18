package johnnewto.world2;

public class World2Scenario {
    private final String id;
    private final String displayName;
    private final double yearMin;
    private final double yearMax;
    private final double policyYear;
    private final double resourceUsageMultiplier;
    private final double pollutionMultiplier;
    private final double capitalMultiplier;
    private final double foodMultiplier;
    private final double birthControlStrength;

    public World2Scenario(String id,
                          String displayName,
                          double yearMin,
                          double yearMax,
                          double policyYear,
                          double resourceUsageMultiplier,
                          double pollutionMultiplier,
                          double capitalMultiplier,
                          double foodMultiplier,
                          double birthControlStrength) {
        this.id = id;
        this.displayName = displayName;
        this.yearMin = yearMin;
        this.yearMax = yearMax;
        this.policyYear = policyYear;
        this.resourceUsageMultiplier = resourceUsageMultiplier;
        this.pollutionMultiplier = pollutionMultiplier;
        this.capitalMultiplier = capitalMultiplier;
        this.foodMultiplier = foodMultiplier;
        this.birthControlStrength = birthControlStrength;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getYearMin() {
        return yearMin;
    }

    public double getYearMax() {
        return yearMax;
    }

    public double getPolicyYear() {
        return policyYear;
    }

    public double getResourceUsageMultiplier() {
        return resourceUsageMultiplier;
    }

    public double getPollutionMultiplier() {
        return pollutionMultiplier;
    }

    public double getCapitalMultiplier() {
        return capitalMultiplier;
    }

    public double getFoodMultiplier() {
        return foodMultiplier;
    }

    public double getBirthControlStrength() {
        return birthControlStrength;
    }
}
