package rmsa.net2plan;

public interface Net2PlanDemandView {
    String id();

    int sourceNodeId();

    int destinationNodeId();

    int requestedRateGbps();
}
