package rmsa.net2plan.actual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.net2plan.interfaces.networkDesign.Link;

import rmsa.net2plan.Net2PlanPathView;

public final class ActualNet2PlanPathView implements Net2PlanPathView {
    private final List<Link> links;

    public ActualNet2PlanPathView(List<Link> links) {
        if (links == null || links.isEmpty()) {
            throw new IllegalArgumentException("A Net2Plan path must contain at least one link");
        }
        this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
    }

    @Override
    public List<Long> linkIds() {
        List<Long> ids = new ArrayList<Long>();
        for (Link link : links) {
            ids.add(Long.valueOf(link.getId()));
        }
        return ids;
    }

    public List<Link> links() {
        return links;
    }
}
