package rmsa.net2plan;

public interface Net2PlanLinkView {
    long id();

    int originNodeId();

    int destinationNodeId();

    double lengthKm();
}
