package rmsa.net2plan.actual;

import java.util.HashMap;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.utils.Constants.RoutingType;

public final class DynamicProcessorSmokeTest {
    public static void main(String[] args) {
        NetPlan netPlan = new NetPlan();
        Node n0 = netPlan.addNode(0, 0, "n0", new HashMap<String, String>());
        Node n1 = netPlan.addNode(1, 0, "n1", new HashMap<String, String>());
        Node n2 = netPlan.addNode(2, 0, "n2", new HashMap<String, String>());
        Node n3 = netPlan.addNode(1, 1, "n3", new HashMap<String, String>());

        Link l01 = netPlan.addLink(n0, n1, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n1, n2, 1000, 100, 200000, new HashMap<String, String>());
        Link l03 = netPlan.addLink(n0, n3, 1000, 100, 200000, new HashMap<String, String>());
        netPlan.addLink(n3, n2, 1000, 100, 200000, new HashMap<String, String>());
        Demand demand = netPlan.addDemand(n0, n2, 200, RoutingType.SOURCE_ROUTING, new HashMap<String, String>());

        OnlineEvProc_RobustSbppRmsa processor = new OnlineEvProc_RobustSbppRmsa();
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("uiRefreshEveryNEvents", "1");
        processor.initialize(netPlan, parameters, new HashMap<String, String>(), new HashMap<String, String>());

        SimEvent.RouteAdd add = new SimEvent.RouteAdd(demand, java.util.Collections.<Link>emptyList(), 50.0, 50.0);
        processor.processEvent(netPlan, new SimEvent(0.0, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, add));
        assertTrue(add.routeAddedToFillByProcessor != null, "processor should create a working route");
        assertTrue(add.routeAddedToFillByProcessor.getCarriedTraffic() == 50.0,
                "processor should use the RouteAdd carried traffic as the connection bitrate");
        assertTrue(add.routeAddedToFillByProcessor.getOccupiedCapacity() == 50.0,
                "working route should keep the RouteAdd occupied link capacity");
        assertTrue(add.routeAddedToFillByProcessor.getBackupRoutes().iterator().next().getOccupiedCapacity() == 50.0,
                "backup route should reserve the same occupied link capacity as the protected working route");
        assertTrue(add.routeAddedToFillByProcessor.hasBackupRoutes(), "working route should have a backup route");
        assertTrue(add.routeAddedToFillByProcessor.getAttribute(Net2PlanDecisionApplier.ATTR_BACKUP_ROUTE_ID) != null,
                "working route should expose backup route id attribute");
        assertTrue(add.routeAddedToFillByProcessor.getBackupRoutes().iterator().next()
                        .getAttribute(Net2PlanDecisionApplier.ATTR_WORKING_ROUTE_ID) != null,
                "backup route should expose working route id attribute");
        assertEquals(2, netPlan.getRoutes().size(), "working plus backup routes should exist");
        assertTrue(l01.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("W"),
                "working link spectrum attribute should show occupied working slots");
        assertTrue(l03.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("B"),
                "backup link spectrum attribute should show reserved backup slots");
        assertTrue(Double.parseDouble(l01.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_OCCUPANCY_RATIO)) > 0.0,
                "occupied working link should expose positive occupancy ratio");

        SimEvent.RouteRemove remove = new SimEvent.RouteRemove(add.routeAddedToFillByProcessor);
        processor.processEvent(netPlan, new SimEvent(10.0, SimEvent.DestinationModule.EVENT_PROCESSOR, -1, remove));
        assertEquals(0, netPlan.getRoutes().size(), "route remove should clean working and backup routes");
        assertTrue(!l01.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("W"),
                "working link spectrum attribute should be refreshed after release");
        assertTrue(!l03.getAttribute(Net2PlanSpectrumSynchronizer.ATTR_SPECTRUM).contains("B"),
                "backup link spectrum attribute should be refreshed after release");

        StringBuilder report = new StringBuilder();
        String returnedReport = processor.finish(report, 10.0);
        assertTrue(returnedReport.contains("arrivals=1"), "finish report should include arrivals");
        assertTrue(returnedReport.contains("accepted=1"), "finish report should include accepted requests");
        assertTrue(returnedReport.contains("departures=1"), "finish report should include departures");
        assertTrue(returnedReport.contains("bbp=0.0"), "finish report should include BBP");
        assertTrue(report.toString().contains("fragmentation="), "finish should append fragmentation to the output buffer");

        System.out.println("Dynamic processor smoke test passed");
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
}
