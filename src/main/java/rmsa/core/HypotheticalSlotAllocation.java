package rmsa.core;

public final class HypotheticalSlotAllocation {
    private final Connection connection;
    private final PathRole role;
    private final NetworkPath path;
    private final int slotIndex;

    public HypotheticalSlotAllocation(Connection connection, PathRole role, NetworkPath path, int slotIndex) {
        if (path == null) {
            throw new IllegalArgumentException("Path is required");
        }
        if (slotIndex < 0) {
            throw new IllegalArgumentException("Slot index must be non-negative");
        }
        this.connection = connection;
        this.role = role;
        this.path = path;
        this.slotIndex = slotIndex;
    }

    public Connection connection() {
        return connection;
    }

    public PathRole role() {
        return role;
    }

    public NetworkPath path() {
        return path;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public boolean isActive(FailureScenario scenario) {
        if (connection == null || role == null) {
            return true;
        }
        if (role == PathRole.WORKING) {
            return scenario.isNoFailure() || !path.containsAnyLink(scenario.failedLinkIds());
        }
        return !scenario.isNoFailure() && connection.workingPath().containsAnyLink(scenario.failedLinkIds());
    }
}
