package rmsa.net2plan.actual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;

import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.CandidatePathPair;
import rmsa.net2plan.Net2PlanCoreAdapter;
import rmsa.net2plan.multidomain.DomainPath;
import rmsa.net2plan.multidomain.LocalSdnController;
import rmsa.net2plan.multidomain.DomainPathModule;

public final class GlobalCandidateAssemblyModule {
    private static final double HIGHEST_SLOT_RATE_GBPS = 200.0D;

    private final Net2PlanCoreAdapter adapter;
    private final Net2PlanCandidatePathBuilder fallbackBuilder;
    private final DomainPathModule domainPathModule;
    private final int maxSearchDepth;
    private final double localMaxLinkOccupancy;

    private int lastLocalPathsEvaluated;
    private int lastLocalPathsRejected;
    private int lastSbppRejectedPairs;
    private int lastAssembledPairs;
    private boolean lastUsedFallback;

    public GlobalCandidateAssemblyModule(Net2PlanCoreAdapter adapter, int maxSearchDepth) {
        this(adapter, maxSearchDepth, 0.98D);
    }

    public GlobalCandidateAssemblyModule(Net2PlanCoreAdapter adapter, int maxSearchDepth, double localMaxLinkOccupancy) {
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter is required");
        }
        this.adapter = adapter;
        this.maxSearchDepth = maxSearchDepth <= 0 ? 12 : maxSearchDepth;
        this.localMaxLinkOccupancy = localMaxLinkOccupancy;
        this.fallbackBuilder = new Net2PlanCandidatePathBuilder(adapter, maxSearchDepth);
        this.domainPathModule = new DomainPathModule();
    }

    public List<CandidatePathPair> buildCandidatePairs(
            NetPlan netPlan,
            Demand demand,
            AdaptedNetwork adaptedNetwork,
            int kWorking,
            int kBackupPerWorking) {
        return buildCandidatePairs(netPlan, demand, adaptedNetwork, kWorking, kBackupPerWorking, 0.0D);
    }

    public List<CandidatePathPair> buildCandidatePairs(
            NetPlan netPlan,
            Demand demand,
            AdaptedNetwork adaptedNetwork,
            int kWorking,
            int kBackupPerWorking,
            double requestedGbps) {
        resetLastAssemblyStats();
        Map<Long, String> domainIdByNodeId = domainPathModule.buildDomainIdByNodeId(netPlan);
        Map<String, LocalSdnController> controllers = domainPathModule.buildLocalControllers(domainIdByNodeId);
        if (controllers.size() <= 1 || !domainPathModule.isInterDomainRequest(demand, domainIdByNodeId)) {
            lastUsedFallback = true;
            return fallbackBuilder.buildCandidatePairs(netPlan, demand, adaptedNetwork, kWorking, kBackupPerWorking);
        }

        int minimumSlots = optimisticMinimumSlots(requestedGbps);
        List<CandidatePathPair> pairs = new ArrayList<CandidatePathPair>();
        Map<String, Boolean> seenPairs = new HashMap<String, Boolean>();
        List<DomainPath> workingDomainPaths = domainPathModule.candidateDomainPaths(
                netPlan,
                demand,
                domainIdByNodeId,
                Math.max(1, kWorking));
        for (DomainPath workingDomainPath : workingDomainPaths) {
            List<List<Link>> rawWorkingPaths = kShortestDomainConstrainedPaths(
                    netPlan,
                    demand.getIngressNode(),
                    demand.getEgressNode(),
                    workingDomainPath,
                    domainIdByNodeId,
                    Math.max(1, kWorking),
                    Collections.<String>emptySet());
            lastLocalPathsEvaluated += rawWorkingPaths.size();
            List<List<Link>> workingPaths = domainPathModule.filterAndRankWorkingPaths(
                    rawWorkingPaths,
                    controllers,
                    domainIdByNodeId,
                    adaptedNetwork,
                    minimumSlots,
                    localMaxLinkOccupancy);
            lastLocalPathsRejected += rawWorkingPaths.size() - workingPaths.size();

            for (List<Link> working : workingPaths) {
                Set<String> forbiddenRisks = physicalRiskKeySet(working);
                List<DomainPath> backupDomainPaths = domainPathModule.backupDomainPaths(
                        netPlan,
                        demand,
                        domainIdByNodeId,
                        workingDomainPath,
                        Math.max(1, kBackupPerWorking));
                for (DomainPath backupDomainPath : backupDomainPaths) {
                    List<List<Link>> rawBackups = kShortestDomainConstrainedPaths(
                            netPlan,
                            demand.getIngressNode(),
                            demand.getEgressNode(),
                            backupDomainPath,
                            domainIdByNodeId,
                            Math.max(1, kBackupPerWorking),
                            forbiddenRisks);
                    lastLocalPathsEvaluated += rawBackups.size();
                    List<List<Link>> backups = domainPathModule.filterAndRankBackupPaths(
                            rawBackups,
                            controllers,
                            domainIdByNodeId,
                            adaptedNetwork,
                            minimumSlots,
                            localMaxLinkOccupancy);
                    lastLocalPathsRejected += rawBackups.size() - backups.size();
                    for (List<Link> backup : backups) {
                        if (!domainPathModule.verifiesInterDomainSbpp(working, backup)) {
                            lastSbppRejectedPairs++;
                            continue;
                        }
                        String key = pathKey(working) + "|" + pathKey(backup);
                        if (seenPairs.containsKey(key)) {
                            continue;
                        }
                        seenPairs.put(key, Boolean.TRUE);
                        pairs.add(new CandidatePathPair(
                                adapter.adaptPath(adaptedNetwork, new ActualNet2PlanPathView(working)),
                                adapter.adaptPath(adaptedNetwork, new ActualNet2PlanPathView(backup))));
                    }
                }
            }
        }

        lastAssembledPairs = pairs.size();
        if (pairs.isEmpty()) {
            lastUsedFallback = true;
            return fallbackBuilder.buildCandidatePairs(netPlan, demand, adaptedNetwork, kWorking, kBackupPerWorking);
        }
        return pairs;
    }

    public String lastAssemblySummary() {
        return "globalFallback=" + lastUsedFallback
                + " localPathsEvaluated=" + lastLocalPathsEvaluated
                + " localPathsRejected=" + lastLocalPathsRejected
                + " sbppRejectedPairs=" + lastSbppRejectedPairs
                + " brokerAssembledPairs=" + lastAssembledPairs;
    }

    public int lastLocalPathsEvaluated() {
        return lastLocalPathsEvaluated;
    }

    public int lastLocalPathsRejected() {
        return lastLocalPathsRejected;
    }

    public int lastSbppRejectedPairs() {
        return lastSbppRejectedPairs;
    }

    public int lastAssembledPairs() {
        return lastAssembledPairs;
    }

    public boolean lastUsedFallback() {
        return lastUsedFallback;
    }
    private void resetLastAssemblyStats() {
        lastLocalPathsEvaluated = 0;
        lastLocalPathsRejected = 0;
        lastSbppRejectedPairs = 0;
        lastAssembledPairs = 0;
        lastUsedFallback = false;
    }

    private int optimisticMinimumSlots(double requestedGbps) {
        if (requestedGbps <= 0.0D) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(requestedGbps / HIGHEST_SLOT_RATE_GBPS));
    }

    private List<List<Link>> kShortestDomainConstrainedPaths(
            NetPlan netPlan,
            Node source,
            Node destination,
            DomainPath domainPath,
            Map<Long, String> domainIdByNodeId,
            int k,
            Set<String> forbiddenPhysicalRisks) {
        List<List<Link>> paths = new ArrayList<List<Link>>();
        dfs(
                netPlan,
                source,
                destination,
                domainPath,
                domainIdByNodeId,
                0,
                forbiddenPhysicalRisks,
                new HashSet<Long>(),
                new ArrayList<Link>(),
                paths);
        Collections.sort(paths, new Comparator<List<Link>>() {
            public int compare(List<Link> a, List<Link> b) {
                return Double.compare(pathLengthKm(a), pathLengthKm(b));
            }
        });
        int limit = Math.max(1, k);
        if (paths.size() <= limit) {
            return paths;
        }
        return new ArrayList<List<Link>>(paths.subList(0, limit));
    }

    private void dfs(
            NetPlan netPlan,
            Node current,
            Node destination,
            DomainPath domainPath,
            Map<Long, String> domainIdByNodeId,
            int domainIndex,
            Set<String> forbiddenPhysicalRisks,
            Set<Long> visitedNodeIds,
            List<Link> currentPath,
            List<List<Link>> paths) {
        if (current.equals(destination)) {
            if (domainIndex == domainPath.domainIds().size() - 1) {
                paths.add(new ArrayList<Link>(currentPath));
            }
            return;
        }
        if (currentPath.size() >= maxSearchDepth) {
            return;
        }
        visitedNodeIds.add(Long.valueOf(current.getId()));
        for (Link link : netPlan.getLinks()) {
            if (!link.getOriginNode().equals(current)) {
                continue;
            }
            if (forbiddenPhysicalRisks.contains(physicalRiskKey(link))) {
                continue;
            }
            int nextDomainIndex = nextDomainIndex(link, domainPath, domainIdByNodeId, domainIndex);
            if (nextDomainIndex < 0) {
                continue;
            }
            Node next = link.getDestinationNode();
            if (visitedNodeIds.contains(Long.valueOf(next.getId()))) {
                continue;
            }
            currentPath.add(link);
            dfs(
                    netPlan,
                    next,
                    destination,
                    domainPath,
                    domainIdByNodeId,
                    nextDomainIndex,
                    forbiddenPhysicalRisks,
                    visitedNodeIds,
                    currentPath,
                    paths);
            currentPath.remove(currentPath.size() - 1);
        }
        visitedNodeIds.remove(Long.valueOf(current.getId()));
    }

    private int nextDomainIndex(
            Link link,
            DomainPath domainPath,
            Map<Long, String> domainIdByNodeId,
            int currentDomainIndex) {
        List<String> domains = domainPath.domainIds();
        String expectedOriginDomain = domains.get(currentDomainIndex);
        String originDomain = domainOf(link.getOriginNode(), domainIdByNodeId);
        String destinationDomain = domainOf(link.getDestinationNode(), domainIdByNodeId);
        if (!expectedOriginDomain.equals(originDomain)) {
            return -1;
        }
        if (destinationDomain.equals(expectedOriginDomain)) {
            return currentDomainIndex;
        }
        int nextIndex = currentDomainIndex + 1;
        if (nextIndex < domains.size() && destinationDomain.equals(domains.get(nextIndex))) {
            return nextIndex;
        }
        return -1;
    }

    private String domainOf(Node node, Map<Long, String> domainIdByNodeId) {
        String domainId = domainIdByNodeId.get(Long.valueOf(node.getId()));
        return domainId == null || domainId.trim().isEmpty() ? "D0" : domainId.trim();
    }

    private Set<String> physicalRiskKeySet(List<Link> links) {
        Set<String> risks = new HashSet<String>();
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
        double length = 0.0D;
        for (Link link : links) {
            length += link.getLengthInKm();
        }
        return length;
    }

    private String pathKey(List<Link> links) {
        StringBuilder builder = new StringBuilder();
        for (Link link : links) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(link.getId());
        }
        return builder.toString();
    }
}
