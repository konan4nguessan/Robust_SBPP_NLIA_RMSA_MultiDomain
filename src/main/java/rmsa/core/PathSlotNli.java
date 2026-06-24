package rmsa.core;

public final class PathSlotNli {
    private final NetworkPath path;
    private final int slotIndex;
    private final FailureScenario scenario;
    private final InterferenceBreakdown interference;

    public PathSlotNli(NetworkPath path, int slotIndex, FailureScenario scenario, InterferenceBreakdown interference) {
        if (path == null || scenario == null || interference == null) {
            throw new IllegalArgumentException("Path, scenario and interference are required");
        }
        this.path = path;
        this.slotIndex = slotIndex;
        this.scenario = scenario;
        this.interference = interference;
    }

    public NetworkPath path() {
        return path;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public FailureScenario scenario() {
        return scenario;
    }

    public InterferenceBreakdown interference() {
        return interference;
    }

    public double totalNliW() {
        return interference.totalNliW();
    }
}
