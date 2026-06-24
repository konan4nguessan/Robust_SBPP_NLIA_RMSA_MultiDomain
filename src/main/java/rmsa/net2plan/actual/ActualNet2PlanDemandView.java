package rmsa.net2plan.actual;

import com.net2plan.interfaces.networkDesign.Demand;

import rmsa.net2plan.Net2PlanDemandView;

public final class ActualNet2PlanDemandView implements Net2PlanDemandView {
    private final Demand demand;
    private final Integer requestedRateGbps;

    public ActualNet2PlanDemandView(Demand demand) {
        this(demand, null);
    }

    public ActualNet2PlanDemandView(Demand demand, Double requestedRateGbps) {
        if (demand == null) {
            throw new IllegalArgumentException("Net2Plan demand is required");
        }
        this.demand = demand;
        this.requestedRateGbps = requestedRateGbps == null
                ? null
                : Integer.valueOf((int) Math.ceil(requestedRateGbps.doubleValue()));
    }

    @Override
    public String id() {
        return Long.toString(demand.getId());
    }

    @Override
    public int sourceNodeId() {
        return (int) demand.getIngressNode().getId();
    }

    @Override
    public int destinationNodeId() {
        return (int) demand.getEgressNode().getId();
    }

    @Override
    public int requestedRateGbps() {
        if (requestedRateGbps != null) {
            return requestedRateGbps.intValue();
        }
        return (int) Math.ceil(demand.getOfferedTraffic());
    }

    public Demand demand() {
        return demand;
    }
}
