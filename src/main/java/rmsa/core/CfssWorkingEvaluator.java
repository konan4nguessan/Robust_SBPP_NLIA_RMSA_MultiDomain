package rmsa.core;

import java.util.ArrayList;
import java.util.List;

public final class CfssWorkingEvaluator {
    private final SpectrumState spectrum;
    private final SlotModulationAssigner modulationAssigner;

    public CfssWorkingEvaluator(SpectrumState spectrum, SlotModulationAssigner modulationAssigner) {
        if (spectrum == null || modulationAssigner == null) {
            throw new IllegalArgumentException("Spectrum and modulation assigner are required");
        }
        this.spectrum = spectrum;
        this.modulationAssigner = modulationAssigner;
    }

    public CfssEvaluation evaluate(Connection connection, ContiguousSlotBlock block) {
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

            ModulationFormat modulation = modulationAssigner.choose(
                    connection,
                    connection.workingPath(),
                    slotIndex,
                    chosenSlots,
                    chosenModulations);
            if (modulation == null) {
                return CfssEvaluation.rejected("No modulation satisfies the candidate slot", chosenSlots, chosenModulations, carriedRate);
            }

            chosenSlots.add(slotIndex);
            chosenModulations.add(modulation);
            carriedRate += modulation.dataRateGbps();
            slotIndex++;
        }

        return CfssEvaluation.feasible(chosenSlots, chosenModulations, carriedRate);
    }
}
