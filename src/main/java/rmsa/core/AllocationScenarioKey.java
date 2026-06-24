package rmsa.core;

import java.util.Objects;

public final class AllocationScenarioKey {
    private final String connectionId;
    private final PathRole role;
    private final int slotIndex;
    private final FailureScenario scenario;

    public AllocationScenarioKey(String connectionId, PathRole role, int slotIndex, FailureScenario scenario) {
        if (connectionId == null || role == null || scenario == null) {
            throw new IllegalArgumentException("Connection id, role and scenario are required");
        }
        this.connectionId = connectionId;
        this.role = role;
        this.slotIndex = slotIndex;
        this.scenario = scenario;
    }

    public static AllocationScenarioKey of(SlotAllocation allocation, FailureScenario scenario) {
        return new AllocationScenarioKey(
                allocation.connection().id(),
                allocation.role(),
                allocation.slotIndex(),
                scenario);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AllocationScenarioKey)) return false;
        AllocationScenarioKey that = (AllocationScenarioKey) other;
        return slotIndex == that.slotIndex
                && connectionId.equals(that.connectionId)
                && role == that.role
                && scenario.equals(that.scenario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId, role, slotIndex, scenario);
    }
}
