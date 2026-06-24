package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class NetworkNliEvaluator {
    private final SpectrumState spectrum;
    private final PhysicalLayerModel physicalLayer;
    private ScientificComputationListener computationListener;

    public NetworkNliEvaluator(SpectrumState spectrum, PhysicalLayerModel physicalLayer) {
        if (spectrum == null || physicalLayer == null) {
            throw new IllegalArgumentException("Spectrum and physical layer are required");
        }
        this.spectrum = spectrum;
        this.physicalLayer = physicalLayer;
    }

    public void setComputationListener(ScientificComputationListener computationListener) {
        this.computationListener = computationListener;
    }

    public PathSlotNli evaluate(NetworkPath path, int targetSlot, FailureScenario scenario) {
        return evaluateWithHypotheticals(path, targetSlot, scenario, new ArrayList<HypotheticalSlotAllocation>());
    }

    public PathSlotNli evaluate(NetworkPath path, int targetSlot, FailureScenario scenario, List<Integer> sameConnectionSlots) {
        List<HypotheticalSlotAllocation> hypotheticalAllocations = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer sameConnectionSlot : sameConnectionSlots) {
            if (sameConnectionSlot != null) {
                hypotheticalAllocations.add(new HypotheticalSlotAllocation(
                        null,
                        null,
                        path,
                        sameConnectionSlot.intValue()));
            }
        }
        return evaluateWithHypotheticals(path, targetSlot, scenario, hypotheticalAllocations);
    }

    public PathSlotNli evaluateWithHypotheticals(
            NetworkPath path,
            int targetSlot,
            FailureScenario scenario,
            List<HypotheticalSlotAllocation> hypotheticalAllocations) {
        if (computationListener != null) {
            computationListener.recordNliSinrComputation(false);
        }
        InterferenceBreakdown total = new InterferenceBreakdown(0.0, 0.0);
        for (Link link : path.links()) {
            int spans = physicalLayer.spanCount(link.lengthKm());
            double sci = physicalLayer.sciPerSpanW() * spans;
            double xci = xciFromActiveAllocations(link.id(), targetSlot, scenario, hypotheticalAllocations) * spans;
            total = total.plus(new InterferenceBreakdown(sci, xci));
        }
        PathSlotNli result = new PathSlotNli(path, targetSlot, scenario, total);
        return result;
    }

    public RobustSlotQoT robustQoT(NetworkPath path, int targetSlot, List<FailureScenario> scenarios) {
        return robustQoT(path, targetSlot, scenarios, new ArrayList<Integer>());
    }

    public RobustSlotQoT robustQoT(
            NetworkPath path,
            int targetSlot,
            List<FailureScenario> scenarios,
            List<Integer> sameConnectionSlots) {
        List<HypotheticalSlotAllocation> hypotheticalAllocations = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer sameConnectionSlot : sameConnectionSlots) {
            if (sameConnectionSlot != null) {
                hypotheticalAllocations.add(new HypotheticalSlotAllocation(
                        null,
                        null,
                        path,
                        sameConnectionSlot.intValue()));
            }
        }
        return robustQoTWithHypotheticals(path, targetSlot, scenarios, hypotheticalAllocations);
    }

    public RobustSlotQoT robustQoTWithHypotheticals(
            NetworkPath path,
            int targetSlot,
            List<FailureScenario> scenarios,
            List<HypotheticalSlotAllocation> hypotheticalAllocations) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("At least one scenario is required");
        }
        if (computationListener != null) {
            computationListener.recordRobustQoTValidation(scenarios.size());
        }
        PathSlotNli worst = null;
        for (FailureScenario scenario : scenarios) {
            PathSlotNli current = evaluateWithHypotheticals(path, targetSlot, scenario, hypotheticalAllocations);
            if (worst == null || current.totalNliW() > worst.totalNliW()) {
                worst = current;
            }
        }
        SlotQoT qoT = physicalLayer.slotQoT(path, worst.totalNliW());
        return new RobustSlotQoT(targetSlot, worst.scenario(), worst.totalNliW(), qoT);
    }

    public double hypotheticalNliContributionW(
            NetworkPath path,
            int targetSlot,
            FailureScenario scenario,
            List<HypotheticalSlotAllocation> hypotheticalAllocations) {
        if (hypotheticalAllocations == null || hypotheticalAllocations.isEmpty()) {
            return 0.0;
        }
        if (computationListener != null) {
            computationListener.recordNliSinrComputation(false);
        }
        double contribution = 0.0;
        for (Link link : path.links()) {
            int spans = physicalLayer.spanCount(link.lengthKm());
            double xciPerSpan = 0.0;
            for (HypotheticalSlotAllocation allocation : hypotheticalAllocations) {
                if (allocation.path().containsLink(link.id()) && allocation.isActive(scenario)) {
                    xciPerSpan += physicalLayer.xciPerSpanW(targetSlot, allocation.slotIndex());
                }
            }
            contribution += xciPerSpan * spans;
        }
        return contribution;
    }

    public SlotQoT slotQoT(NetworkPath path, double nliW) {
        return physicalLayer.slotQoT(path, nliW);
    }


    public void recordExistingQoTValidation(int scenarioCount) {
        if (computationListener != null) {
            computationListener.recordExistingQoTValidation(scenarioCount);
        }
    }

    public List<FailureScenario> workingScenariosFor(NetworkPath workingPath) {
        return RobustScenarioGenerator.forWorkingPath(workingPath, spectrum);
    }

    public List<FailureScenario> backupScenariosFor(NetworkPath backupPath, NetworkPath correspondingWorkingPath) {
        return RobustScenarioGenerator.forBackupPath(correspondingWorkingPath, spectrum);
    }
    public RobustSlotQoT robustQoTForWorkingPath(NetworkPath workingPath, int targetSlot) {
        return robustQoTForWorkingPath(workingPath, targetSlot, new ArrayList<Integer>());
    }

    public RobustSlotQoT robustQoTForWorkingPath(NetworkPath workingPath, int targetSlot, List<Integer> sameConnectionSlots) {
        List<FailureScenario> scenarios = RobustScenarioGenerator.forWorkingPath(workingPath, spectrum);
        return robustQoT(workingPath, targetSlot, scenarios, sameConnectionSlots);
    }

    public RobustSlotQoT robustQoTForBackupPath(NetworkPath backupPath, NetworkPath correspondingWorkingPath, int targetSlot) {
        return robustQoTForBackupPath(backupPath, correspondingWorkingPath, targetSlot, new ArrayList<Integer>());
    }

    public RobustSlotQoT robustQoTForBackupPath(
            NetworkPath backupPath,
            NetworkPath correspondingWorkingPath,
            int targetSlot,
            List<Integer> sameConnectionSlots) {
        List<FailureScenario> scenarios = RobustScenarioGenerator.forBackupPath(correspondingWorkingPath, spectrum);
        return robustQoT(backupPath, targetSlot, scenarios, sameConnectionSlots);
    }

    private double xciFromActiveAllocations(
            int linkId,
            int targetSlot,
            FailureScenario scenario,
            List<HypotheticalSlotAllocation> hypotheticalAllocations) {
        double xci = 0.0;
        for (SlotAllocation allocation : spectrum.activeAllocationsOnLink(linkId, scenario)) {
            xci += physicalLayer.xciPerSpanW(targetSlot, allocation.slotIndex());
        }
        for (HypotheticalSlotAllocation allocation : hypotheticalAllocations) {
            if (allocation.path().containsLink(linkId) && allocation.isActive(scenario)) {
                xci += physicalLayer.xciPerSpanW(targetSlot, allocation.slotIndex());
            }
        }
        return xci;
    }

}
