package rmsa.net2plan.multidomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;

import rmsa.net2plan.AdaptedNetwork;

public final class DomainPathModule {
    private static final String DEFAULT_DOMAIN_ID = "D0";
    private static final String DOMAIN_ID_ATTRIBUTE = "domainId";
    private static final String DOMAIN_ATTRIBUTE = "domain";
    private static final String DOMAIN_NAME_ATTRIBUTE = "domainName";

    public Map<Long, String> buildDomainIdByNodeId(NetPlan netPlan) {
        Map<Long, String> result = new LinkedHashMap<Long, String>();
        for (Node node : netPlan.getNodes()) {
            result.put(Long.valueOf(node.getId()), readNodeDomainId(node));
        }
        return result;
    }

    public Map<String, LocalSdnController> buildLocalControllers(Map<Long, String> domainIdByNodeId) {
        Set<String> domains = new LinkedHashSet<String>();
        if (domainIdByNodeId != null) {
            domains.addAll(domainIdByNodeId.values());
        }
        Map<String, LocalSdnController> result = new LinkedHashMap<String, LocalSdnController>();
        for (String domainId : domains) {
            result.put(domainId, new LocalSdnController(domainId));
        }
        if (result.isEmpty()) {
            result.put(DEFAULT_DOMAIN_ID, new LocalSdnController(DEFAULT_DOMAIN_ID));
        }
        return result;
    }

    public boolean isInterDomainRequest(Demand demand, Map<Long, String> domainIdByNodeId) {
        return !sourceDomainId(demand, domainIdByNodeId).equals(destinationDomainId(demand, domainIdByNodeId));
    }

    public String sourceDomainId(Demand demand, Map<Long, String> domainIdByNodeId) {
        return domainOf(demand.getIngressNode(), domainIdByNodeId);
    }

    public String destinationDomainId(Demand demand, Map<Long, String> domainIdByNodeId) {
        return domainOf(demand.getEgressNode(), domainIdByNodeId);
    }

    public List<DomainPath> candidateDomainPaths(
            NetPlan netPlan,
            Demand demand,
            Map<Long, String> domainIdByNodeId,
            int k) {
        String source = sourceDomainId(demand, domainIdByNodeId);
        String destination = destinationDomainId(demand, domainIdByNodeId);
        if (source.equals(destination)) {
            return Collections.singletonList(new DomainPath(Collections.singletonList(source)));
        }
        return kShortestDomainPaths(buildDomainAdjacency(netPlan, domainIdByNodeId), source, destination, k);
    }

    public List<DomainPath> backupDomainPaths(
            NetPlan netPlan,
            Demand demand,
            Map<Long, String> domainIdByNodeId,
            DomainPath workingDomainPath,
            int k) {
        String source = sourceDomainId(demand, domainIdByNodeId);
        String destination = destinationDomainId(demand, domainIdByNodeId);
        Map<String, Set<String>> adjacency = buildDomainAdjacency(netPlan, domainIdByNodeId);
        Set<String> forbiddenTransitions = workingDomainPath == null
                ? Collections.<String>emptySet()
                : workingDomainPath.transitionKeys();
        List<DomainPath> disjoint = kShortestDomainPaths(adjacency, source, destination, k, forbiddenTransitions);
        if (!disjoint.isEmpty()) {
            return disjoint;
        }
        return kShortestDomainPaths(adjacency, source, destination, k);
    }

    private List<DomainPath> kShortestDomainPaths(Map<String, Set<String>> adjacency, String source, String destination, int k) {
        return kShortestDomainPaths(adjacency, source, destination, k, Collections.<String>emptySet());
    }

    private List<DomainPath> kShortestDomainPaths(
            Map<String, Set<String>> adjacency,
            String source,
            String destination,
            int k,
            Set<String> forbiddenTransitions) {
        List<DomainPath> paths = new ArrayList<DomainPath>();
        dfsDomains(adjacency, source, destination, forbiddenTransitions, new HashSet<String>(), new ArrayList<String>(), paths);
        Collections.sort(paths, new Comparator<DomainPath>() {
            public int compare(DomainPath a, DomainPath b) {
                return Integer.compare(a.domainIds().size(), b.domainIds().size());
            }
        });
        int limit = Math.max(1, k);
        if (paths.size() <= limit) {
            return paths;
        }
        return new ArrayList<DomainPath>(paths.subList(0, limit));
    }

    private void dfsDomains(
            Map<String, Set<String>> adjacency,
            String current,
            String destination,
            Set<String> forbiddenTransitions,
            Set<String> visited,
            List<String> path,
            List<DomainPath> result) {
        path.add(current);
        if (current.equals(destination)) {
            result.add(new DomainPath(path));
            path.remove(path.size() - 1);
            return;
        }
        visited.add(current);
        Set<String> nextDomains = adjacency.get(current);
        if (nextDomains != null) {
            for (String next : nextDomains) {
                if (visited.contains(next)) {
                    continue;
                }
                if (forbiddenTransitions.contains(DomainPath.transitionKey(current, next))) {
                    continue;
                }
                dfsDomains(adjacency, next, destination, forbiddenTransitions, visited, path, result);
            }
        }
        visited.remove(current);
        path.remove(path.size() - 1);
    }
    public List<List<Link>> filterAndRankWorkingPaths(
            List<List<Link>> paths,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy) {
        return filterAndRankPaths(paths, controllers, domainIdByNodeId, adaptedNetwork, minimumSlots, maxAllowedLinkOccupancy, true);
    }

    public List<List<Link>> filterAndRankBackupPaths(
            List<List<Link>> paths,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy) {
        return filterAndRankPaths(paths, controllers, domainIdByNodeId, adaptedNetwork, minimumSlots, maxAllowedLinkOccupancy, false);
    }

    public boolean verifiesInterDomainSbpp(List<Link> workingPath, List<Link> backupPath) {
        Set<String> workingRisks = physicalRiskKeys(workingPath);
        for (Link link : backupPath) {
            if (workingRisks.contains(physicalRiskKey(link))) {
                return false;
            }
        }
        return true;
    }

    public String localSegmentSummary(
            List<Link> path,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots) {
        StringBuilder builder = new StringBuilder();
        for (LocalSdnController controller : controllers.values()) {
            List<LocalPathSegment> segments = controller.localSegments(path, domainIdByNodeId, adaptedNetwork, minimumSlots);
            for (LocalPathSegment segment : segments) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(segment.toString());
            }
        }
        return builder.toString();
    }

    private List<List<Link>> filterAndRankPaths(
            List<List<Link>> paths,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy,
            boolean workingPath) {
        List<List<Link>> accepted = new ArrayList<List<Link>>();
        if (paths == null || paths.isEmpty()) {
            return accepted;
        }
        for (List<Link> path : paths) {
            if (acceptedByLocalControllers(path, controllers, domainIdByNodeId, adaptedNetwork, minimumSlots, maxAllowedLinkOccupancy, workingPath)) {
                accepted.add(path);
            }
        }
        Collections.sort(accepted, new Comparator<List<Link>>() {
            public int compare(List<Link> a, List<Link> b) {
                int occupancy = Double.compare(localOccupancyScore(a, controllers, domainIdByNodeId, adaptedNetwork, minimumSlots),
                        localOccupancyScore(b, controllers, domainIdByNodeId, adaptedNetwork, minimumSlots));
                if (occupancy != 0) {
                    return occupancy;
                }
                return Double.compare(pathLengthKm(a), pathLengthKm(b));
            }
        });
        return accepted;
    }

    private boolean acceptedByLocalControllers(
            List<Link> path,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots,
            double maxAllowedLinkOccupancy,
            boolean workingPath) {
        for (LocalSdnController controller : controllers.values()) {
            boolean accepted = workingPath
                    ? controller.acceptsWorkingPath(path, domainIdByNodeId, adaptedNetwork, minimumSlots, maxAllowedLinkOccupancy)
                    : controller.acceptsBackupPath(path, domainIdByNodeId, adaptedNetwork, minimumSlots, maxAllowedLinkOccupancy);
            if (!accepted) {
                return false;
            }
        }
        return true;
    }

    private double localOccupancyScore(
            List<Link> path,
            Map<String, LocalSdnController> controllers,
            Map<Long, String> domainIdByNodeId,
            AdaptedNetwork adaptedNetwork,
            int minimumSlots) {
        double score = 0.0D;
        int segments = 0;
        for (LocalSdnController controller : controllers.values()) {
            for (LocalPathSegment segment : controller.localSegments(path, domainIdByNodeId, adaptedNetwork, minimumSlots)) {
                score += segment.averageSpectralOccupancy() + segment.maxSpectralOccupancy();
                segments++;
            }
        }
        return segments == 0 ? 0.0D : score / segments;
    }

    private Set<String> physicalRiskKeys(List<Link> links) {
        Set<String> risks = new LinkedHashSet<String>();
        for (Link link : links) {
            risks.add(physicalRiskKey(link));
        }
        return risks;
    }

    private String physicalRiskKey(Link link) {
        long firstNode = Math.min(link.getOriginNode().getId(), link.getDestinationNode().getId());
        long secondNode = Math.max(link.getOriginNode().getId(), link.getDestinationNode().getId());
        return firstNode + "-" + secondNode;
    }

    private double pathLengthKm(List<Link> links) {
        double lengthKm = 0.0D;
        for (Link link : links) {
            lengthKm += link.getLengthInKm();
        }
        return lengthKm;
    }

    private Map<String, Set<String>> buildDomainAdjacency(NetPlan netPlan, Map<Long, String> domainIdByNodeId) {
        Map<String, Set<String>> adjacency = new LinkedHashMap<String, Set<String>>();
        for (String domainId : new LinkedHashSet<String>(domainIdByNodeId.values())) {
            ensureSet(adjacency, domainId);
        }
        for (Link link : netPlan.getLinks()) {
            String originDomain = domainOf(link.getOriginNode(), domainIdByNodeId);
            String destinationDomain = domainOf(link.getDestinationNode(), domainIdByNodeId);
            if (!originDomain.equals(destinationDomain)) {
                ensureSet(adjacency, originDomain).add(destinationDomain);
            }
        }
        return adjacency;
    }

    private Set<String> ensureSet(Map<String, Set<String>> values, String key) {
        Set<String> set = values.get(key);
        if (set == null) {
            set = new LinkedHashSet<String>();
            values.put(key, set);
        }
        return set;
    }

    private String domainOf(Node node, Map<Long, String> domainIdByNodeId) {
        String domainId = domainIdByNodeId == null ? null : domainIdByNodeId.get(Long.valueOf(node.getId()));
        return domainId == null || domainId.trim().isEmpty() ? DEFAULT_DOMAIN_ID : domainId.trim();
    }

    private String readNodeDomainId(Node node) {
        String domainId = firstNonEmpty(
                node.getAttribute(DOMAIN_ID_ATTRIBUTE),
                node.getAttribute(DOMAIN_ATTRIBUTE),
                node.getAttribute(DOMAIN_NAME_ATTRIBUTE));
        if (domainId == null) {
            try {
                node.setAttribute(DOMAIN_ID_ATTRIBUTE, DEFAULT_DOMAIN_ID);
            } catch (RuntimeException e) {
                // Net2Plan attributes are best-effort here.
            }
            return DEFAULT_DOMAIN_ID;
        }
        return domainId;
    }

    private String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        if (third != null && !third.trim().isEmpty()) {
            return third.trim();
        }
        return null;
    }
}
