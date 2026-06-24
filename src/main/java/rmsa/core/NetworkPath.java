package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NetworkPath {
    private final List<Link> links;

    public NetworkPath(List<Link> links) {
        if (links == null || links.isEmpty()) {
            throw new IllegalArgumentException("A path must contain at least one link");
        }
        this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
    }

    public List<Link> links() {
        return links;
    }

    public boolean isLinkDisjointWith(NetworkPath other) {
        Set<String> risks = physicalRiskKeys();
        for (Link link : other.links) {
            if (risks.contains(physicalRiskKey(link))) {
                return false;
            }
        }
        return true;
    }

    public boolean containsLink(int linkId) {
        for (Link link : links) {
            if (link.id() == linkId) {
                return true;
            }
        }
        return false;
    }

    public boolean containsAnyLink(Set<Integer> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            return false;
        }
        for (Link link : links) {
            if (linkIds.contains(Integer.valueOf(link.id()))) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> linkIds() {
        Set<Integer> ids = new HashSet<Integer>();
        for (Link link : links) {
            ids.add(link.id());
        }
        return ids;
    }

    public Set<String> physicalRiskKeys() {
        Set<String> risks = new HashSet<String>();
        for (Link link : links) {
            risks.add(physicalRiskKey(link));
        }
        return risks;
    }

    private String physicalRiskKey(Link link) {
        int firstNode = Math.min(link.origin(), link.destination());
        int secondNode = Math.max(link.origin(), link.destination());
        return firstNode + "-" + secondNode;
    }

    @Override
    public String toString() {
        return links.toString();
    }
}
