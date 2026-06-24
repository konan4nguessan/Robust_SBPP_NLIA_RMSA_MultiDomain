package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class GreedyRobustBitloading {
    private final NetworkNliEvaluator nliEvaluator;

    public GreedyRobustBitloading(NetworkNliEvaluator nliEvaluator) {
        if (nliEvaluator == null) {
            throw new IllegalArgumentException("NLI evaluator is required");
        }
        this.nliEvaluator = nliEvaluator;
    }

    public BitloadingDecision addWorkingSlot(
            Connection connection,
            int targetSlot,
            List<Integer> chosenSlots,
            List<ModulationFormat> chosenModulations) {
        if (chosenSlots.size() != chosenModulations.size()) {
            throw new IllegalArgumentException("Chosen slots and modulations must have the same size");
        }

        List<Integer> allSlots = new ArrayList<Integer>(chosenSlots);
        allSlots.add(Integer.valueOf(targetSlot));
        List<ModulationFormat> updatedModulations = new ArrayList<ModulationFormat>(chosenModulations);

        for (int i = 0; i < chosenSlots.size(); i++) {
            int existingSlot = chosenSlots.get(i).intValue();
            List<Integer> otherSlots = allSlotsExcept(allSlots, existingSlot);
            RobustSlotQoT qoT = nliEvaluator.robustQoTForWorkingPath(
                    connection.workingPath(),
                    existingSlot,
                    otherSlots);
            ModulationFormat highestAllowed = qoT.highestSupportedModulation();
            if (highestAllowed == null) {
                return BitloadingDecision.rejected("A previously chosen slot has no QoT-feasible modulation", updatedModulations);
            }
            ModulationFormat current = updatedModulations.get(i);
            if (current.sinrThresholdDb() > highestAllowed.sinrThresholdDb()) {
                updatedModulations.set(i, highestAllowed);
            }
        }

        RobustSlotQoT targetQoT = nliEvaluator.robustQoTForWorkingPath(
                connection.workingPath(),
                targetSlot,
                chosenSlots);
        ModulationFormat targetModulation = targetQoT.highestSupportedModulation();
        if (targetModulation == null) {
            return BitloadingDecision.rejected("Target slot has no QoT-feasible modulation", updatedModulations);
        }

        updatedModulations.add(targetModulation);
        return BitloadingDecision.feasible(updatedModulations);
    }

    public BitloadingDecision addBackupSlot(
            Connection connection,
            int targetSlot,
            List<Integer> chosenSlots,
            List<ModulationFormat> chosenModulations) {
        if (chosenSlots.size() != chosenModulations.size()) {
            throw new IllegalArgumentException("Chosen slots and modulations must have the same size");
        }

        List<Integer> allSlots = new ArrayList<Integer>(chosenSlots);
        allSlots.add(Integer.valueOf(targetSlot));
        List<ModulationFormat> updatedModulations = new ArrayList<ModulationFormat>(chosenModulations);

        for (int i = 0; i < chosenSlots.size(); i++) {
            int existingSlot = chosenSlots.get(i).intValue();
            List<Integer> otherSlots = allSlotsExcept(allSlots, existingSlot);
            RobustSlotQoT qoT = nliEvaluator.robustQoTForBackupPath(
                    connection.backupPath(),
                    connection.workingPath(),
                    existingSlot,
                    otherSlots);
            ModulationFormat highestAllowed = qoT.highestSupportedModulation();
            if (highestAllowed == null) {
                return BitloadingDecision.rejected("A previously chosen backup slot has no QoT-feasible modulation", updatedModulations);
            }
            ModulationFormat current = updatedModulations.get(i);
            if (current.sinrThresholdDb() > highestAllowed.sinrThresholdDb()) {
                updatedModulations.set(i, highestAllowed);
            }
        }

        RobustSlotQoT targetQoT = nliEvaluator.robustQoTForBackupPath(
                connection.backupPath(),
                connection.workingPath(),
                targetSlot,
                chosenSlots);
        ModulationFormat targetModulation = targetQoT.highestSupportedModulation();
        if (targetModulation == null) {
            return BitloadingDecision.rejected("Target backup slot has no QoT-feasible modulation", updatedModulations);
        }

        updatedModulations.add(targetModulation);
        return BitloadingDecision.feasible(updatedModulations);
    }

    private List<Integer> allSlotsExcept(List<Integer> slots, int excludedSlot) {
        List<Integer> result = new ArrayList<Integer>();
        boolean skipped = false;
        for (Integer slot : slots) {
            if (!skipped && slot.intValue() == excludedSlot) {
                skipped = true;
            } else {
                result.add(slot);
            }
        }
        return result;
    }
}
