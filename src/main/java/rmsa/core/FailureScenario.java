package rmsa.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FailureScenario {
    private final Set<Integer> failedLinkIds;

    private FailureScenario(Set<Integer> failedLinkIds) {
        this.failedLinkIds = Collections.unmodifiableSet(new LinkedHashSet<Integer>(failedLinkIds));
    }

    public static FailureScenario noFailure() {
        return new FailureScenario(Collections.<Integer>emptySet());
    }

    public static FailureScenario failedLink(int linkId) {
        if (linkId < 0) {
            throw new IllegalArgumentException("Failed link id must be non-negative");
        }
        Set<Integer> ids = new LinkedHashSet<Integer>();
        ids.add(Integer.valueOf(linkId));
        return new FailureScenario(ids);
    }

    public static FailureScenario failedLinks(Collection<Integer> linkIds) {
        if (linkIds == null || linkIds.isEmpty()) {
            throw new IllegalArgumentException("At least one failed link id is required");
        }
        Set<Integer> ids = new LinkedHashSet<Integer>();
        for (Integer linkId : linkIds) {
            if (linkId == null || linkId.intValue() < 0) {
                throw new IllegalArgumentException("Failed link ids must be non-negative");
            }
            ids.add(linkId);
        }
        return new FailureScenario(ids);
    }

    public boolean isNoFailure() {
        return failedLinkIds.isEmpty();
    }

    public Integer failedLinkId() {
        if (failedLinkIds.isEmpty()) {
            return null;
        }
        return failedLinkIds.iterator().next();
    }

    public Set<Integer> failedLinkIds() {
        return failedLinkIds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FailureScenario)) return false;
        FailureScenario that = (FailureScenario) other;
        return Objects.equals(failedLinkIds, that.failedLinkIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failedLinkIds);
    }

    @Override
    public String toString() {
        if (isNoFailure()) {
            return "NO_FAILURE";
        }
        List<Integer> ids = new ArrayList<Integer>(failedLinkIds);
        Collections.sort(ids);
        return ids.size() == 1 ? "FAILED_LINK(" + ids.get(0) + ")" : "FAILED_LINKS" + ids;
    }
}
