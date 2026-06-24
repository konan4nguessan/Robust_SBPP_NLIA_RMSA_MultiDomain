package rmsa.core;

public final class SlotAllocation {
    private final Connection connection;
    private final PathRole role;
    private final int slotIndex;
    private final ModulationFormat modulationFormat;

    public SlotAllocation(Connection connection, PathRole role, int slotIndex, ModulationFormat modulationFormat) {
        this.connection = connection;
        this.role = role;
        this.slotIndex = slotIndex;
        this.modulationFormat = modulationFormat;
    }

    public Connection connection() {
        return connection;
    }

    public PathRole role() {
        return role;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public ModulationFormat modulationFormat() {
        return modulationFormat;
    }
}
