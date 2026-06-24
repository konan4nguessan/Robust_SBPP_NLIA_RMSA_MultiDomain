package rmsa.net2plan.actual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Route;

import rmsa.core.CfssEvaluation;
import rmsa.core.ModulationFormat;
import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.RmsaProvisioningDecision;

public final class Net2PlanDecisionApplier {
    public static final String ATTR_ROLE = "rmsaRole";
    public static final String ATTR_CONNECTION_ID = "rmsaConnectionId";
    public static final String ATTR_SLOTS = "rmsaSlots";
    public static final String ATTR_MODULATIONS = "rmsaModulations";
    public static final String ATTR_WORKING_ROUTE_ID = "rmsaWorkingRouteId";
    public static final String ATTR_BACKUP_ROUTE_ID = "rmsaBackupRouteId";
    public static final String ATTR_PROTECTION_STATE = "rmsaProtectionState";
    public static final String ATTR_INTER_DOMAIN = "interDomain";
    public static final String ATTR_DOMAIN_PATH = "domainPath";
    public static final String ATTR_DOMAIN_TRANSITIONS = "domainTransitions";

    public AppliedRoutes apply(NetPlan netPlan, Demand demand, AdaptedNetwork adaptedNetwork, RmsaProvisioningDecision decision) {
        return apply(netPlan, demand, demand.getOfferedTraffic(), demand.getOfferedTraffic(), adaptedNetwork, decision);
    }

    public AppliedRoutes apply(
            NetPlan netPlan,
            Demand demand,
            double carriedTraffic,
            double occupiedLinkCapacity,
            AdaptedNetwork adaptedNetwork,
            RmsaProvisioningDecision decision) {
        if (netPlan == null || demand == null || adaptedNetwork == null || decision == null) {
            throw new IllegalArgumentException("NetPlan, demand, adapted network and decision are required");
        }
        if (!decision.isFeasible()) {
            throw new IllegalArgumentException("Cannot apply an infeasible decision: " + decision.rejectionReason());
        }

        List<Link> workingLinks = toNet2PlanLinks(netPlan, adaptedNetwork, decision.connection().workingPath().links());
        List<Link> backupLinks = toNet2PlanLinks(netPlan, adaptedNetwork, decision.connection().backupPath().links());

        Route workingRoute = netPlan.addRoute(
                demand,
                carriedTraffic,
                occupiedLinkCapacity,
                workingLinks,
                attributes("WORKING", decision.connection().id(), decision.workingEvaluation()));

        Route backupRoute = netPlan.addRoute(
                demand,
                0.0,
                occupiedLinkCapacity,
                backupLinks,
                attributes("BACKUP", decision.connection().id(), decision.backupEvaluation()));
        workingRoute.addBackupRoute(backupRoute);
        workingRoute.setAttribute(ATTR_BACKUP_ROUTE_ID, String.valueOf(backupRoute.getId()));
        workingRoute.setAttribute(ATTR_PROTECTION_STATE, "WORKING_ACTIVE");
        backupRoute.setAttribute(ATTR_WORKING_ROUTE_ID, String.valueOf(workingRoute.getId()));
        backupRoute.setAttribute(ATTR_PROTECTION_STATE, "STANDBY");
        applyMultiDomainAttributes(demand, workingRoute, workingLinks);
        applyMultiDomainAttributes(demand, backupRoute, backupLinks);

        return new AppliedRoutes(workingRoute, backupRoute);
    }

    private void applyMultiDomainAttributes(Demand demand, Route route, List<Link> links) {
        List<String> domainPath = domainPath(demand, links);
        route.setAttribute(ATTR_INTER_DOMAIN, String.valueOf(domainPathHasTransition(domainPath)));
        route.setAttribute(ATTR_DOMAIN_PATH, joinStrings(domainPath, "->"));
        route.setAttribute(ATTR_DOMAIN_TRANSITIONS, domainTransitions(domainPath));
    }

    private List<String> domainPath(Demand demand, List<Link> links) {
        List<String> domains = new ArrayList<String>();
        addDomain(domains, domainOf(demand.getIngressNode()));
        for (Link link : links) {
            addDomain(domains, domainOf(link.getDestinationNode()));
        }
        return domains;
    }

    private void addDomain(List<String> domains, String domainId) {
        if (domains.isEmpty() || !domains.get(domains.size() - 1).equals(domainId)) {
            domains.add(domainId);
        }
    }

    private boolean domainPathHasTransition(List<String> domains) {
        Set<String> uniqueDomains = new LinkedHashSet<String>(domains);
        return uniqueDomains.size() > 1;
    }

    private String domainTransitions(List<String> domains) {
        List<String> transitions = new ArrayList<String>();
        for (int i = 0; i + 1 < domains.size(); i++) {
            String from = domains.get(i);
            String to = domains.get(i + 1);
            if (!from.equals(to)) {
                transitions.add(from + "->" + to);
            }
        }
        return joinStrings(transitions, ",");
    }

    private String domainOf(Node node) {
        String domainId = firstNonEmpty(
                node.getAttribute("domainId"),
                node.getAttribute("domain"),
                node.getAttribute("domainName"));
        return domainId == null ? "D0" : domainId;
    }

    private String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        if (third != null && !third.trim().isEmpty()) {
            return third.trim();
        }
        return null;
    }

    private String joinStrings(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(separator);
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private List<Link> toNet2PlanLinks(NetPlan netPlan, AdaptedNetwork adaptedNetwork, List<rmsa.core.Link> coreLinks) {
        List<Link> links = new ArrayList<Link>();
        for (rmsa.core.Link coreLink : coreLinks) {
            long externalId = adaptedNetwork.externalLinkId(coreLink.id());
            links.add(findLinkById(netPlan, externalId));
        }
        return links;
    }

    private Link findLinkById(NetPlan netPlan, long id) {
        for (Link link : netPlan.getLinks()) {
            if (link.getId() == id) {
                return link;
            }
        }
        throw new IllegalArgumentException("Cannot find Net2Plan link id: " + id);
    }

    private Map<String, String> attributes(String role, String connectionId, CfssEvaluation evaluation) {
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put(ATTR_ROLE, role);
        attributes.put(ATTR_CONNECTION_ID, connectionId);
        attributes.put(ATTR_SLOTS, joinIntegers(evaluation.slotIndexes()));
        attributes.put(ATTR_MODULATIONS, joinModulations(evaluation.modulationFormats()));
        return attributes;
    }

    private String joinIntegers(List<Integer> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(",");
            builder.append(values.get(i).intValue());
        }
        return builder.toString();
    }

    private String joinModulations(List<ModulationFormat> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(",");
            builder.append(values.get(i).name());
        }
        return builder.toString();
    }

    public static final class AppliedRoutes {
        private final Route workingRoute;
        private final Route backupRoute;

        private AppliedRoutes(Route workingRoute, Route backupRoute) {
            this.workingRoute = workingRoute;
            this.backupRoute = backupRoute;
        }

        public Route workingRoute() {
            return workingRoute;
        }

        public Route backupRoute() {
            return backupRoute;
        }
    }
}
