package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BitloadingDecision {
    private final boolean feasible;
    private final String rejectionReason;
    private final List<ModulationFormat> modulationFormats;
    private final int carriedRateGbps;

    private BitloadingDecision(
            boolean feasible,
            String rejectionReason,
            List<ModulationFormat> modulationFormats,
            int carriedRateGbps) {
        this.feasible = feasible;
        this.rejectionReason = rejectionReason;
        this.modulationFormats = Collections.unmodifiableList(new ArrayList<ModulationFormat>(modulationFormats));
        this.carriedRateGbps = carriedRateGbps;
    }

    public static BitloadingDecision feasible(List<ModulationFormat> modulationFormats) {
        return new BitloadingDecision(true, "", modulationFormats, sumRate(modulationFormats));
    }

    public static BitloadingDecision rejected(String reason, List<ModulationFormat> modulationFormats) {
        return new BitloadingDecision(false, reason, modulationFormats, sumRate(modulationFormats));
    }

    public boolean isFeasible() {
        return feasible;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public List<ModulationFormat> modulationFormats() {
        return modulationFormats;
    }

    public int carriedRateGbps() {
        return carriedRateGbps;
    }

    private static int sumRate(List<ModulationFormat> formats) {
        int rate = 0;
        for (ModulationFormat format : formats) {
            rate += format.dataRateGbps();
        }
        return rate;
    }
}
