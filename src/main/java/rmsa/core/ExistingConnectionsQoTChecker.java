package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class ExistingConnectionsQoTChecker implements ExistingQoTGuard {
    private final SpectrumState spectrum;
    private final NetworkNliEvaluator nliEvaluator;

    public ExistingConnectionsQoTChecker(SpectrumState spectrum, NetworkNliEvaluator nliEvaluator) {
        if (spectrum == null || nliEvaluator == null) {
            throw new IllegalArgumentException("Spectrum and NLI evaluator are required");
        }
        this.spectrum = spectrum;
        this.nliEvaluator = nliEvaluator;
    }

    public ExistingQoTCheck checkWithHypothetical(HypotheticalSlotAllocation candidate) {
        List<HypotheticalSlotAllocation> candidates = new ArrayList<HypotheticalSlotAllocation>();
        candidates.add(candidate);
        return checkWithHypotheticals(candidates);
    }

    @Override
    public ExistingQoTCheck checkWithHypotheticals(List<HypotheticalSlotAllocation> candidates) {
        if (candidates == null) {
            throw new IllegalArgumentException("Candidate allocations are required");
        }
        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            RobustSlotQoT qoT = qoTForExistingAllocation(allocation, candidates);
            if (qoT.sinrMinDb() < allocation.modulationFormat().sinrThresholdDb()) {
                return ExistingQoTCheck.rejected(allocation, qoT);
            }
        }
        return ExistingQoTCheck.feasible();
    }

    private RobustSlotQoT qoTForExistingAllocation(
            SlotAllocation allocation,
            List<HypotheticalSlotAllocation> candidates) {
        NetworkPath path = allocation.connection().pathForRole(allocation.role());
        if (allocation.role() == PathRole.WORKING) {
            List<FailureScenario> scenarios = RobustScenarioGenerator.forWorkingPath(path, spectrum);
            return nliEvaluator.robustQoTWithHypotheticals(path, allocation.slotIndex(), scenarios, candidates);
        }
        List<FailureScenario> scenarios = RobustScenarioGenerator.forBackupPath(allocation.connection().workingPath(), spectrum);
        return nliEvaluator.robustQoTWithHypotheticals(path, allocation.slotIndex(), scenarios, candidates);
    }
}
