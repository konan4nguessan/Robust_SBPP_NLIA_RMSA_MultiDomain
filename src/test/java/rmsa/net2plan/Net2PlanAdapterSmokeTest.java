package rmsa.net2plan;

import java.util.Arrays;
import java.util.List;

import rmsa.core.ExistingQoTGuard;
import rmsa.core.GreedyRobustBitloading;
import rmsa.core.NetworkNliEvaluator;
import rmsa.core.NliSnapshotStore;
import rmsa.core.PhysicalLayerModel;
import rmsa.core.PhysicalLayerParameters;
import rmsa.core.RecursiveExistingConnectionsQoTChecker;

public final class Net2PlanAdapterSmokeTest {
    public static void main(String[] args) {
        Net2PlanCoreAdapter adapter = new Net2PlanCoreAdapter(8);
        AdaptedNetwork network = adapter.adaptNetwork(Arrays.asList(
                new LinkView(10, 0, 1, 100),
                new LinkView(11, 1, 2, 100),
                new LinkView(12, 0, 3, 100),
                new LinkView(13, 3, 2, 100)));

        Net2PlanDemandView demand = new DemandView("d1", 0, 2, 200);
        CandidatePathPair pair = new CandidatePathPair(
                adapter.adaptPath(network, new PathView(10, 11)),
                adapter.adaptPath(network, new PathView(12, 13)));

        PhysicalLayerModel physical = new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults());
        NetworkNliEvaluator nliEvaluator = new NetworkNliEvaluator(network.spectrum(), physical);
        NliSnapshotStore snapshot = NliSnapshotStore.build(network.spectrum(), nliEvaluator);
        ExistingQoTGuard guard = new RecursiveExistingConnectionsQoTChecker(network.spectrum(), nliEvaluator, snapshot);
        RmsaCoreProvisioner provisioner = new RmsaCoreProvisioner(
                network.spectrum(),
                new GreedyRobustBitloading(nliEvaluator),
                guard);

        RmsaProvisioningDecision decision = provisioner.chooseFirstFeasible(
                "c1",
                adapter.adaptDemand(demand),
                Arrays.asList(pair));

        assertTrue(decision.isFeasible(), "adapter provisioner should find a feasible protected allocation");
        assertTrue(decision.objectiveValue() > 0 && !Double.isInfinite(decision.objectiveValue()), "decision should carry a finite objective value");
        assertTrue(decision.workingEvaluation().slotIndexes().size() > 0, "working allocation should contain slots");
        assertTrue(decision.backupEvaluation().slotIndexes().size() > 0, "backup allocation should contain slots");
        assertEquals(0, network.coreLinkId(10), "external link id should map to compact core id");
        assertEquals(13L, network.externalLinkId(3), "core link id should map back to external id");

        System.out.println("Net2Plan adapter smoke test passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static final class LinkView implements Net2PlanLinkView {
        private final long id;
        private final int origin;
        private final int destination;
        private final double lengthKm;

        private LinkView(long id, int origin, int destination, double lengthKm) {
            this.id = id;
            this.origin = origin;
            this.destination = destination;
            this.lengthKm = lengthKm;
        }

        public long id() {
            return id;
        }

        public int originNodeId() {
            return origin;
        }

        public int destinationNodeId() {
            return destination;
        }

        public double lengthKm() {
            return lengthKm;
        }
    }

    private static final class DemandView implements Net2PlanDemandView {
        private final String id;
        private final int source;
        private final int destination;
        private final int rateGbps;

        private DemandView(String id, int source, int destination, int rateGbps) {
            this.id = id;
            this.source = source;
            this.destination = destination;
            this.rateGbps = rateGbps;
        }

        public String id() {
            return id;
        }

        public int sourceNodeId() {
            return source;
        }

        public int destinationNodeId() {
            return destination;
        }

        public int requestedRateGbps() {
            return rateGbps;
        }
    }

    private static final class PathView implements Net2PlanPathView {
        private final List<Long> linkIds;

        private PathView(long firstLinkId, long secondLinkId) {
            this.linkIds = Arrays.asList(Long.valueOf(firstLinkId), Long.valueOf(secondLinkId));
        }

        public List<Long> linkIds() {
            return linkIds;
        }
    }
}
