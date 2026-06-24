package rmsa.net2plan.actual;

import java.util.ArrayList;
import java.util.List;

import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;

import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.Net2PlanCoreAdapter;
import rmsa.net2plan.Net2PlanLinkView;

public final class ActualNet2PlanAdapterFactory {
    private final Net2PlanCoreAdapter coreAdapter;

    public ActualNet2PlanAdapterFactory(int slotCount) {
        this.coreAdapter = new Net2PlanCoreAdapter(slotCount);
    }

    public AdaptedNetwork adaptNetPlan(NetPlan netPlan) {
        if (netPlan == null) {
            throw new IllegalArgumentException("NetPlan is required");
        }
        List<Net2PlanLinkView> links = new ArrayList<Net2PlanLinkView>();
        for (Link link : netPlan.getLinks()) {
            links.add(new ActualNet2PlanLinkView(link));
        }
        return coreAdapter.adaptNetwork(links);
    }

    public Net2PlanCoreAdapter coreAdapter() {
        return coreAdapter;
    }
}
