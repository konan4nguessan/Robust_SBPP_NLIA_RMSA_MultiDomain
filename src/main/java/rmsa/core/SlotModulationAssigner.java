package rmsa.core;

import java.util.List;

public interface SlotModulationAssigner {
    ModulationFormat choose(
            Connection connection,
            NetworkPath path,
            int slotIndex,
            List<Integer> alreadyChosenSlots,
            List<ModulationFormat> alreadyChosenModulations);
}
