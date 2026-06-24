package rmsa.net2plan.multidomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.net2plan.interfaces.networkDesign.Link;

public final class LocalPathSegment {
    private final String domainId;
    private final List<Link> links;
    private final double lengthKm;
    private final double averageSpectralOccupancy;
    private final double maxSpectralOccupancy;
    private final boolean hasContiguousWorkingSlots;

    public LocalPathSegment(
            String domainId,
            List<Link> links,
            double lengthKm,
            double averageSpectralOccupancy,
            double maxSpectralOccupancy,
            boolean hasContiguousWorkingSlots) {
        if (domainId == null || domainId.trim().isEmpty()) {
            throw new IllegalArgumentException("domainId is required");
        }
        if (links == null || links.isEmpty()) {
            throw new IllegalArgumentException("A local segment must contain at least one link");
        }
        this.domainId = domainId.trim();
        this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
        this.lengthKm = lengthKm;
        this.averageSpectralOccupancy = averageSpectralOccupancy;
        this.maxSpectralOccupancy = maxSpectralOccupancy;
        this.hasContiguousWorkingSlots = hasContiguousWorkingSlots;
    }

    public String domainId() {
        return domainId;
    }

    public List<Link> links() {
        return links;
    }

    public double lengthKm() {
        return lengthKm;
    }

    public double averageSpectralOccupancy() {
        return averageSpectralOccupancy;
    }

    public double maxSpectralOccupancy() {
        return maxSpectralOccupancy;
    }

    public boolean hasContiguousWorkingSlots() {
        return hasContiguousWorkingSlots;
    }

    @Override
    public String toString() {
        return domainId + " links=" + links.size()
                + " lengthKm=" + lengthKm
                + " avgOcc=" + averageSpectralOccupancy
                + " maxOcc=" + maxSpectralOccupancy
                + " contiguous=" + hasContiguousWorkingSlots;
    }
}