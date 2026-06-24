package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpectrumState {
    private final int linkCount;
    private final int slotCount;
    private final SlotState[][] slots;
    private final Map<Integer, Set<Integer>> bidirectionalFailureGroups;

    public SpectrumState(int linkCount, int slotCount) {
        this(linkCount, slotCount, singletonFailureGroups(linkCount));
    }

    public SpectrumState(List<Link> topologyLinks, int slotCount) {
        this(validateTopologyLinks(topologyLinks), slotCount, buildBidirectionalFailureGroups(topologyLinks));
    }

    private SpectrumState(int linkCount, int slotCount, Map<Integer, Set<Integer>> bidirectionalFailureGroups) {
        if (linkCount <= 0 || slotCount <= 0) {
            throw new IllegalArgumentException("Link count and slot count must be positive");
        }
        this.linkCount = linkCount;
        this.slotCount = slotCount;
        this.bidirectionalFailureGroups = immutableFailureGroups(bidirectionalFailureGroups);
        this.slots = new SlotState[linkCount][slotCount];
        for (int link = 0; link < linkCount; link++) {
            for (int slot = 0; slot < slotCount; slot++) {
                slots[link][slot] = new SlotState(link, slot);
            }
        }
    }

    public SlotState slot(int linkId, int slotIndex) {
        validateLink(linkId);
        validateSlot(slotIndex);
        return slots[linkId][slotIndex];
    }

    public List<Integer> workingContiguousStarts(NetworkPath path, int width) {
        return contiguousStarts(path, width, null, PathRole.WORKING);
    }

    public List<Integer> backupContiguousStarts(NetworkPath backupPath, int width, Connection candidate) {
        return contiguousStarts(backupPath, width, candidate, PathRole.BACKUP);
    }

    public List<ContiguousSlotBlock> workingCandidateBlocks(NetworkPath path, int minimumWidth) {
        return contiguousBlocks(path, minimumWidth, null, PathRole.WORKING);
    }

    public List<ContiguousSlotBlock> backupCandidateBlocks(NetworkPath backupPath, int minimumWidth, Connection candidate) {
        return contiguousBlocks(backupPath, minimumWidth, candidate, PathRole.BACKUP);
    }

    public boolean isWorkingRangeUsable(NetworkPath path, int startSlot, int width) {
        return isUsable(path, startSlot, width, null, PathRole.WORKING);
    }

    public boolean isBackupRangeUsable(NetworkPath backupPath, int startSlot, int width, Connection candidate) {
        return isUsable(backupPath, startSlot, width, candidate, PathRole.BACKUP);
    }

    public void reserveWorking(Connection connection, int startSlot, List<ModulationFormat> modulations) {
        reserve(connection, connection.workingPath(), PathRole.WORKING, startSlot, modulations);
    }

    public void reserveBackup(Connection connection, int startSlot, List<ModulationFormat> modulations) {
        reserve(connection, connection.backupPath(), PathRole.BACKUP, startSlot, modulations);
    }

    public void releaseWorking(Connection connection, List<Integer> slotIndexes) {
        release(connection, connection.workingPath(), PathRole.WORKING, slotIndexes);
    }

    public void releaseBackup(Connection connection, List<Integer> slotIndexes) {
        release(connection, connection.backupPath(), PathRole.BACKUP, slotIndexes);
    }

    public int highestAllocatedSlotOnLink(int linkId) {
        validateLink(linkId);
        for (int slot = slotCount - 1; slot >= 0; slot--) {
            SlotState state = slots[linkId][slot];
            if (state.workingOwner() != null || state.hasBackupOwners()) {
                return slot;
            }
        }
        return -1;
    }

    public List<SlotAllocation> activeAllocationsOnLink(int linkId, FailureScenario scenario) {
        validateLink(linkId);
        if (scenario == null) {
            throw new IllegalArgumentException("Failure scenario is required");
        }
        List<SlotAllocation> active = new ArrayList<SlotAllocation>();
        for (int slot = 0; slot < slotCount; slot++) {
            SlotState state = slots[linkId][slot];
            SlotAllocation working = state.workingOwner();
            if (working != null && isWorkingActive(working.connection(), scenario)) {
                active.add(working);
            }
            for (SlotAllocation backup : state.backupOwners()) {
                if (isBackupActive(backup.connection(), scenario)) {
                    active.add(backup);
                }
            }
        }
        return active;
    }

    public List<SlotAllocation> uniqueAllocations() {
        Map<String, SlotAllocation> unique = new LinkedHashMap<String, SlotAllocation>();
        for (int link = 0; link < linkCount; link++) {
            for (int slot = 0; slot < slotCount; slot++) {
                SlotState state = slots[link][slot];
                SlotAllocation working = state.workingOwner();
                if (working != null) {
                    unique.put(allocationKey(working), working);
                }
                for (SlotAllocation backup : state.backupOwners()) {
                    unique.put(allocationKey(backup), backup);
                }
            }
        }
        return new ArrayList<SlotAllocation>(unique.values());
    }

    public int slotCount() {
        return slotCount;
    }

    public int linkCount() {
        return linkCount;
    }

    public Set<Integer> bidirectionalFailureGroupForLink(int linkId) {
        validateLink(linkId);
        Set<Integer> group = bidirectionalFailureGroups.get(Integer.valueOf(linkId));
        if (group == null || group.isEmpty()) {
            return Collections.singleton(Integer.valueOf(linkId));
        }
        return group;
    }

    public List<Set<Integer>> uniqueBidirectionalFailureGroups() {
        List<Set<Integer>> groups = new ArrayList<Set<Integer>>();
        Set<String> seen = new HashSet<String>();
        for (int linkId = 0; linkId < linkCount; linkId++) {
            Set<Integer> group = bidirectionalFailureGroupForLink(linkId);
            String key = canonicalGroupKey(group);
            if (seen.add(key)) {
                groups.add(group);
            }
        }
        return groups;
    }

    public SpectrumState copy() {
        SpectrumState copy = new SpectrumState(linkCount, slotCount, bidirectionalFailureGroups);
        for (SlotAllocation allocation : uniqueAllocations()) {
            if (allocation.role() == PathRole.WORKING) {
                copy.reserveWorking(
                        allocation.connection(),
                        allocation.slotIndex(),
                        singleModulation(allocation.modulationFormat()));
            } else {
                copy.reserveBackup(
                        allocation.connection(),
                        allocation.slotIndex(),
                        singleModulation(allocation.modulationFormat()));
            }
        }
        return copy;
    }

    private boolean isWorkingActive(Connection connection, FailureScenario scenario) {
        return scenario.isNoFailure() || !connection.workingPath().containsAnyLink(scenario.failedLinkIds());
    }

    private boolean isBackupActive(Connection connection, FailureScenario scenario) {
        return !scenario.isNoFailure() && connection.workingPath().containsAnyLink(scenario.failedLinkIds());
    }

    private String allocationKey(SlotAllocation allocation) {
        return allocation.connection().id() + "|" + allocation.role() + "|" + allocation.slotIndex();
    }

    private List<ModulationFormat> singleModulation(ModulationFormat modulationFormat) {
        List<ModulationFormat> modulations = new ArrayList<ModulationFormat>();
        modulations.add(modulationFormat);
        return modulations;
    }

    private List<Integer> contiguousStarts(NetworkPath path, int width, Connection candidate, PathRole role) {
        List<ContiguousSlotBlock> blocks = contiguousBlocks(path, width, candidate, role);
        List<Integer> starts = new ArrayList<Integer>();
        for (ContiguousSlotBlock block : blocks) {
            starts.add(block.startSlot());
        }
        return starts;
    }

    private List<ContiguousSlotBlock> contiguousBlocks(NetworkPath path, int width, Connection candidate, PathRole role) {
        if (width <= 0 || width > slotCount) {
            throw new IllegalArgumentException("Invalid contiguous width");
        }
        List<ContiguousSlotBlock> blocks = new ArrayList<ContiguousSlotBlock>();
        for (int start = 0; start <= slotCount - width; start++) {
            if (isUsable(path, start, width, candidate, role)) {
                blocks.add(new ContiguousSlotBlock(start, width));
            }
        }
        return blocks;
    }

    private boolean isUsable(NetworkPath path, int startSlot, int width, Connection candidate, PathRole role) {
        if (width <= 0 || startSlot < 0 || startSlot + width > slotCount) {
            return false;
        }
        for (Link link : path.links()) {
            for (int offset = 0; offset < width; offset++) {
                SlotState state = slot(link.id(), startSlot + offset);
                if (role == PathRole.WORKING) {
                    if (!state.isFreeForWorking()) return false;
                } else {
                    if (!state.isUsableForBackup(candidate)) return false;
                }
            }
        }
        return true;
    }

    private void reserve(Connection connection, NetworkPath path, PathRole role, int startSlot, List<ModulationFormat> modulations) {
        if (modulations == null || modulations.isEmpty()) {
            throw new IllegalArgumentException("At least one modulation is required");
        }
        if (!isUsable(path, startSlot, modulations.size(), connection, role)) {
            throw new IllegalStateException("Requested allocation is not feasible");
        }
        for (Link link : path.links()) {
            for (int offset = 0; offset < modulations.size(); offset++) {
                SlotAllocation allocation = new SlotAllocation(connection, role, startSlot + offset, modulations.get(offset));
                SlotState state = slot(link.id(), startSlot + offset);
                if (role == PathRole.WORKING) {
                    state.reserveWorking(allocation);
                } else {
                    state.reserveBackup(allocation);
                }
            }
        }
    }

    private void release(Connection connection, NetworkPath path, PathRole role, List<Integer> slotIndexes) {
        if (connection == null || path == null || role == null || slotIndexes == null) {
            return;
        }
        for (Link link : path.links()) {
            for (Integer slotIndex : slotIndexes) {
                if (slotIndex != null && slotIndex.intValue() >= 0 && slotIndex.intValue() < slotCount) {
                    slot(link.id(), slotIndex.intValue()).release(connection, role);
                }
            }
        }
    }

    private void validateLink(int linkId) {
        if (linkId < 0 || linkId >= linkCount) {
            throw new IllegalArgumentException("Unknown link id: " + linkId);
        }
    }

    private void validateSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slotCount) {
            throw new IllegalArgumentException("Unknown slot index: " + slotIndex);
        }
    }

    private static int validateTopologyLinks(List<Link> topologyLinks) {
        if (topologyLinks == null || topologyLinks.isEmpty()) {
            throw new IllegalArgumentException("At least one topology link is required");
        }
        for (int i = 0; i < topologyLinks.size(); i++) {
            Link link = topologyLinks.get(i);
            if (link == null) {
                throw new IllegalArgumentException("Topology links cannot contain null entries");
            }
            if (link.id() != i) {
                throw new IllegalArgumentException("Topology links must use compact ids matching their list index");
            }
        }
        return topologyLinks.size();
    }

    private static Map<Integer, Set<Integer>> singletonFailureGroups(int linkCount) {
        Map<Integer, Set<Integer>> groups = new LinkedHashMap<Integer, Set<Integer>>();
        for (int linkId = 0; linkId < linkCount; linkId++) {
            groups.put(Integer.valueOf(linkId), Collections.singleton(Integer.valueOf(linkId)));
        }
        return groups;
    }

    private static Map<Integer, Set<Integer>> buildBidirectionalFailureGroups(List<Link> topologyLinks) {
        Map<String, Set<Integer>> byRisk = new LinkedHashMap<String, Set<Integer>>();
        for (Link link : topologyLinks) {
            String key = physicalRiskKey(link);
            Set<Integer> group = byRisk.get(key);
            if (group == null) {
                group = new LinkedHashSet<Integer>();
                byRisk.put(key, group);
            }
            group.add(Integer.valueOf(link.id()));
        }

        Map<Integer, Set<Integer>> byLink = new LinkedHashMap<Integer, Set<Integer>>();
        for (Set<Integer> group : byRisk.values()) {
            Set<Integer> immutableGroup = Collections.unmodifiableSet(new LinkedHashSet<Integer>(group));
            for (Integer linkId : group) {
                byLink.put(linkId, immutableGroup);
            }
        }
        return byLink;
    }

    private static Map<Integer, Set<Integer>> immutableFailureGroups(Map<Integer, Set<Integer>> groups) {
        Map<Integer, Set<Integer>> copy = new LinkedHashMap<Integer, Set<Integer>>();
        for (Map.Entry<Integer, Set<Integer>> entry : groups.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<Integer>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String physicalRiskKey(Link link) {
        int firstNode = Math.min(link.origin(), link.destination());
        int secondNode = Math.max(link.origin(), link.destination());
        return firstNode + "-" + secondNode;
    }

    private static String canonicalGroupKey(Set<Integer> group) {
        List<Integer> ids = new ArrayList<Integer>(group);
        Collections.sort(ids);
        return ids.toString();
    }
}
