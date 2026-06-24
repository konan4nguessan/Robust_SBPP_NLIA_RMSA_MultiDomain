package rmsa.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TransceiverState {
    private final int txPerNode;
    private final int rxPerNode;
    private final Map<Integer, NodeTransceiverPool> txPools = new LinkedHashMap<Integer, NodeTransceiverPool>();
    private final Map<Integer, NodeTransceiverPool> rxPools = new LinkedHashMap<Integer, NodeTransceiverPool>();

    public TransceiverState(int txPerNode, int rxPerNode) {
        if (txPerNode < 0 || rxPerNode < 0) {
            throw new IllegalArgumentException("Tx/Rx capacities must be non-negative");
        }
        this.txPerNode = txPerNode;
        this.rxPerNode = rxPerNode;
    }

    public TransceiverReservation reserveWorking(Connection connection, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Reserved units must be positive");
        }
        NodeTransceiverPool tx = txPool(connection.request().source());
        NodeTransceiverPool rx = rxPool(connection.request().destination());
        List<Integer> txIds = tx.findWorkingUnits(units);
        List<Integer> rxIds = rx.findWorkingUnits(units);
        if (txIds.size() < units || rxIds.size() < units) {
            throw new IllegalStateException("Not enough free working Tx/Rx resources");
        }
        tx.reserveWorking(connection, txIds);
        rx.reserveWorking(connection, rxIds);
        return new TransceiverReservation(connection, PathRole.WORKING, txIds, rxIds);
    }

    public TransceiverReservation reserveBackup(Connection connection, int units) {
        if (units <= 0) {
            throw new IllegalArgumentException("Reserved units must be positive");
        }
        NodeTransceiverPool tx = txPool(connection.request().source());
        NodeTransceiverPool rx = rxPool(connection.request().destination());
        List<Integer> txIds = tx.findBackupUnits(connection, units);
        List<Integer> rxIds = rx.findBackupUnits(connection, units);
        if (txIds.size() < units || rxIds.size() < units) {
            throw new IllegalStateException("Not enough free/shareable backup Tx/Rx resources");
        }
        tx.reserveBackup(connection, txIds);
        rx.reserveBackup(connection, rxIds);
        return new TransceiverReservation(connection, PathRole.BACKUP, txIds, rxIds);
    }

    public void release(Connection connection, PathRole role) {
        txPool(connection.request().source()).release(connection, role);
        rxPool(connection.request().destination()).release(connection, role);
    }

    public int usedTransmitters() {
        int used = 0;
        for (NodeTransceiverPool pool : txPools.values()) {
            used += pool.usedUnits();
        }
        return used;
    }

    public int usedReceivers() {
        int used = 0;
        for (NodeTransceiverPool pool : rxPools.values()) {
            used += pool.usedUnits();
        }
        return used;
    }

    public double txShareability() {
        return shareability(txPools);
    }

    public double rxShareability() {
        return shareability(rxPools);
    }

    public TransceiverState copy() {
        TransceiverState copy = new TransceiverState(txPerNode, rxPerNode);
        for (Map.Entry<Integer, NodeTransceiverPool> entry : txPools.entrySet()) {
            copy.txPools.put(entry.getKey(), new NodeTransceiverPool(entry.getValue()));
        }
        for (Map.Entry<Integer, NodeTransceiverPool> entry : rxPools.entrySet()) {
            copy.rxPools.put(entry.getKey(), new NodeTransceiverPool(entry.getValue()));
        }
        return copy;
    }

    private double shareability(Map<Integer, NodeTransceiverPool> pools) {
        int backupUnits = 0;
        int owners = 0;
        for (NodeTransceiverPool pool : pools.values()) {
            backupUnits += pool.backupUsedUnits();
            owners += pool.backupOwnerCount();
        }
        if (backupUnits == 0) {
            return 0.0;
        }
        return (double) owners / (double) backupUnits;
    }

    private NodeTransceiverPool txPool(int nodeId) {
        NodeTransceiverPool pool = txPools.get(Integer.valueOf(nodeId));
        if (pool == null) {
            pool = new NodeTransceiverPool(nodeId, TransceiverKind.TX, txPerNode);
            txPools.put(Integer.valueOf(nodeId), pool);
        }
        return pool;
    }

    private NodeTransceiverPool rxPool(int nodeId) {
        NodeTransceiverPool pool = rxPools.get(Integer.valueOf(nodeId));
        if (pool == null) {
            pool = new NodeTransceiverPool(nodeId, TransceiverKind.RX, rxPerNode);
            rxPools.put(Integer.valueOf(nodeId), pool);
        }
        return pool;
    }
}
