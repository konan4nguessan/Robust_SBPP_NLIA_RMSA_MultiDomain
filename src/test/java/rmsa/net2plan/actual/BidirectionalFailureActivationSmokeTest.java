package rmsa.net2plan.actual;

import java.util.Collections;
import java.util.HashMap;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.utils.Constants.RoutingType;

public final class BidirectionalFailureActivationSmokeTest {
    public static void main(String[] args) {
        NetPlan netPlan = new NetPlan();
        Node n0 = netPlan.addNode(0, 0, "n0", new HashMap<String, String>());
        Node n1 = netPlan.addNode(1, 0, "n1", new HashMap<String, String>());
        Node n2 = netPlan.addNode(2, 0, "n2", new HashMap<String, String>());
        Node n3 = netPlan.addNode(1, 1, "n3", new HashMap<String, String>());

        netPlan.addLink(n0, n1, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n1, n0, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n1, n2, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n2, n1, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n0, n3, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n3, n0, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n3, n2, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n2, n3, 1000, 100, 200000, new HashMap<String, String>());
        Demand demand = netPlan.addDemand(n0, n2, 200, RoutingType.SOURCE_ROUTING, new HashMap<String, String>());

        OnlineEvProc_RobustSbppRmsa processor = new OnlineEvProc_RobustSbppRmsa();
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("uiRefreshEveryNEvents", "1");
        processor.initialize(netPlan, parameters, new HashMap<String, String>(), new HashMap<String, String>());

        SimEvent.RouteAdd add = new SimEvent.RouteAdd(demand, Collections.<Link>emptyList(), 200.0, 1.0);
        processor.processEvent(netPlan, new SimEvent(0.0, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, add));
        Route workingRoute = add.routeAddedToFillByProcessor;
        assertTrue(workingRoute != null, "processor should create a working route");
        Route backupRoute = workingRoute.getBackupRoutes().iterator().next();
        assertEquals("STANDBY", backupRoute.getAttribute(Net2PlanDecisionApplier.ATTR_PROTECTION_STATE),
                "backup should start in standby");
        Link failedWorkingLink = workingRoute.getSeqLinks().get(0);
        Link failedWorkingReverseLink = findReverseLink(netPlan, failedWorkingLink);
        Link backupDisplayLink = backupRoute.getSeqLinks().get(0);
        assertTrue(backupDisplayLink.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("B"),
                "backup path should initially be reserved with B slots");

        SimEvent.NodesAndLinksChangeFailureState down = new SimEvent.NodesAndLinksChangeFailureState(
                null,
                null,
                null,
                Collections.singleton(failedWorkingLink));
        processor.processEvent(netPlan, new SimEvent(5.0, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, down));

        assertTrue(!failedWorkingLink.isUp(), "failed directed link should be down");
        assertTrue(failedWorkingReverseLink != null && !failedWorkingReverseLink.isUp(),
                "reverse directed link should also be down for bidirectional failure");
        assertEquals("FAILED_WORKING", workingRoute.getAttribute(Net2PlanDecisionApplier.ATTR_PROTECTION_STATE),
                "working route should expose failed state");
        assertEquals("BACKUP_ACTIVE", backupRoute.getAttribute(Net2PlanDecisionApplier.ATTR_PROTECTION_STATE),
                "backup route should expose active state");
        assertTrue(backupDisplayLink.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("A"),
                "backup path should display active backup slots with A");

        SimEvent.NodesAndLinksChangeFailureState up = new SimEvent.NodesAndLinksChangeFailureState(
                null,
                null,
                Collections.singleton(failedWorkingLink),
                null);
        processor.processEvent(netPlan, new SimEvent(10.0, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, up));

        assertTrue(failedWorkingLink.isUp(), "repaired directed link should be up");
        assertTrue(failedWorkingReverseLink.isUp(), "reverse directed link should also be up after repair");
        assertEquals("WORKING_ACTIVE", workingRoute.getAttribute(Net2PlanDecisionApplier.ATTR_PROTECTION_STATE),
                "working route should become active again after repair");
        assertEquals("STANDBY", backupRoute.getAttribute(Net2PlanDecisionApplier.ATTR_PROTECTION_STATE),
                "backup route should return to standby after repair");
        assertTrue(backupDisplayLink.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("B"),
                "backup path should return to reserved B slots after repair");

        System.out.println("Bidirectional failure activation smoke test passed");
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

    private static Link findReverseLink(NetPlan netPlan, Link link) {
        for (Link candidate : netPlan.getLinks()) {
            if (candidate.getId() != link.getId()
                    && candidate.getOriginNode().equals(link.getDestinationNode())
                    && candidate.getDestinationNode().equals(link.getOriginNode())) {
                return candidate;
            }
        }
        return null;
    }
}
