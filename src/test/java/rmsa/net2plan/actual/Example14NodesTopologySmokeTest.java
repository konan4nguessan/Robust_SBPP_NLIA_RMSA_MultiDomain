package rmsa.net2plan.actual;

import java.io.File;
import java.util.HashMap;

import com.net2plan.interfaces.networkDesign.NetPlan;

import rmsa.net2plan.AdaptedNetwork;

public final class Example14NodesTopologySmokeTest {
    public static void main(String[] args) {
        File topologyFile = new File("data/networkTopologies/example14nodes_robust_sbpp_avec_demandes.n2p");
        assertTrue(topologyFile.isFile(), "example 14-node topology file should exist in the project");

        NetPlan netPlan = new NetPlan(topologyFile);
        assertEquals(14, netPlan.getNumberOfNodes(), "paper example topology should have 14 nodes");
        assertTrue(netPlan.getNumberOfLinks() > 0, "paper example topology should contain links");
        assertTrue(netPlan.getNumberOfDemands() > 0, "paper example topology should contain demands");

        ActualNet2PlanAdapterFactory adapterFactory = new ActualNet2PlanAdapterFactory(110);
        AdaptedNetwork adapted = adapterFactory.adaptNetPlan(netPlan);
        assertEquals(netPlan.getNumberOfLinks(), adapted.coreLinks().size(), "all Net2Plan links should be adapted");

        OnlineEvProc_RobustSbppRmsa processor = new OnlineEvProc_RobustSbppRmsa();
        processor.initialize(netPlan, new HashMap<String, String>(), new HashMap<String, String>(), new HashMap<String, String>());

        OnlineEvGen_RobustSbppTraffic generator = new OnlineEvGen_RobustSbppTraffic();
        assertTrue(generator.getDescription().contains("50 and 700"), "traffic generator should expose paper bitrate range");
        assertTrue(generator.getParameters().size() > 0, "traffic generator should expose Net2Plan parameters");

        System.out.println("Example 14-node topology smoke test passed");
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
