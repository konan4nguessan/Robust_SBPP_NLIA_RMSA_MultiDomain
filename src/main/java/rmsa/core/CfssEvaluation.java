package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CfssEvaluation {
    private final boolean feasible;
    private final String rejectionReason;
    private final List<Integer> slotIndexes;
    private final List<ModulationFormat> modulationFormats;
    private final int carriedRateGbps;

    private CfssEvaluation(
            boolean feasible,
            String rejectionReason,
            List<Integer> slotIndexes,
            List<ModulationFormat> modulationFormats,
            int carriedRateGbps) {
        this.feasible = feasible;
        this.rejectionReason = rejectionReason;
        this.slotIndexes = Collections.unmodifiableList(new ArrayList<Integer>(slotIndexes));
        this.modulationFormats = Collections.unmodifiableList(new ArrayList<ModulationFormat>(modulationFormats));
        this.carriedRateGbps = carriedRateGbps;
    }

    public static CfssEvaluation feasible(List<Integer> slotIndexes, List<ModulationFormat> modulationFormats, int carriedRateGbps) {
        return new CfssEvaluation(true, "", slotIndexes, modulationFormats, carriedRateGbps);
    }

    public static CfssEvaluation rejected(String reason, List<Integer> slotIndexes, List<ModulationFormat> modulationFormats, int carriedRateGbps) {
        return new CfssEvaluation(false, reason, slotIndexes, modulationFormats, carriedRateGbps);
    }

    public boolean isFeasible() {
        return feasible;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public List<Integer> slotIndexes() {
        return slotIndexes;
    }

    public List<ModulationFormat> modulationFormats() {
        return modulationFormats;
    }

    public int carriedRateGbps() {
        return carriedRateGbps;
    }
}
