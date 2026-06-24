package rmsa.net2plan.multidomain;

import java.util.List;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.NetPlan;

import rmsa.core.ConnectionRequest;
import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.CandidatePathPair;
import rmsa.net2plan.RmsaCoreProvisioner;
import rmsa.net2plan.RmsaProvisioningDecision;
import rmsa.net2plan.SbppNliaStats;
import rmsa.net2plan.actual.GlobalCandidateAssemblyModule;

public final class GlobalSdnBroker {
    private final GlobalCandidateAssemblyModule pathBuilder;
    private final RmsaCoreProvisioner robustProvisioner;
    private final SbppNliaStats stats;
    private final AdaptedNetwork adaptedNetwork;

    public GlobalSdnBroker(
            GlobalCandidateAssemblyModule pathBuilder,
            RmsaCoreProvisioner robustProvisioner,
            SbppNliaStats stats,
            AdaptedNetwork adaptedNetwork) {
        if (pathBuilder == null || robustProvisioner == null || adaptedNetwork == null) {
            throw new IllegalArgumentException("Path builder, robust provisioner and adapted network are required");
        }
        this.pathBuilder = pathBuilder;
        this.robustProvisioner = robustProvisioner;
        this.stats = stats;
        this.adaptedNetwork = adaptedNetwork;
    }

    public GlobalAdmissionResult admit(
            NetPlan netPlan,
            Demand demand,
            double requestedGbps,
            int kWorkingPaths,
            int kBackupPaths) {
        List<CandidatePathPair> candidatePairs = pathBuilder.buildCandidatePairs(
                netPlan,
                demand,
                adaptedNetwork,
                kWorkingPaths,
                kBackupPaths,
                requestedGbps);
        if (stats != null) {
            stats.recordCandidatePairsGenerated(candidatePairs.size());
        }
        GlobalBrokerStats brokerStats = new GlobalBrokerStats(
                pathBuilder.lastLocalPathsEvaluated(),
                pathBuilder.lastLocalPathsRejected(),
                pathBuilder.lastSbppRejectedPairs(),
                pathBuilder.lastAssembledPairs(),
                pathBuilder.lastUsedFallback(),
                pathBuilder.lastAssemblySummary());
        if (candidatePairs.isEmpty()) {
            return new GlobalAdmissionResult(
                    RmsaProvisioningDecision.rejected("No link-disjoint candidate pair found"),
                    0,
                    0,
                    brokerStats);
        }

        if (stats != null) {
            stats.recordCandidatePairsSelectedForValidation(candidatePairs.size());
        }
        String connectionId = "demand-" + demand.getId() + "-t" + System.nanoTime();
        ConnectionRequest request = new ConnectionRequest(
                Long.toString(demand.getId()),
                (int) demand.getIngressNode().getId(),
                (int) demand.getEgressNode().getId(),
                (int) Math.ceil(requestedGbps));
        RmsaProvisioningDecision decision = robustProvisioner.chooseFirstFeasible(
                connectionId,
                request,
                candidatePairs);
        return new GlobalAdmissionResult(
                decision,
                candidatePairs.size(),
                candidatePairs.size(),
                brokerStats);
    }

    public static final class GlobalAdmissionResult {
        private final RmsaProvisioningDecision decision;
        private final int candidatePairs;
        private final int selectedCandidatePairs;
        private final GlobalBrokerStats brokerStats;

        private GlobalAdmissionResult(
                RmsaProvisioningDecision decision,
                int candidatePairs,
                int selectedCandidatePairs,
                GlobalBrokerStats brokerStats) {
            this.decision = decision;
            this.candidatePairs = candidatePairs;
            this.selectedCandidatePairs = selectedCandidatePairs;
            this.brokerStats = brokerStats;
        }

        public RmsaProvisioningDecision decision() {
            return decision;
        }

        public int candidatePairs() {
            return candidatePairs;
        }

        public int selectedCandidatePairs() {
            return selectedCandidatePairs;
        }

        public GlobalBrokerStats brokerStats() {
            return brokerStats;
        }
    }

    public static final class GlobalBrokerStats {
        private final int localPathsEvaluated;
        private final int localPathsRejected;
        private final int sbppRejectedPairs;
        private final int brokerAssembledPairs;
        private final boolean fallbackUsed;
        private final String summary;

        private GlobalBrokerStats(
                int localPathsEvaluated,
                int localPathsRejected,
                int sbppRejectedPairs,
                int brokerAssembledPairs,
                boolean fallbackUsed,
                String summary) {
            this.localPathsEvaluated = localPathsEvaluated;
            this.localPathsRejected = localPathsRejected;
            this.sbppRejectedPairs = sbppRejectedPairs;
            this.brokerAssembledPairs = brokerAssembledPairs;
            this.fallbackUsed = fallbackUsed;
            this.summary = summary;
        }

        public int localPathsEvaluated() {
            return localPathsEvaluated;
        }

        public int localPathsRejected() {
            return localPathsRejected;
        }

        public int sbppRejectedPairs() {
            return sbppRejectedPairs;
        }

        public int brokerAssembledPairs() {
            return brokerAssembledPairs;
        }

        public boolean fallbackUsed() {
            return fallbackUsed;
        }

        public String summary() {
            return summary;
        }
    }
}
