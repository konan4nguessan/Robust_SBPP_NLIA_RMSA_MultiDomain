package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class RobustCfssWorkingEvaluator {
    private final SpectrumState spectrum;
    private final GreedyRobustBitloading bitloading;
    private final ExistingQoTGuard existingQoTGuard;
    private final boolean checkExistingConnectionQoT;
    private ExistingQoTCheck lastExistingQoTViolation;
    private int lastRejectedCandidateSlot = -1;

    public RobustCfssWorkingEvaluator(
            SpectrumState spectrum,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard) {
        this(spectrum, bitloading, existingQoTGuard, true);
    }

    public RobustCfssWorkingEvaluator(
            SpectrumState spectrum,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard,
            boolean checkExistingConnectionQoT) {
        if (spectrum == null || bitloading == null || existingQoTGuard == null) {
            throw new IllegalArgumentException("Spectrum, bitloading and existing QoT guard are required");
        }
        this.spectrum = spectrum;
        this.bitloading = bitloading;
        this.existingQoTGuard = existingQoTGuard;
        this.checkExistingConnectionQoT = checkExistingConnectionQoT;
    }

    public CfssEvaluation evaluate(Connection connection, ContiguousSlotBlock block) {
        lastExistingQoTViolation = null;
        lastRejectedCandidateSlot = -1;
        return evaluate(connection, block, checkExistingConnectionQoT);
    }

    public ExistingQoTCheck lastExistingQoTViolation() {
        return lastExistingQoTViolation;
    }

    public int lastRejectedCandidateSlot() {
        return lastRejectedCandidateSlot;
    }

    private CfssEvaluation evaluate(Connection connection, ContiguousSlotBlock block, boolean checkExistingQoT) {
        List<Integer> chosenSlots = new ArrayList<Integer>();
        List<ModulationFormat> chosenModulations = new ArrayList<ModulationFormat>();
        int carriedRate = 0;
        int slotIndex = block.startSlot();

        while (carriedRate < connection.request().dataRateGbps()) {
            if (slotIndex >= spectrum.slotCount()) {
                return CfssEvaluation.rejected("Not enough contiguous slots before spectrum end", chosenSlots, chosenModulations, carriedRate);
            }
            if (!spectrum.isWorkingRangeUsable(connection.workingPath(), slotIndex, 1)) {
                return CfssEvaluation.rejected("Next contiguous working slot is not free", chosenSlots, chosenModulations, carriedRate);
            }

            if (checkExistingQoT) {
                List<HypotheticalSlotAllocation> currentHypotheticals = workingHypotheticals(connection, chosenSlots, slotIndex);
                ExistingQoTCheck existingCheck = existingQoTGuard.checkWithHypotheticals(currentHypotheticals);
                if (!existingCheck.isFeasible()) {
                    lastExistingQoTViolation = existingCheck;
                    lastRejectedCandidateSlot = slotIndex;
                    return CfssEvaluation.rejected("Candidate would violate QoT of an existing connection", chosenSlots, chosenModulations, carriedRate);
                }
            }

            BitloadingDecision decision = bitloading.addWorkingSlot(connection, slotIndex, chosenSlots, chosenModulations);
            if (!decision.isFeasible()) {
                return CfssEvaluation.rejected(decision.rejectionReason(), chosenSlots, decision.modulationFormats(), decision.carriedRateGbps());
            }

            chosenSlots.add(Integer.valueOf(slotIndex));
            chosenModulations = new ArrayList<ModulationFormat>(decision.modulationFormats());
            carriedRate = decision.carriedRateGbps();
            slotIndex++;
        }

        return CfssEvaluation.feasible(chosenSlots, chosenModulations, carriedRate);
    }

    private List<HypotheticalSlotAllocation> workingHypotheticals(
            Connection connection,
            List<Integer> chosenSlots,
            int targetSlot) {
        List<HypotheticalSlotAllocation> hypotheticals = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer slot : chosenSlots) {
            hypotheticals.add(new HypotheticalSlotAllocation(connection, PathRole.WORKING, connection.workingPath(), slot.intValue()));
        }
        hypotheticals.add(new HypotheticalSlotAllocation(connection, PathRole.WORKING, connection.workingPath(), targetSlot));
        return hypotheticals;
    }
}
