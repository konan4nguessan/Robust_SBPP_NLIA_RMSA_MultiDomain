package rmsa.net2plan.multidomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DomainPath {
    private final List<String> domainIds;

    public DomainPath(List<String> domainIds) {
        if (domainIds == null || domainIds.isEmpty()) {
            throw new IllegalArgumentException("A domain path must contain at least one domain");
        }
        List<String> normalized = new ArrayList<String>();
        for (String domainId : domainIds) {
            if (domainId == null || domainId.trim().isEmpty()) {
                throw new IllegalArgumentException("Domain ids must not be empty");
            }
            String value = domainId.trim();
            if (normalized.isEmpty() || !normalized.get(normalized.size() - 1).equals(value)) {
                normalized.add(value);
            }
        }
        this.domainIds = Collections.unmodifiableList(normalized);
    }

    public List<String> domainIds() {
        return domainIds;
    }

    public String sourceDomainId() {
        return domainIds.get(0);
    }

    public String destinationDomainId() {
        return domainIds.get(domainIds.size() - 1);
    }

    public boolean isSingleDomain() {
        return domainIds.size() == 1;
    }

    public Set<String> transitionKeys() {
        Set<String> keys = new LinkedHashSet<String>();
        for (int i = 0; i + 1 < domainIds.size(); i++) {
            keys.add(transitionKey(domainIds.get(i), domainIds.get(i + 1)));
        }
        return keys;
    }

    public static String transitionKey(String sourceDomainId, String destinationDomainId) {
        return sourceDomainId + "->" + destinationDomainId;
    }

    @Override
    public String toString() {
        return domainIds.toString();
    }
}
