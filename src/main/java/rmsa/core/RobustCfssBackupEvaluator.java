package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class RobustCfssBackupEvaluator {
    private final SpectrumState spectrum;
    private final GreedyRobustBitloading bitloading;
    private final ExistingQoTGuard existingQoTGuard;
    private final boolean checkExistingConnectionQoT;
    private ExistingQoTCheck lastExistingQoTViolation;
    private int lastRejectedCandidateSlot = -1;

    public RobustCfssBackupEvaluator(
            SpectrumState spectrum,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard) {
        this(spectrum, bitloading, existingQoTGuard, true);
    }

    public RobustCfssBackupEvaluator(
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
        return evaluate(connection, block, checkExistingConnectionQoT, new ArrayList<HypotheticalSlotAllocation>());
    }

    public CfssEvaluation evaluateWithWorkingAllocation(
            Connection connection,
            ContiguousSlotBlock block,
            CfssEvaluation working) {
        if (working == null || !working.isFeasible()) {
            throw new IllegalArgumentException("A feasible working CFSS is required before backup CFSS evaluation");
        }
        lastExistingQoTViolation = null;
        lastRejectedCandidateSlot = -1;
        return evaluate(connection, block, checkExistingConnectionQoT, workingHypotheticals(connection, working));
    }

    public ExistingQoTCheck lastExistingQoTViolation() {
        return lastExistingQoTViolation;
    }

    public int lastRejectedCandidateSlot() {
        return lastRejectedCandidateSlot;
    }

    private CfssEvaluation evaluate(
            Connection connection,
            ContiguousSlotBlock block,
            boolean checkExistingQoT,
            List<HypotheticalSlotAllocation> committedHypotheticals) {
        List<Integer> chosenSlots = new ArrayList<Integer>();
        List<ModulationFormat> chosenModulations = new ArrayList<ModulationFormat>();
        int carriedRate = 0;
        int slotIndex = block.startSlot();

        while (carriedRate < connection.request().dataRateGbps()) {
            if (slotIndex >= spectrum.slotCount()) {
                return CfssEvaluation.rejected("Not enough contiguous backup slots before spectrum end", chosenSlots, chosenModulations, carriedRate);
            }
            if (!spectrum.isBackupRangeUsable(connection.backupPath(), slotIndex, 1, connection)) {
                return CfssEvaluation.rejected("Next contiguous backup slot is not free or shareable", chosenSlots, chosenModulations, carriedRate);
            }

            if (checkExistingQoT) {
                List<HypotheticalSlotAllocation> currentHypotheticals = new ArrayList<HypotheticalSlotAllocation>(committedHypotheticals);
                currentHypotheticals.addAll(backupHypotheticals(connection, chosenSlots, slotIndex));
                ExistingQoTCheck existingCheck = existingQoTGuard.checkWithHypotheticals(currentHypotheticals);
                if (!existingCheck.isFeasible()) {
                    lastExistingQoTViolation = existingCheck;
                    lastRejectedCandidateSlot = slotIndex;
                    return CfssEvaluation.rejected("Backup candidate would violate QoT of an existing connection", chosenSlots, chosenModulations, carriedRate);
                }
            }

            BitloadingDecision decision = bitloading.addBackupSlot(connection, slotIndex, chosenSlots, chosenModulations);
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
            CfssEvaluation working) {
        List<HypotheticalSlotAllocation> hypotheticals = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer slot : working.slotIndexes()) {
            hypotheticals.add(new HypotheticalSlotAllocation(
                    connection,
                    PathRole.WORKING,
                    connection.workingPath(),
                    slot.intValue()));
        }
        return hypotheticals;
    }

    private List<HypotheticalSlotAllocation> backupHypotheticals(
            Connection connection,
            List<Integer> chosenSlots,
            int targetSlot) {
        List<HypotheticalSlotAllocation> hypotheticals = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer slot : chosenSlots) {
            hypotheticals.add(new HypotheticalSlotAllocation(connection, PathRole.BACKUP, connection.backupPath(), slot.intValue()));
        }
        hypotheticals.add(new HypotheticalSlotAllocation(connection, PathRole.BACKUP, connection.backupPath(), targetSlot));
        return hypotheticals;
    }
}
