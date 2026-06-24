package rmsa.net2plan.actual;

import com.net2plan.interfaces.networkDesign.Link;

import rmsa.net2plan.Net2PlanLinkView;

public final class ActualNet2PlanLinkView implements Net2PlanLinkView {
    private final Link link;

    public ActualNet2PlanLinkView(Link link) {
        if (link == null) {
            throw new IllegalArgumentException("Net2Plan link is required");
        }
        this.link = link;
    }

    @Override
    public long id() {
        return link.getId();
    }

    @Override
    public int originNodeId() {
        return (int) link.getOriginNode().getId();
    }

    @Override
    public int destinationNodeId() {
        return (int) link.getDestinationNode().getId();
    }

    @Override
    public double lengthKm() {
        return link.getLengthInKm();
    }

    public Link link() {
        return link;
    }
}
