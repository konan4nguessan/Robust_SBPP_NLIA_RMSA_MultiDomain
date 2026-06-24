package rmsa.core;

import java.util.List;

public interface ExistingQoTGuard {
    ExistingQoTCheck checkWithHypothetical(HypotheticalSlotAllocation candidate);

    ExistingQoTCheck checkWithHypotheticals(List<HypotheticalSlotAllocation> candidates);
}
