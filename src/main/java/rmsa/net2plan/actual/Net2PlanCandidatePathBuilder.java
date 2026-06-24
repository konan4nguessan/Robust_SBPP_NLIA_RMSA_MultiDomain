package rmsa.net2plan.actual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;

import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.CandidatePathPair;
import rmsa.net2plan.Net2PlanCoreAdapter;

public final class Net2PlanCandidatePathBuilder {
    private final Net2PlanCoreAdapter adapter;
    private final int maxSearchDepth;

    public Net2PlanCandidatePathBuilder(Net2PlanCoreAdapter adapter, int maxSearchDepth) {
        if (adapter == null) {
            throw new IllegalArgumentException("Adapter is required");
        }
        this.adapter = adapter;
        this.maxSearchDepth = maxSearchDepth <= 0 ? 12 : maxSearchDepth;
    }

    public List<CandidatePathPair> buildCandidatePairs(
            NetPlan netPlan,
            Demand demand,
            AdaptedNetwork adaptedNetwork,
            int kWorking,
            int kBackupPerWorking) {
        List<List<Link>> workingPaths = kShortestSimplePaths(
                netPlan,
                demand.getIngressNode(),
                demand.getEgressNode(),
                Math.max(1, kWorking),
                Collections.<String>emptySet());
        List<CandidatePathPair> pairs = new ArrayList<CandidatePathPair>();
        for (List<Link> working : workingPaths) {
            Set<String> forbidden = physicalRiskKeySet(working);
            List<List<Link>> backups = kShortestSimplePaths(
                    netPlan,
                    demand.getIngressNode(),
                    demand.getEgressNode(),
                    Math.max(1, kBackupPerWorking),
                    forbidden);
            for (List<Link> backup : backups) {
                pairs.add(new CandidatePathPair(
                        adapter.adaptPath(adaptedNetwork, new ActualNet2PlanPathView(working)),
                        adapter.adaptPath(adaptedNetwork, new ActualNet2PlanPathView(backup))));
            }
        }
        return pairs;
    }

    private List<List<Link>> kShortestSimplePaths(
            NetPlan netPlan,
            Node source,
            Node destination,
            int k,
            Set<String> forbiddenPhysicalRisks) {
        List<List<Link>> paths = new ArrayList<List<Link>>();
        dfs(netPlan, source, destination, forbiddenPhysicalRisks, new HashSet<Long>(), new ArrayList<Link>(), paths);
        Collections.sort(paths, new Comparator<List<Link>>() {
            public int compare(List<Link> a, List<Link> b) {
                return Double.compare(pathLengthKm(a), pathLengthKm(b));
            }
        });
        if (paths.size() <= k) {
            return paths;
        }
        return new ArrayList<List<Link>>(paths.subList(0, k));
    }

    private void dfs(
            NetPlan netPlan,
            Node current,
            Node destination,
            Set<String> forbiddenPhysicalRisks,
            Set<Long> visitedNodeIds,
            List<Link> currentPath,
            List<List<Link>> paths) {
        if (current.equals(destination)) {
            paths.add(new ArrayList<Link>(currentPath));
            return;
        }
        if (currentPath.size() >= maxSearchDepth) {
            return;
        }
        visitedNodeIds.add(Long.valueOf(current.getId()));
        for (Link link : netPlan.getLinks()) {
            if (forbiddenPhysicalRisks.contains(physicalRiskKey(link))) {
                continue;
            }
            if (!link.getOriginNode().equals(current)) {
                continue;
            }
            Node next = link.getDestinationNode();
            if (visitedNodeIds.contains(Long.valueOf(next.getId()))) {
                continue;
            }
            currentPath.add(link);
            dfs(netPlan, next, destination, forbiddenPhysicalRisks, visitedNodeIds, currentPath, paths);
            currentPath.remove(currentPath.size() - 1);
        }
        visitedNodeIds.remove(Long.valueOf(current.getId()));
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
        double length = 0.0;
        for (Link link : links) {
            length += link.getLengthInKm();
        }
        return length;
    }
}
