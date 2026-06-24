package rmsa.core;

import java.util.List;

public final class FixedModulationAssigner implements SlotModulationAssigner {
    private final ModulationFormat modulationFormat;

    public FixedModulationAssigner(ModulationFormat modulationFormat) {
        if (modulationFormat == null) {
            throw new IllegalArgumentException("Modulation format is required");
        }
        this.modulationFormat = modulationFormat;
    }

    @Override
    public ModulationFormat choose(
            Connection connection,
            NetworkPath path,
            int slotIndex,
            List<Integer> alreadyChosenSlots,
            List<ModulationFormat> alreadyChosenModulations) {
        return modulationFormat;
    }
}
