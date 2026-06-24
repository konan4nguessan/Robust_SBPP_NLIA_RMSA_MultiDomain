package rmsa.core;

public final class ExistingQoTCheck {
    private final boolean feasible;
    private final SlotAllocation violatingAllocation;
    private final RobustSlotQoT violatingQoT;

    private ExistingQoTCheck(boolean feasible, SlotAllocation violatingAllocation, RobustSlotQoT violatingQoT) {
        this.feasible = feasible;
        this.violatingAllocation = violatingAllocation;
        this.violatingQoT = violatingQoT;
    }

    public static ExistingQoTCheck feasible() {
        return new ExistingQoTCheck(true, null, null);
    }

    public static ExistingQoTCheck rejected(SlotAllocation allocation, RobustSlotQoT qoT) {
        return new ExistingQoTCheck(false, allocation, qoT);
    }

    public boolean isFeasible() {
        return feasible;
    }

    public SlotAllocation violatingAllocation() {
        return violatingAllocation;
    }

    public RobustSlotQoT violatingQoT() {
        return violatingQoT;
    }
}
