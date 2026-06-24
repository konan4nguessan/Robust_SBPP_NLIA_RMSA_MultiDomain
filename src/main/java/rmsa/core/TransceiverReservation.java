package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TransceiverReservation {
    private final Connection connection;
    private final PathRole role;
    private final List<Integer> txIds;
    private final List<Integer> rxIds;

    public TransceiverReservation(Connection connection, PathRole role, List<Integer> txIds, List<Integer> rxIds) {
        if (connection == null || role == null || txIds == null || rxIds == null) {
            throw new IllegalArgumentException("Connection, role, txIds and rxIds are required");
        }
        if (txIds.size() != rxIds.size()) {
            throw new IllegalArgumentException("Tx and Rx reservations must have the same size");
        }
        this.connection = connection;
        this.role = role;
        this.txIds = Collections.unmodifiableList(new ArrayList<Integer>(txIds));
        this.rxIds = Collections.unmodifiableList(new ArrayList<Integer>(rxIds));
    }

    public Connection connection() {
        return connection;
    }

    public PathRole role() {
        return role;
    }

    public List<Integer> txIds() {
        return txIds;
    }

    public List<Integer> rxIds() {
        return rxIds;
    }
}
