package rmsa.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public final class NliSnapshotStore {
    private final Map<AllocationScenarioKey, Double> nliByAllocationAndScenario =
            new LinkedHashMap<AllocationScenarioKey, Double>();

    public static NliSnapshotStore build(SpectrumState spectrum, NetworkNliEvaluator nliEvaluator) {
        if (spectrum == null || nliEvaluator == null) {
            throw new IllegalArgumentException("Spectrum and NLI evaluator are required");
        }
        NliSnapshotStore store = new NliSnapshotStore();
        store.refreshFromSpectrum(spectrum, nliEvaluator);
        return store;
    }

    public void refreshFromSpectrum(SpectrumState spectrum, NetworkNliEvaluator nliEvaluator) {
        if (spectrum == null || nliEvaluator == null) {
            throw new IllegalArgumentException("Spectrum and NLI evaluator are required");
        }
        nliByAllocationAndScenario.clear();
        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            List<FailureScenario> scenarios;
            if (allocation.role() == PathRole.WORKING) {
                scenarios = RobustScenarioGenerator.forWorkingPath(path, spectrum);
            } else {
                scenarios = RobustScenarioGenerator.forBackupPath(allocation.connection().workingPath(), spectrum);
            }
            for (FailureScenario scenario : scenarios) {
                double nli = nliEvaluator.evaluate(path, allocation.slotIndex(), scenario).totalNliW();
                put(allocation, scenario, nli);
            }
        }
    }

    public void updateAfterAcceptedAllocation(
            SpectrumState spectrum,
            NetworkNliEvaluator nliEvaluator,
            List<SlotAllocation> acceptedAllocations) {
        if (spectrum == null || nliEvaluator == null || acceptedAllocations == null) {
            throw new IllegalArgumentException("Spectrum, NLI evaluator and accepted allocations are required");
        }
        if (acceptedAllocations.isEmpty()) {
            return;
        }

        List<HypotheticalSlotAllocation> acceptedAsHypotheticals =
                new ArrayList<HypotheticalSlotAllocation>();
        for (SlotAllocation allocation : acceptedAllocations) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            acceptedAsHypotheticals.add(new HypotheticalSlotAllocation(
                    allocation.connection(),
                    allocation.role(),
                    path,
                    allocation.slotIndex()));
        }

        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            List<FailureScenario> scenarios = scenariosFor(allocation, path, spectrum);
            for (FailureScenario scenario : scenarios) {
                if (isAccepted(allocation, acceptedAllocations) || !contains(allocation, scenario)) {
                    put(allocation, scenario, nliEvaluator.evaluate(path, allocation.slotIndex(), scenario).totalNliW());
                } else {
                    double updatedNli = get(allocation, scenario)
                            + nliEvaluator.hypotheticalNliContributionW(
                                    path,
                                    allocation.slotIndex(),
                                    scenario,
                                    acceptedAsHypotheticals);
                    put(allocation, scenario, updatedNli);
                }
            }
        }
    }

    public void updateAfterReleasedAllocation(
            SpectrumState spectrum,
            NetworkNliEvaluator nliEvaluator,
            List<SlotAllocation> releasedAllocations) {
        if (spectrum == null || nliEvaluator == null || releasedAllocations == null) {
            throw new IllegalArgumentException("Spectrum, NLI evaluator and released allocations are required");
        }
        if (releasedAllocations.isEmpty()) {
            return;
        }

        List<HypotheticalSlotAllocation> releasedAsHypotheticals =
                new ArrayList<HypotheticalSlotAllocation>();
        for (SlotAllocation allocation : releasedAllocations) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            releasedAsHypotheticals.add(new HypotheticalSlotAllocation(
                    allocation.connection(),
                    allocation.role(),
                    path,
                    allocation.slotIndex()));
            for (FailureScenario scenario : scenariosFor(allocation, path, spectrum)) {
                remove(allocation, scenario);
            }
        }

        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            List<FailureScenario> scenarios = scenariosFor(allocation, path, spectrum);
            for (FailureScenario scenario : scenarios) {
                if (!contains(allocation, scenario)) {
                    put(allocation, scenario, nliEvaluator.evaluate(path, allocation.slotIndex(), scenario).totalNliW());
                } else {
                    double updatedNli = get(allocation, scenario)
                            - nliEvaluator.hypotheticalNliContributionW(
                                    path,
                                    allocation.slotIndex(),
                                    scenario,
                                    releasedAsHypotheticals);
                    put(allocation, scenario, Math.max(0.0, updatedNli));
                }
            }
        }
    }

    public void put(SlotAllocation allocation, FailureScenario scenario, double nliW) {
        if (nliW < 0) {
            throw new IllegalArgumentException("NLI must be non-negative");
        }
        nliByAllocationAndScenario.put(AllocationScenarioKey.of(allocation, scenario), Double.valueOf(nliW));
    }

    public double get(SlotAllocation allocation, FailureScenario scenario) {
        Double value = nliByAllocationAndScenario.get(AllocationScenarioKey.of(allocation, scenario));
        if (value == null) {
            throw new IllegalStateException("No NLI snapshot for allocation and scenario");
        }
        return value.doubleValue();
    }

    public boolean contains(SlotAllocation allocation, FailureScenario scenario) {
        return nliByAllocationAndScenario.containsKey(AllocationScenarioKey.of(allocation, scenario));
    }

    public void remove(SlotAllocation allocation, FailureScenario scenario) {
        nliByAllocationAndScenario.remove(AllocationScenarioKey.of(allocation, scenario));
    }

    public int size() {
        return nliByAllocationAndScenario.size();
    }

    private List<FailureScenario> scenariosFor(SlotAllocation allocation, NetworkPath path, SpectrumState spectrum) {
        if (allocation.role() == PathRole.WORKING) {
            return RobustScenarioGenerator.forWorkingPath(path, spectrum);
        }
        return RobustScenarioGenerator.forBackupPath(allocation.connection().workingPath(), spectrum);
    }

    private boolean isAccepted(SlotAllocation allocation, List<SlotAllocation> acceptedAllocations) {
        for (SlotAllocation accepted : acceptedAllocations) {
            if (sameAllocationIdentity(allocation, accepted)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameAllocationIdentity(SlotAllocation first, SlotAllocation second) {
        return first.slotIndex() == second.slotIndex()
                && first.role() == second.role()
                && first.connection().id().equals(second.connection().id());
    }
}
