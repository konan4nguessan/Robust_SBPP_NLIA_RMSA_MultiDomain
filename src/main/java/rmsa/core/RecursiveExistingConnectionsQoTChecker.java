package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class RecursiveExistingConnectionsQoTChecker implements ExistingQoTGuard {
    private final SpectrumState spectrum;
    private final NetworkNliEvaluator nliEvaluator;
    private final NliSnapshotStore snapshotStore;

    public RecursiveExistingConnectionsQoTChecker(
            SpectrumState spectrum,
            NetworkNliEvaluator nliEvaluator,
            NliSnapshotStore snapshotStore) {
        if (spectrum == null || nliEvaluator == null || snapshotStore == null) {
            throw new IllegalArgumentException("Spectrum, NLI evaluator and snapshot store are required");
        }
        this.spectrum = spectrum;
        this.nliEvaluator = nliEvaluator;
        this.snapshotStore = snapshotStore;
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
            RobustSlotQoT qoT = recursiveQoTForExistingAllocation(allocation, candidates);
            if (qoT.sinrMinDb() < allocation.modulationFormat().sinrThresholdDb()) {
                return ExistingQoTCheck.rejected(allocation, qoT);
            }
        }
        return ExistingQoTCheck.feasible();
    }

    private RobustSlotQoT recursiveQoTForExistingAllocation(
            SlotAllocation allocation,
            List<HypotheticalSlotAllocation> candidates) {
        NetworkPath path = allocation.connection().pathForRole(allocation.role());
        List<FailureScenario> scenarios;
        if (allocation.role() == PathRole.WORKING) {
            scenarios = nliEvaluator.workingScenariosFor(path);
        } else {
            scenarios = nliEvaluator.backupScenariosFor(path, allocation.connection().workingPath());
        }

        nliEvaluator.recordExistingQoTValidation(scenarios.size());
        FailureScenario worstScenario = null;
        double nliMax = -1.0;
        for (FailureScenario scenario : scenarios) {
            double baseNli = snapshotStore.get(allocation, scenario);
            double addedNli = nliEvaluator.hypotheticalNliContributionW(
                    path,
                    allocation.slotIndex(),
                    scenario,
                    candidates);
            double updatedNli = baseNli + addedNli;
            if (updatedNli > nliMax) {
                nliMax = updatedNli;
                worstScenario = scenario;
            }
        }
        SlotQoT qoT = nliEvaluator.slotQoT(path, nliMax);
        return new RobustSlotQoT(allocation.slotIndex(), worstScenario, nliMax, qoT);
    }
}
