package rmsa.net2plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rmsa.core.ConnectionRequest;
import rmsa.core.Link;
import rmsa.core.NetworkPath;
import rmsa.core.SpectrumState;

public final class Net2PlanCoreAdapter {
    private final int slotCount;

    public Net2PlanCoreAdapter(int slotCount) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("Slot count must be positive");
        }
        this.slotCount = slotCount;
    }

    public AdaptedNetwork adaptNetwork(List<? extends Net2PlanLinkView> links) {
        if (links == null || links.isEmpty()) {
            throw new IllegalArgumentException("At least one link is required");
        }

        List<Link> coreLinks = new ArrayList<Link>();
        Map<Long, Integer> externalToCore = new LinkedHashMap<Long, Integer>();
        Map<Integer, Long> coreToExternal = new LinkedHashMap<Integer, Long>();

        int coreId = 0;
        for (Net2PlanLinkView link : links) {
            if (externalToCore.containsKey(Long.valueOf(link.id()))) {
                throw new IllegalArgumentException("Duplicate external link id: " + link.id());
            }
            externalToCore.put(Long.valueOf(link.id()), Integer.valueOf(coreId));
            coreToExternal.put(Integer.valueOf(coreId), Long.valueOf(link.id()));
            coreLinks.add(new Link(coreId, link.originNodeId(), link.destinationNodeId(), link.lengthKm()));
            coreId++;
        }

        return new AdaptedNetwork(
                coreLinks,
                externalToCore,
                coreToExternal,
                new SpectrumState(coreLinks, slotCount));
    }

    public ConnectionRequest adaptDemand(Net2PlanDemandView demand) {
        if (demand == null) {
            throw new IllegalArgumentException("Demand is required");
        }
        return new ConnectionRequest(
                demand.id(),
                demand.sourceNodeId(),
                demand.destinationNodeId(),
                demand.requestedRateGbps());
    }

    public NetworkPath adaptPath(AdaptedNetwork network, Net2PlanPathView pathView) {
        if (network == null || pathView == null) {
            throw new IllegalArgumentException("Network and path are required");
        }
        List<Link> links = new ArrayList<Link>();
        for (Long externalId : pathView.linkIds()) {
            int coreId = network.coreLinkId(externalId.longValue());
            links.add(network.coreLinks().get(coreId));
        }
        return new NetworkPath(links);
    }
}
