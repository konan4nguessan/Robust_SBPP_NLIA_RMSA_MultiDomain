package rmsa.core;

public final class RobustSlotQoT {
    private final int slotIndex;
    private final FailureScenario worstCaseScenario;
    private final double nliMaxW;
    private final SlotQoT qoT;

    public RobustSlotQoT(int slotIndex, FailureScenario worstCaseScenario, double nliMaxW, SlotQoT qoT) {
        if (worstCaseScenario == null || qoT == null || nliMaxW < 0) {
            throw new IllegalArgumentException("Robust QoT inputs are invalid");
        }
        this.slotIndex = slotIndex;
        this.worstCaseScenario = worstCaseScenario;
        this.nliMaxW = nliMaxW;
        this.qoT = qoT;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public FailureScenario worstCaseScenario() {
        return worstCaseScenario;
    }

    public double nliMaxW() {
        return nliMaxW;
    }

    public SlotQoT qoT() {
        return qoT;
    }

    public double sinrMinDb() {
        return qoT.sinrDb();
    }

    public ModulationFormat highestSupportedModulation() {
        return qoT.highestSupportedModulation();
    }
}
