package rmsa.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NodeTransceiverPool {
    private final int nodeId;
    private final TransceiverKind kind;
    private final int capacity;
    private final Map<Integer, Connection> workingOwners = new LinkedHashMap<Integer, Connection>();
    private final Map<Integer, List<Connection>> backupOwners = new LinkedHashMap<Integer, List<Connection>>();

    NodeTransceiverPool(int nodeId, TransceiverKind kind, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Transceiver capacity must be non-negative");
        }
        this.nodeId = nodeId;
        this.kind = kind;
        this.capacity = capacity;
    }

    NodeTransceiverPool(NodeTransceiverPool other) {
        this.nodeId = other.nodeId;
        this.kind = other.kind;
        this.capacity = other.capacity;
        this.workingOwners.putAll(other.workingOwners);
        for (Map.Entry<Integer, List<Connection>> entry : other.backupOwners.entrySet()) {
            this.backupOwners.put(entry.getKey(), new ArrayList<Connection>(entry.getValue()));
        }
    }

    List<Integer> findWorkingUnits(int units) {
        List<Integer> ids = new ArrayList<Integer>();
        for (int id = 0; id < capacity && ids.size() < units; id++) {
            if (isFree(id)) {
                ids.add(Integer.valueOf(id));
            }
        }
        return ids;
    }

    List<Integer> findBackupUnits(Connection candidate, int units) {
        List<Integer> ids = new ArrayList<Integer>();
        for (int id = 0; id < capacity && ids.size() < units; id++) {
            if (isShareableBackupUnit(id, candidate) && !isFree(id)) {
                ids.add(Integer.valueOf(id));
            }
        }
        for (int id = 0; id < capacity && ids.size() < units; id++) {
            Integer boxed = Integer.valueOf(id);
            if (!ids.contains(boxed) && isFree(id)) {
                ids.add(boxed);
            }
        }
        return ids;
    }

    void reserveWorking(Connection connection, List<Integer> ids) {
        for (Integer id : ids) {
            if (!isFree(id.intValue())) {
                throw new IllegalStateException("Transceiver " + kind + " " + id + " at node " + nodeId + " is not free");
            }
        }
        for (Integer id : ids) {
            workingOwners.put(id, connection);
        }
    }

    void reserveBackup(Connection connection, List<Integer> ids) {
        for (Integer id : ids) {
            if (!isShareableBackupUnit(id.intValue(), connection)) {
                throw new IllegalStateException("Transceiver " + kind + " " + id + " at node " + nodeId + " is not shareable");
            }
        }
        for (Integer id : ids) {
            List<Connection> owners = backupOwners.get(id);
            if (owners == null) {
                owners = new ArrayList<Connection>();
                backupOwners.put(id, owners);
            }
            owners.add(connection);
        }
    }

    void release(Connection connection, PathRole role) {
        if (role == PathRole.WORKING) {
            List<Integer> toRemove = new ArrayList<Integer>();
            for (Map.Entry<Integer, Connection> entry : workingOwners.entrySet()) {
                if (entry.getValue().id().equals(connection.id())) {
                    toRemove.add(entry.getKey());
                }
            }
            for (Integer id : toRemove) {
                workingOwners.remove(id);
            }
        } else {
            List<Integer> emptyUnits = new ArrayList<Integer>();
            for (Map.Entry<Integer, List<Connection>> entry : backupOwners.entrySet()) {
                List<Connection> owners = entry.getValue();
                for (int i = owners.size() - 1; i >= 0; i--) {
                    if (owners.get(i).id().equals(connection.id())) {
                        owners.remove(i);
                    }
                }
                if (owners.isEmpty()) {
                    emptyUnits.add(entry.getKey());
                }
            }
            for (Integer id : emptyUnits) {
                backupOwners.remove(id);
            }
        }
    }

    int usedUnits() {
        int used = 0;
        for (int id = 0; id < capacity; id++) {
            if (!isFree(id)) {
                used++;
            }
        }
        return used;
    }

    int backupUsedUnits() {
        return backupOwners.size();
    }

    int backupOwnerCount() {
        int count = 0;
        for (List<Connection> owners : backupOwners.values()) {
            count += owners.size();
        }
        return count;
    }

    private boolean isFree(int id) {
        return !workingOwners.containsKey(Integer.valueOf(id)) && !backupOwners.containsKey(Integer.valueOf(id));
    }

    private boolean isShareableBackupUnit(int id, Connection candidate) {
        if (workingOwners.containsKey(Integer.valueOf(id))) {
            return false;
        }
        List<Connection> owners = backupOwners.get(Integer.valueOf(id));
        if (owners == null || owners.isEmpty()) {
            return true;
        }
        for (Connection owner : owners) {
            if (!owner.workingPath().isLinkDisjointWith(candidate.workingPath())) {
                return false;
            }
        }
        return true;
    }
}
