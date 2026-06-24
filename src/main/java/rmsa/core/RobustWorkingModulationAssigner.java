package rmsa.core;

import java.util.List;

public final class RobustWorkingModulationAssigner implements SlotModulationAssigner {
    private final NetworkNliEvaluator nliEvaluator;

    public RobustWorkingModulationAssigner(NetworkNliEvaluator nliEvaluator) {
        if (nliEvaluator == null) {
            throw new IllegalArgumentException("NLI evaluator is required");
        }
        this.nliEvaluator = nliEvaluator;
    }

    @Override
    public ModulationFormat choose(
            Connection connection,
            NetworkPath path,
            int slotIndex,
            List<Integer> alreadyChosenSlots,
            List<ModulationFormat> alreadyChosenModulations) {
        RobustSlotQoT qoT = nliEvaluator.robustQoTForWorkingPath(path, slotIndex, alreadyChosenSlots);
        return qoT.highestSupportedModulation();
    }
}
