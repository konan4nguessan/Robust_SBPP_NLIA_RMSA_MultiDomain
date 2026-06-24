package rmsa.net2plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rmsa.core.Link;
import rmsa.core.SpectrumState;

public final class AdaptedNetwork {
    private final List<Link> coreLinks;
    private final Map<Long, Integer> externalToCoreLinkId;
    private final Map<Integer, Long> coreToExternalLinkId;
    private final SpectrumState spectrum;

    public AdaptedNetwork(
            List<Link> coreLinks,
            Map<Long, Integer> externalToCoreLinkId,
            Map<Integer, Long> coreToExternalLinkId,
            SpectrumState spectrum) {
        this.coreLinks = Collections.unmodifiableList(coreLinks);
        this.externalToCoreLinkId = Collections.unmodifiableMap(new LinkedHashMap<Long, Integer>(externalToCoreLinkId));
        this.coreToExternalLinkId = Collections.unmodifiableMap(new LinkedHashMap<Integer, Long>(coreToExternalLinkId));
        this.spectrum = spectrum;
    }

    public List<Link> coreLinks() {
        return coreLinks;
    }

    public int coreLinkId(long externalLinkId) {
        Integer coreId = externalToCoreLinkId.get(Long.valueOf(externalLinkId));
        if (coreId == null) {
            throw new IllegalArgumentException("Unknown external link id: " + externalLinkId);
        }
        return coreId.intValue();
    }

    public long externalLinkId(int coreLinkId) {
        Long externalId = coreToExternalLinkId.get(Integer.valueOf(coreLinkId));
        if (externalId == null) {
            throw new IllegalArgumentException("Unknown core link id: " + coreLinkId);
        }
        return externalId.longValue();
    }

    public SpectrumState spectrum() {
        return spectrum;
    }
}
