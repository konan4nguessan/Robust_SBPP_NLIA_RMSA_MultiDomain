package rmsa.net2plan.multidomain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.net2plan.interfaces.networkDesign.Link;

import rmsa.core.SlotState;
import rmsa.core.SpectrumState;
import rmsa.net2plan.AdaptedNetwork;

public final class LocalSdnController {
    private final String domainId;

    public LocalSdnController(String domainId) {
        if (domainId == null || domainId.trim().isEmpty()) {
            throw new IllegalArgumentException("domainId is required");
        }
        this.domainId = domainId.trim();
    }

    public String domainId() {
        return domainId;
    }

    public boolean ownsNode(long nodeId, Map<Long, String> domainIdByNodeId) {
        String nodeDomain = domainIdByNodeId == null ? null : domainIdByNodeId.get(Long.valueOf(nodeId));
        return domainId.equals(nodeDomain);
    }

    public boolean ownsInternalLink(Link link, Map<Long, String> domainIdByNodeId) {
        if (link == null) {
            return false;
        }
        String originDomain = domainIdByNodeId.get(Long.valueOf(link.getOriginNode().getId()));
        String destinationDomain = domainIdByNodeId.get(Long.valueOf(link.getDestinationNode().getId()));
        return domainId.equals(originDomain) && domainId.equals(destinationDomain);
    }

    public List<LocalPathSegment> localSegments(
            List<Link> path,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots) {
        List<LocalPathSegment> segments = new ArrayList<LocalPathSegment>();
        if (path == null || path.isEmpty()) {
            return segments;
        }
        List<Link> current = new ArrayList<Link>();
        for (Link link : path) {
            String ownerDomain = domainOf(link.getOriginNode().getId(), domainIdByNodeId);
            if (domainId.equals(ownerDomain)) {
                current.add(link);
            } else if (!current.isEmpty()) {
                segments.add(buildSegment(current, adaptedNetwork, minimumSlots));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            segments.add(buildSegment(current, adaptedNetwork, minimumSlots));
        }
        return segments;
    }

    public boolean acceptsWorkingPath(
            List<Link> path,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy) {
        List<LocalPathSegment> segments = localSegments(path, domainIdByNodeId, adaptedNetwork, minimumSlots);
        for (LocalPathSegment segment : segments) {
            if (!segment.hasContiguousWorkingSlots()) {
                return false;
            }
            if (maxAllowedLinkOccupancy > 0.0D && segment.maxSpectralOccupancy() > maxAllowedLinkOccupancy) {
                return false;
            }
        }
        return true;
    }

    public boolean acceptsBackupPath(
            List<Link> path,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy) {
        List<LocalPathSegment> segments = localSegments(path, domainIdByNodeId, adaptedNetwork, minimumSlots);
        for (LocalPathSegment segment : segments) {
            if (maxAllowedLinkOccupancy > 0.0D && segment.maxSpectralOccupancy() > maxAllowedLinkOccupancy) {
                return false;
            }
        }
        return true;
    }

    private LocalPathSegment buildSegment(List<Link> links, AdaptedNetwork adaptedNetwork, int minimumSlots) {
        double lengthKm = 0.0D;
        double occupancySum = 0.0D;
        double maxOccupancy = 0.0D;
        for (Link link : links) {
            lengthKm += link.getLengthInKm();
            double occupancy = linkOccupancy(link, adaptedNetwork);
            occupancySum += occupancy;
            maxOccupancy = Math.max(maxOccupancy, occupancy);
        }
        double averageOccupancy = links.isEmpty() ? 0.0D : occupancySum / links.size();
        return new LocalPathSegment(
                domainId,
                links,
                lengthKm,
                averageOccupancy,
                maxOccupancy,
                hasContiguousWorkingSlots(links, adaptedNetwork, Math.max(1, minimumSlots)));
    }

    private double linkOccupancy(Link link, AdaptedNetwork adaptedNetwork) {
        SpectrumState spectrum = adaptedNetwork.spectrum();
        int coreLinkId = adaptedNetwork.coreLinkId(link.getId());
        int occupied = 0;
        for (int slot = 0; slot < spectrum.slotCount(); slot++) {
            SlotState state = spectrum.slot(coreLinkId, slot);
            if (!state.isFreeForWorking()) {
                occupied++;
            }
        }
        return ((double) occupied) / spectrum.slotCount();
    }

    private boolean hasContiguousWorkingSlots(List<Link> links, AdaptedNetwork adaptedNetwork, int minimumSlots) {
        SpectrumState spectrum = adaptedNetwork.spectrum();
        if (minimumSlots <= 0 || minimumSlots > spectrum.slotCount()) {
            return false;
        }
        for (int start = 0; start <= spectrum.slotCount() - minimumSlots; start++) {
            boolean usable = true;
            for (Link link : links) {
                int coreLinkId = adaptedNetwork.coreLinkId(link.getId());
                for (int offset = 0; offset < minimumSlots; offset++) {
                    if (!spectrum.slot(coreLinkId, start + offset).isFreeForWorking()) {
                        usable = false;
                        break;
                    }
                }
                if (!usable) {
                    break;
                }
            }
            if (usable) {
                return true;
            }
        }
        return false;
    }

    private String domainOf(long nodeId, Map<Long, String> domainIdByNodeId) {
        String value = domainIdByNodeId == null ? null : domainIdByNodeId.get(Long.valueOf(nodeId));
        return value == null || value.trim().isEmpty() ? "D0" : value.trim();
    }
}