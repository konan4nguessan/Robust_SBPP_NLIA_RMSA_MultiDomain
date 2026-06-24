package rmsa.net2plan.actual;

import java.util.HashSet;
import java.util.Set;

import rmsa.core.Connection;
import rmsa.core.FailureScenario;
import rmsa.core.NetworkNliEvaluator;
import rmsa.core.NetworkPath;
import rmsa.core.PathRole;
import rmsa.core.RobustSlotQoT;
import rmsa.core.SlotState;
import rmsa.core.SlotAllocation;
import rmsa.core.SpectrumState;

public final class RobustSbppRmsaMetrics {
    private long arrivals;
    private long accepted;
    private long blocked;
    private long departures;
    private long intraDomainArrivals;
    private long interDomainArrivals;
    private long intraDomainAccepted;
    private long interDomainAccepted;
    private long intraDomainBlocked;
    private long interDomainBlocked;
    private double intraDomainAcceptedGbps;
    private double interDomainAcceptedGbps;
    private long acceptedDomainTransitions;
    private long maxAcceptedDomainTransitions;
    private double requestedGbps;
    private double acceptedGbps;
    private double blockedGbps;
    private long failureEvents;
    private long repairEvents;
    private long lastAffectedConnections;
    private long lastActivatedBackups;
    private long activeBackupConnections;
    private long cumulativeAffectedConnections;
    private long cumulativeActivatedBackups;
    private double lastAffectedGbps;
    private double lastRestoredGbps;
    private double lastLostGbps;
    private double cumulativeAffectedGbps;
    private double cumulativeRestoredGbps;
    private double cumulativeLostGbps;
    private String lastFailedBidirectionalLinks = "";
    private long postFailureQoTCheckedConnections;
    private long postFailureQoTCheckedSlots;
    private long postFailureQoTViolationsStrict;
    private long postFailureQoTHeadroomSamples;
    private double postFailureQoTHeadroomDbSum;
    private double postFailureQoTWorstHeadroomDb;

    public void reset() {
        arrivals = 0L;
        accepted = 0L;
        blocked = 0L;
        departures = 0L;
        intraDomainArrivals = 0L;
        interDomainArrivals = 0L;
        intraDomainAccepted = 0L;
        interDomainAccepted = 0L;
        intraDomainBlocked = 0L;
        interDomainBlocked = 0L;
        intraDomainAcceptedGbps = 0.0;
        interDomainAcceptedGbps = 0.0;
        acceptedDomainTransitions = 0L;
        maxAcceptedDomainTransitions = 0L;
        requestedGbps = 0.0;
        acceptedGbps = 0.0;
        blockedGbps = 0.0;
        failureEvents = 0L;
        repairEvents = 0L;
        lastAffectedConnections = 0L;
        lastActivatedBackups = 0L;
        activeBackupConnections = 0L;
        cumulativeAffectedConnections = 0L;
        cumulativeActivatedBackups = 0L;
        lastAffectedGbps = 0.0;
        lastRestoredGbps = 0.0;
        lastLostGbps = 0.0;
        cumulativeAffectedGbps = 0.0;
        cumulativeRestoredGbps = 0.0;
        cumulativeLostGbps = 0.0;
        lastFailedBidirectionalLinks = "";
        postFailureQoTCheckedConnections = 0L;
        postFailureQoTCheckedSlots = 0L;
        postFailureQoTViolationsStrict = 0L;
        postFailureQoTHeadroomSamples = 0L;
        postFailureQoTHeadroomDbSum = 0.0D;
        postFailureQoTWorstHeadroomDb = 0.0D;
    }

    public void recordArrival(double gbps) {
        recordArrival(gbps, false);
    }

    public void recordArrival(double gbps, boolean interDomain) {
        arrivals++;
        requestedGbps += gbps;
        if (interDomain) {
            interDomainArrivals++;
        } else {
            intraDomainArrivals++;
        }
    }

    public void recordAccepted(double gbps) {
        recordAccepted(gbps, false);
    }

    public void recordAccepted(double gbps, boolean interDomain) {
        recordAccepted(gbps, interDomain, 0);
    }

    public void recordAccepted(double gbps, boolean interDomain, int domainTransitions) {
        accepted++;
        acceptedGbps += gbps;
        if (interDomain) {
            interDomainAccepted++;
            interDomainAcceptedGbps += gbps;
        } else {
            intraDomainAccepted++;
            intraDomainAcceptedGbps += gbps;
        }
        int safeTransitions = Math.max(0, domainTransitions);
        acceptedDomainTransitions += safeTransitions;
        if (safeTransitions > maxAcceptedDomainTransitions) {
            maxAcceptedDomainTransitions = safeTransitions;
        }
    }

    public void recordBlocked(double gbps) {
        recordBlocked(gbps, false);
    }

    public void recordBlocked(double gbps, boolean interDomain) {
        blocked++;
        blockedGbps += gbps;
        if (interDomain) {
            interDomainBlocked++;
        } else {
            intraDomainBlocked++;
        }
    }

    public void recordDeparture() {
        departures++;
    }


    public void recordFailureOutcome(
            String failedBidirectionalLinks,
            long affectedConnections,
            long activatedBackups,
            double affectedGbps,
            double restoredGbps,
            double lostGbps,
            long activeBackupsAfterEvent) {
        failureEvents++;
        lastFailedBidirectionalLinks = failedBidirectionalLinks == null ? "" : failedBidirectionalLinks;
        lastAffectedConnections = affectedConnections;
        lastActivatedBackups = activatedBackups;
        lastAffectedGbps = affectedGbps;
        lastRestoredGbps = restoredGbps;
        lastLostGbps = lostGbps;
        activeBackupConnections = activeBackupsAfterEvent;
        cumulativeAffectedConnections += affectedConnections;
        cumulativeActivatedBackups += activatedBackups;
        cumulativeAffectedGbps += affectedGbps;
        cumulativeRestoredGbps += restoredGbps;
        cumulativeLostGbps += lostGbps;
    }

    public void recordRepairOutcome(long activeBackupsBeforeRepair, long activeBackupsAfterRepair) {
        repairEvents++;
        activeBackupConnections = activeBackupsAfterRepair;
        lastAffectedConnections = activeBackupsBeforeRepair;
        lastActivatedBackups = activeBackupsAfterRepair;
        lastAffectedGbps = 0.0;
        lastRestoredGbps = 0.0;
        lastLostGbps = 0.0;
    }

    public double lastRestorationSuccessRatio() {
        if (lastAffectedConnections <= 0L) {
            return 0.0;
        }
        return (double) lastActivatedBackups / (double) lastAffectedConnections;
    }

    public double cumulativeRestorationSuccessRatio() {
        if (cumulativeAffectedConnections <= 0L) {
            return 0.0;
        }
        return (double) cumulativeActivatedBackups / (double) cumulativeAffectedConnections;
    }

    public void recordPostFailureQoTAudit(
            SpectrumState spectrum,
            NetworkNliEvaluator nliEvaluator,
            FailureScenario scenario) {
        if (spectrum == null || nliEvaluator == null || scenario == null || scenario.isNoFailure()) {
            return;
        }
        Set<String> checkedConnectionIds = new HashSet<String>();
        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            if (!isAllocationActive(allocation, scenario)) {
                continue;
            }
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            RobustSlotQoT qoT = nliEvaluator.robustQoT(
                    path,
                    allocation.slotIndex(),
                    java.util.Collections.singletonList(scenario));
            double headroomDb = qoT.sinrMinDb() - allocation.modulationFormat().sinrThresholdDb();
            checkedConnectionIds.add(allocation.connection().id());
            postFailureQoTCheckedSlots++;
            postFailureQoTHeadroomSamples++;
            postFailureQoTHeadroomDbSum += headroomDb;
            if (postFailureQoTHeadroomSamples == 1L || headroomDb < postFailureQoTWorstHeadroomDb) {
                postFailureQoTWorstHeadroomDb = headroomDb;
            }
            if (headroomDb < 0.0D) {
                postFailureQoTViolationsStrict++;
            }
        }
        postFailureQoTCheckedConnections += checkedConnectionIds.size();
    }
    public long acceptedConnections() {
        return accepted;
    }

    public double bandwidthBlockingProbability() {
        if (requestedGbps <= 0.0) {
            return 0.0;
        }
        return blockedGbps / requestedGbps;
    }

    public double averageFragmentation(SpectrumState spectrum) {
        if (spectrum == null || spectrum.linkCount() == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (int linkId = 0; linkId < spectrum.linkCount(); linkId++) {
            sum += fragmentationOnLink(spectrum, linkId);
        }
        return sum / spectrum.linkCount();
    }

    public String report(SpectrumState spectrum) {
        StringBuilder builder = new StringBuilder();
        builder.append("Robust SBPP RMSA dynamic report\n");
        builder.append("arrivals=").append(arrivals).append('\n');
        builder.append("accepted=").append(accepted).append('\n');
        builder.append("blocked=").append(blocked).append('\n');
        builder.append("departures=").append(departures).append('\n');
        builder.append("intraDomainArrivals=").append(intraDomainArrivals).append('\n');
        builder.append("interDomainArrivals=").append(interDomainArrivals).append('\n');
        builder.append("intraDomainAccepted=").append(intraDomainAccepted).append('\n');
        builder.append("interDomainAccepted=").append(interDomainAccepted).append('\n');
        builder.append("intraDomainBlocked=").append(intraDomainBlocked).append('\n');
        builder.append("interDomainBlocked=").append(interDomainBlocked).append('\n');
        builder.append("requestedGbps=").append(requestedGbps).append('\n');
        builder.append("acceptedGbps=").append(acceptedGbps).append('\n');
        builder.append("intraDomainAcceptedGbps=").append(intraDomainAcceptedGbps).append('\n');
        builder.append("interDomainAcceptedGbps=").append(interDomainAcceptedGbps).append('\n');
        builder.append("acceptedDomainTransitions=").append(acceptedDomainTransitions).append('\n');
        builder.append("maxAcceptedDomainTransitions=").append(maxAcceptedDomainTransitions).append('\n');
        builder.append("blockedGbps=").append(blockedGbps).append('\n');
        builder.append("bbp=").append(bandwidthBlockingProbability()).append('\n');
        builder.append("fragmentation=").append(averageFragmentation(spectrum)).append('\n');
        SpectrumLoadSummary load = spectrumLoadSummary(spectrum);
        builder.append("totalCarriedSlots=").append(load.totalCarriedSlots).append('\n');
        builder.append("totalReservedSlots=").append(load.totalReservedSlots).append('\n');
        builder.append("totalOccupiedSlots=").append(load.totalOccupiedSlots).append('\n');
        builder.append("totalFreeSlots=").append(load.totalFreeSlots).append('\n');
        builder.append("averageLinkSpectralOccupancy=").append(load.averageOccupancyRatio).append('\n');
        builder.append("maxLinkSpectralOccupancy=").append(load.maxOccupancyRatio).append('\n');
        builder.append("failureEvents=").append(failureEvents).append('\n');
        builder.append("repairEvents=").append(repairEvents).append('\n');
        builder.append("lastFailedBidirectionalLinks=").append(lastFailedBidirectionalLinks).append('\n');
        builder.append("lastAffectedConnections=").append(lastAffectedConnections).append('\n');
        builder.append("lastActivatedBackups=").append(lastActivatedBackups).append('\n');
        builder.append("activeBackupConnections=").append(activeBackupConnections).append('\n');
        builder.append("lastAffectedGbps=").append(lastAffectedGbps).append('\n');
        builder.append("lastRestoredGbps=").append(lastRestoredGbps).append('\n');
        builder.append("lastLostGbps=").append(lastLostGbps).append('\n');
        builder.append("lastRestorationSuccessRatio=").append(lastRestorationSuccessRatio()).append('\n');
        builder.append("cumulativeAffectedConnections=").append(cumulativeAffectedConnections).append('\n');
        builder.append("cumulativeActivatedBackups=").append(cumulativeActivatedBackups).append('\n');
        builder.append("cumulativeAffectedGbps=").append(cumulativeAffectedGbps).append('\n');
        builder.append("cumulativeRestoredGbps=").append(cumulativeRestoredGbps).append('\n');
        builder.append("cumulativeLostGbps=").append(cumulativeLostGbps).append('\n');
        builder.append("cumulativeRestorationSuccessRatio=").append(cumulativeRestorationSuccessRatio()).append('\n');
        builder.append("postFailureQoTCheckedConnections=").append(postFailureQoTCheckedConnections).append('\n');
        builder.append("postFailureQoTCheckedSlots=").append(postFailureQoTCheckedSlots).append('\n');
        builder.append("postFailureQoTViolationsStrict=").append(postFailureQoTViolationsStrict).append('\n');
        builder.append("postFailureQoTWorstHeadroomDb=").append(postFailureQoTWorstHeadroomDb).append('\n');
        builder.append("postFailureQoTAverageHeadroomDb=").append(averagePostFailureQoTHeadroomDb()).append('\n');
        return builder.toString();
    }

    private boolean isAllocationActive(SlotAllocation allocation, FailureScenario scenario) {
        if (allocation == null || scenario == null) {
            return false;
        }
        Connection connection = allocation.connection();
        if (allocation.role() == PathRole.WORKING) {
            return scenario.isNoFailure() || !connection.workingPath().containsAnyLink(scenario.failedLinkIds());
        }
        return !scenario.isNoFailure() && connection.workingPath().containsAnyLink(scenario.failedLinkIds());
    }

    private double averagePostFailureQoTHeadroomDb() {
        return postFailureQoTHeadroomSamples <= 0L
                ? 0.0D
                : postFailureQoTHeadroomDbSum / (double) postFailureQoTHeadroomSamples;
    }

    private SpectrumLoadSummary spectrumLoadSummary(SpectrumState spectrum) {
        SpectrumLoadSummary summary = new SpectrumLoadSummary();
        if (spectrum == null || spectrum.linkCount() == 0) {
            return summary;
        }
        double occupancyRatioSum = 0.0D;
        for (int linkId = 0; linkId < spectrum.linkCount(); linkId++) {
            int carried = 0;
            int reserved = 0;
            int occupied = 0;
            for (int slot = 0; slot < spectrum.slotCount(); slot++) {
                SlotState state = spectrum.slot(linkId, slot);
                if (state.workingOwner() != null) {
                    carried++;
                    occupied++;
                } else if (state.hasBackupOwners()) {
                    reserved++;
                    occupied++;
                }
            }
            int free = Math.max(0, spectrum.slotCount() - occupied);
            double ratio = spectrum.slotCount() == 0 ? 0.0D : (double) occupied / (double) spectrum.slotCount();
            summary.totalCarriedSlots += carried;
            summary.totalReservedSlots += reserved;
            summary.totalOccupiedSlots += occupied;
            summary.totalFreeSlots += free;
            occupancyRatioSum += ratio;
            if (ratio > summary.maxOccupancyRatio) {
                summary.maxOccupancyRatio = ratio;
            }
        }
        summary.averageOccupancyRatio = occupancyRatioSum / (double) spectrum.linkCount();
        return summary;
    }

    private static final class SpectrumLoadSummary {
        private int totalCarriedSlots;
        private int totalReservedSlots;
        private int totalOccupiedSlots;
        private int totalFreeSlots;
        private double averageOccupancyRatio;
        private double maxOccupancyRatio;
    }
    private double fragmentationOnLink(SpectrumState spectrum, int linkId) {
        int freeSlots = 0;
        int currentFreeRun = 0;
        int largestFreeRun = 0;
        for (int slot = 0; slot < spectrum.slotCount(); slot++) {
            SlotState state = spectrum.slot(linkId, slot);
            boolean free = state.workingOwner() == null && !state.hasBackupOwners();
            if (free) {
                freeSlots++;
                currentFreeRun++;
                if (currentFreeRun > largestFreeRun) {
                    largestFreeRun = currentFreeRun;
                }
            } else {
                currentFreeRun = 0;
            }
        }
        if (freeSlots == 0) {
            return 0.0;
        }
        return 1.0 - ((double) largestFreeRun / (double) freeSlots);
    }
}
