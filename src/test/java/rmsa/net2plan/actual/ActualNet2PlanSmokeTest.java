package rmsa.net2plan.actual;

import java.util.Arrays;
import java.util.HashMap;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.utils.Constants.RoutingType;

import rmsa.core.ExistingQoTGuard;
import rmsa.core.GreedyRobustBitloading;
import rmsa.core.NetworkNliEvaluator;
import rmsa.core.NliSnapshotStore;
import rmsa.core.PhysicalLayerModel;
import rmsa.core.PhysicalLayerParameters;
import rmsa.core.RecursiveExistingConnectionsQoTChecker;
import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.CandidatePathPair;
import rmsa.net2plan.RmsaCoreProvisioner;
import rmsa.net2plan.RmsaProvisioningDecision;

public final class ActualNet2PlanSmokeTest {
    public static void main(String[] args) {
        NetPlan netPlan = new NetPlan();
        Node n0 = netPlan.addNode(0, 0, "n0", new HashMap<String, String>());
        Node n1 = netPlan.addNode(1, 0, "n1", new HashMap<String, String>());
        Node n2 = netPlan.addNode(2, 0, "n2", new HashMap<String, String>());
        Node n3 = netPlan.addNode(1, 1, "n3", new HashMap<String, String>());

        Link l01 = netPlan.addLink(n0, n1, 1000, 100, 200000, new HashMap<String, String>());
        Link l12 = netPlan.addLink(n1, n2, 1000, 100, 200000, new HashMap<String, String>());
        Link l03 = netPlan.addLink(n0, n3, 1000, 100, 200000, new HashMap<String, String>());
        Link l32 = netPlan.addLink(n3, n2, 1000, 100, 200000, new HashMap<String, String>());
        Demand demand = netPlan.addDemand(n0, n2, 200, RoutingType.SOURCE_ROUTING, new HashMap<String, String>());

        ActualNet2PlanAdapterFactory factory = new ActualNet2PlanAdapterFactory(8);
        AdaptedNetwork adapted = factory.adaptNetPlan(netPlan);

        CandidatePathPair pair = new CandidatePathPair(
                factory.coreAdapter().adaptPath(adapted, new ActualNet2PlanPathView(Arrays.asList(l01, l12))),
                factory.coreAdapter().adaptPath(adapted, new ActualNet2PlanPathView(Arrays.asList(l03, l32))));

        PhysicalLayerModel physical = new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults());
        NetworkNliEvaluator nliEvaluator = new NetworkNliEvaluator(adapted.spectrum(), physical);
        NliSnapshotStore snapshot = NliSnapshotStore.build(adapted.spectrum(), nliEvaluator);
        ExistingQoTGuard guard = new RecursiveExistingConnectionsQoTChecker(adapted.spectrum(), nliEvaluator, snapshot);
        RmsaCoreProvisioner provisioner = new RmsaCoreProvisioner(
                adapted.spectrum(),
                new GreedyRobustBitloading(nliEvaluator),
                guard);

        RmsaProvisioningDecision decision = provisioner.chooseFirstFeasible(
                "c-net2plan-1",
                factory.coreAdapter().adaptDemand(new ActualNet2PlanDemandView(demand)),
                Arrays.asList(pair));
        assertTrue(decision.isFeasible(), "actual Net2Plan integration should find a feasible decision");
        assertTrue(decision.objectiveValue() > 0 && !Double.isInfinite(decision.objectiveValue()), "actual decision should carry a finite objective value");

        Net2PlanDecisionApplier.AppliedRoutes routes = new Net2PlanDecisionApplier()
                .apply(netPlan, demand, adapted, decision);
        assertTrue(routes.workingRoute().hasBackupRoutes(), "working route should reference its backup route");
        assertEquals("WORKING", routes.workingRoute().getAttribute(Net2PlanDecisionApplier.ATTR_ROLE), "working role attribute should be stored");
        assertEquals("BACKUP", routes.backupRoute().getAttribute(Net2PlanDecisionApplier.ATTR_ROLE), "backup role attribute should be stored");
        assertEquals(String.valueOf(routes.backupRoute().getId()),
                routes.workingRoute().getAttribute(Net2PlanDecisionApplier.ATTR_BACKUP_ROUTE_ID),
                "working route should expose backup route id");
        assertEquals(String.valueOf(routes.workingRoute().getId()),
                routes.backupRoute().getAttribute(Net2PlanDecisionApplier.ATTR_WORKING_ROUTE_ID),
                "backup route should expose working route id");

        System.out.println("Actual Net2Plan smoke test passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
