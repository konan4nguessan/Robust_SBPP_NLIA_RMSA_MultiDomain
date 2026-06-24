package rmsa.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SlotState {
    private final int linkId;
    private final int slotIndex;
    private SlotAllocation workingOwner;
    private final Map<String, SlotAllocation> backupOwners = new LinkedHashMap<String, SlotAllocation>();

    public SlotState(int linkId, int slotIndex) {
        this.linkId = linkId;
        this.slotIndex = slotIndex;
    }

    public int linkId() {
        return linkId;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public boolean isFreeForWorking() {
        return workingOwner == null && backupOwners.isEmpty();
    }

    public boolean isUsableForBackup(Connection candidate) {
        if (workingOwner != null) {
            return false;
        }
        for (SlotAllocation allocation : backupOwners.values()) {
            NetworkPath existingWorking = allocation.connection().workingPath();
            if (!existingWorking.isLinkDisjointWith(candidate.workingPath())) {
                return false;
            }
        }
        return true;
    }

    public void reserveWorking(SlotAllocation allocation) {
        if (allocation.role() != PathRole.WORKING) {
            throw new IllegalArgumentException("Expected a working allocation");
        }
        if (!isFreeForWorking()) {
            throw new IllegalStateException("Slot is not free for working allocation");
        }
        workingOwner = allocation;
    }

    public void reserveBackup(SlotAllocation allocation) {
        if (allocation.role() != PathRole.BACKUP) {
            throw new IllegalArgumentException("Expected a backup allocation");
        }
        if (!isUsableForBackup(allocation.connection())) {
            throw new IllegalStateException("Slot is not shareable for this backup allocation");
        }
        backupOwners.put(allocation.connection().id(), allocation);
    }

    public void release(Connection connection, PathRole role) {
        if (connection == null || role == null) {
            return;
        }
        if (role == PathRole.WORKING
                && workingOwner != null
                && workingOwner.connection().id().equals(connection.id())) {
            workingOwner = null;
        } else if (role == PathRole.BACKUP) {
            backupOwners.remove(connection.id());
        }
    }

    public SlotAllocation workingOwner() {
        return workingOwner;
    }

    public Collection<SlotAllocation> backupOwners() {
        return backupOwners.values();
    }

    public boolean hasBackupOwners() {
        return !backupOwners.isEmpty();
    }
}
