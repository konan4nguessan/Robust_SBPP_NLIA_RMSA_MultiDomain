package rmsa.net2plan.actual;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;

import rmsa.core.SlotState;
import rmsa.core.SpectrumState;
import rmsa.net2plan.AdaptedNetwork;

public final class Net2PlanSpectrumSynchronizer {
    public static final String ATTR_SPECTRUM = "SPECTRUM";
    public static final String ATTR_CARRIED_SLOTS = "carriedSlots";
    public static final String ATTR_RESERVED_SLOTS = "reservedSlots";
    public static final String ATTR_OCCUPIED_SLOTS = "occupiedSlots";
    public static final String ATTR_ACTIVE_BACKUP_SLOTS = "activeBackupSlots";
    public static final String ATTR_FREE_SLOTS = "freeSlots";
    public static final String ATTR_OCCUPANCY_RATIO = "occupancyRatio";
    public static final String ATTR_SPECTRUM_OCCUPIED_PERCENT = "spectrumOccupiedPercent";
    public static final String ATTR_SPECTRUM_CARRIED_PERCENT = "spectrumCarriedPercent";
    public static final String ATTR_SPECTRUM_RESERVED_PERCENT = "spectrumReservedPercent";
    public static final String ATTR_SPECTRUM_ACTIVE_PERCENT = "spectrumActiveBackupPercent";
    public static final String ATTR_SPECTRAL_STATUS = "spectralStatus";
    public static final String ATTR_SPECTRAL_SUMMARY = "spectralSummary";
    public static final String ATTR_TRAFFIC_CARRIED_GBPS = "trafficCarriedGbps";
    public static final String ATTR_TRAFFIC_OCCUPIED_GBPS = "trafficOccupiedGbps";
    public static final String ATTR_TRAFFIC_LOAD_PERCENT = "trafficLoadPercent";
    public static final String ATTR_SPECTRAL_LOAD_PERCENT = "spectralLoadPercent";

    public SpectrumSnapshot sync(NetPlan netPlan, AdaptedNetwork adaptedNetwork) {
        return sync(netPlan, adaptedNetwork, Collections.<String>emptySet());
    }

    public SpectrumSnapshot sync(
            NetPlan netPlan,
            AdaptedNetwork adaptedNetwork,
            Set<String> activeBackupConnectionIds) {
        SpectrumSnapshot snapshot = new SpectrumSnapshot();
        if (netPlan == null || adaptedNetwork == null) {
            return snapshot;
        }
        Set<String> activeBackups = activeBackupConnectionIds == null
                ? Collections.<String>emptySet()
                : activeBackupConnectionIds;
        SpectrumState spectrum = adaptedNetwork.spectrum();
        for (int coreLinkId = 0; coreLinkId < spectrum.linkCount(); coreLinkId++) {
            Link net2PlanLink = findLinkById(netPlan, adaptedNetwork.externalLinkId(coreLinkId));
            if (net2PlanLink == null) {
                continue;
            }
            LinkSpectrumStats stats = buildLinkSpectrumStats(spectrum, coreLinkId, activeBackups);
            net2PlanLink.setAttribute(ATTR_SPECTRUM, stats.spectrumString);
            net2PlanLink.setAttribute(ATTR_CARRIED_SLOTS, String.valueOf(stats.carriedSlots));
            net2PlanLink.setAttribute(ATTR_RESERVED_SLOTS, String.valueOf(stats.reservedSlots));
            net2PlanLink.setAttribute(ATTR_ACTIVE_BACKUP_SLOTS, String.valueOf(stats.activeBackupSlots));
            net2PlanLink.setAttribute(ATTR_OCCUPIED_SLOTS, String.valueOf(stats.occupiedSlots));
            net2PlanLink.setAttribute(ATTR_FREE_SLOTS, String.valueOf(stats.freeSlots));
            net2PlanLink.setAttribute(ATTR_OCCUPANCY_RATIO, String.valueOf(stats.occupancyRatio));
            net2PlanLink.setAttribute(ATTR_SPECTRUM_OCCUPIED_PERCENT, formatDouble(stats.spectralLoadPercent));
            net2PlanLink.setAttribute(ATTR_SPECTRUM_CARRIED_PERCENT, formatDouble(stats.carriedRatio * 100.0D));
            net2PlanLink.setAttribute(ATTR_SPECTRUM_RESERVED_PERCENT, formatDouble(stats.reservedRatio * 100.0D));
            net2PlanLink.setAttribute(ATTR_SPECTRUM_ACTIVE_PERCENT, formatDouble(stats.activeRatio * 100.0D));
            net2PlanLink.setAttribute(ATTR_SPECTRAL_STATUS, stats.spectralStatus);
            net2PlanLink.setAttribute(ATTR_SPECTRAL_SUMMARY, stats.spectralSummary);
            net2PlanLink.setAttribute(ATTR_TRAFFIC_CARRIED_GBPS, formatDouble(safeGetCarriedTrafficGbps(net2PlanLink)));
            net2PlanLink.setAttribute(ATTR_TRAFFIC_OCCUPIED_GBPS, formatDouble(safeGetOccupiedCapacityGbps(net2PlanLink)));
            net2PlanLink.setAttribute(ATTR_TRAFFIC_LOAD_PERCENT, formatDouble(safeTrafficLoadPercent(net2PlanLink)));
            net2PlanLink.setAttribute(ATTR_SPECTRAL_LOAD_PERCENT, formatDouble(stats.spectralLoadPercent));
            snapshot.addLink(stats.occupancyRatio, stats.reservedSlots);
        }
        return snapshot;
    }

    private LinkSpectrumStats buildLinkSpectrumStats(
            SpectrumState spectrum,
            int coreLinkId,
            Set<String> activeBackupConnectionIds) {
        StringBuilder builder = new StringBuilder(spectrum.slotCount());
        int workingSlots = 0;
        int carriedSlots = 0;
        int reservedSlots = 0;
        int activeBackupSlots = 0;
        int occupiedSlots = 0;
        for (int slot = 0; slot < spectrum.slotCount(); slot++) {
            SlotState state = spectrum.slot(coreLinkId, slot);
            char symbol = symbolFor(state, activeBackupConnectionIds);
            builder.append(symbol);
            if (state.workingOwner() != null) {
                workingSlots++;
                carriedSlots++;
                occupiedSlots++;
            } else if (symbol == 'A') {
                carriedSlots++;
                activeBackupSlots++;
                occupiedSlots++;
            } else if (state.hasBackupOwners()) {
                reservedSlots++;
                occupiedSlots++;
            }
        }
        int totalSlots = spectrum.slotCount();
        int freeSlots = Math.max(0, totalSlots - occupiedSlots);
        double occupancyRatio = totalSlots == 0
                ? 0.0
                : (double) occupiedSlots / (double) totalSlots;
        double carriedRatio = totalSlots == 0 ? 0.0D : (double) carriedSlots / (double) totalSlots;
        double reservedRatio = totalSlots == 0 ? 0.0D : (double) reservedSlots / (double) totalSlots;
        double activeRatio = totalSlots == 0 ? 0.0D : (double) activeBackupSlots / (double) totalSlots;
        double spectralLoadPercent = occupancyRatio * 100.0D;
        String spectralStatus = buildSpectralStatus(occupancyRatio);
        String spectralSummary = buildSpectralSummary(
                occupiedSlots,
                totalSlots,
                workingSlots,
                reservedSlots,
                activeBackupSlots,
                freeSlots,
                spectralLoadPercent,
                spectralStatus);
        return new LinkSpectrumStats(
                builder.toString(),
                carriedSlots,
                reservedSlots,
                activeBackupSlots,
                occupiedSlots,
                freeSlots,
                occupancyRatio,
                carriedRatio,
                reservedRatio,
                activeRatio,
                spectralLoadPercent,
                spectralStatus,
                spectralSummary);
    }

    private char symbolFor(SlotState state, Set<String> activeBackupConnectionIds) {
        if (state.workingOwner() != null) {
            return 'W';
        }
        if (state.hasBackupOwners()) {
            for (rmsa.core.SlotAllocation allocation : state.backupOwners()) {
                if (activeBackupConnectionIds.contains(allocation.connection().id())) {
                    return 'A';
                }
            }
            return 'B';
        }
        return '.';
    }


    private String buildSpectralStatus(double occupancyRatio) {
        if (occupancyRatio >= 0.90D) return "CRITICAL";
        if (occupancyRatio >= 0.70D) return "HIGH";
        if (occupancyRatio >= 0.40D) return "MEDIUM";
        return "LOW";
    }

    private String buildSpectralSummary(
            int occupiedSlots,
            int totalSlots,
            int carriedSlots,
            int reservedSlots,
            int activeBackupSlots,
            int freeSlots,
            double spectralLoadPercent,
            String spectralStatus) {
        return occupiedSlots + "/" + totalSlots
                + " slots (" + formatDouble(spectralLoadPercent) + "%)"
                + " | W=" + carriedSlots
                + " | B=" + reservedSlots
                + " | A=" + activeBackupSlots
                + " | free=" + freeSlots
                + " | " + spectralStatus;
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0.00";
        return String.format(Locale.US, "%.2f", value);
    }

    private double safeGetCarriedTrafficGbps(Link link) {
        if (link == null) return 0.0D;
        try { return link.getCarriedTraffic(); }
        catch (Throwable ignored) { return 0.0D; }
    }

    private double safeGetOccupiedCapacityGbps(Link link) {
        if (link == null) return 0.0D;
        try { return link.getOccupiedCapacity(); }
        catch (Throwable ignored) { return 0.0D; }
    }

    private double safeTrafficLoadPercent(Link link) {
        if (link == null) return 0.0D;
        try {
            double capacity = link.getCapacity();
            if (capacity <= 0.0D) return 0.0D;
            return 100.0D * link.getCarriedTraffic() / capacity;
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }
    private Link findLinkById(NetPlan netPlan, long linkId) {
        for (Link link : netPlan.getLinks()) {
            if (link.getId() == linkId) {
                return link;
            }
        }
        return null;
    }

    private static final class LinkSpectrumStats {
        private final String spectrumString;
        private final int carriedSlots;
        private final int reservedSlots;
        private final int activeBackupSlots;
        private final int occupiedSlots;
        private final int freeSlots;
        private final double occupancyRatio;
        private final double carriedRatio;
        private final double reservedRatio;
        private final double activeRatio;
        private final double spectralLoadPercent;
        private final String spectralStatus;
        private final String spectralSummary;

        private LinkSpectrumStats(
                String spectrumString,
                int carriedSlots,
                int reservedSlots,
                int activeBackupSlots,
                int occupiedSlots,
                int freeSlots,
                double occupancyRatio,
                double carriedRatio,
                double reservedRatio,
                double activeRatio,
                double spectralLoadPercent,
                String spectralStatus,
                String spectralSummary) {
            this.spectrumString = spectrumString;
            this.carriedSlots = carriedSlots;
            this.reservedSlots = reservedSlots;
            this.activeBackupSlots = activeBackupSlots;
            this.occupiedSlots = occupiedSlots;
            this.freeSlots = freeSlots;
            this.occupancyRatio = occupancyRatio;
            this.carriedRatio = carriedRatio;
            this.reservedRatio = reservedRatio;
            this.activeRatio = activeRatio;
            this.spectralLoadPercent = spectralLoadPercent;
            this.spectralStatus = spectralStatus;
            this.spectralSummary = spectralSummary;
        }
    }

    public static final class SpectrumSnapshot {
        private int linkCount;
        private double occupancyRatioSum;
        private int reservedSlots;

        private void addLink(double occupancyRatio, int linkReservedSlots) {
            linkCount++;
            occupancyRatioSum += occupancyRatio;
            reservedSlots += linkReservedSlots;
        }

        public int linkCount() {
            return linkCount;
        }

        public double averageOccupancyRatio() {
            return linkCount == 0 ? 0.0 : occupancyRatioSum / (double) linkCount;
        }

        public int reservedSlots() {
            return reservedSlots;
        }
    }
}
