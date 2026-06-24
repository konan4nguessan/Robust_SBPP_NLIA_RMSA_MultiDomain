package rmsa.net2plan.actual;

import java.util.HashMap;
import java.util.Random;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.net2plan.interfaces.networkDesign.Demand;
import com.net2plan.interfaces.networkDesign.Link;
import com.net2plan.interfaces.networkDesign.NetPlan;
import com.net2plan.interfaces.networkDesign.Node;
import com.net2plan.interfaces.networkDesign.Route;
import com.net2plan.interfaces.simulation.IEventProcessor;
import com.net2plan.interfaces.simulation.SimEvent;
import com.net2plan.utils.InputParameter;
import com.net2plan.utils.Triple;

import rmsa.core.Connection;
import rmsa.core.ExistingQoTGuard;
import rmsa.core.FailureScenario;
import rmsa.core.GreedyRobustBitloading;
import rmsa.core.NetworkNliEvaluator;
import rmsa.core.NliSnapshotStore;
import rmsa.core.PhysicalLayerModel;
import rmsa.core.PhysicalLayerParameters;
import rmsa.core.RecursiveExistingConnectionsQoTChecker;
import rmsa.core.PathRole;
import rmsa.core.SlotAllocation;
import rmsa.core.TransceiverState;
import rmsa.net2plan.AdaptedNetwork;
import rmsa.net2plan.RmsaCoreProvisioner;
import rmsa.net2plan.RmsaProvisioningDecision;
import rmsa.net2plan.SbppNliaStats;
import rmsa.net2plan.multidomain.GlobalSdnBroker;
import rmsa.net2plan.multidomain.GlobalSdnBroker.GlobalAdmissionResult;
import rmsa.net2plan.multidomain.GlobalSdnBroker.GlobalBrokerStats;

public final class OnlineEvProc_RobustSbppRmsa extends IEventProcessor {
    private final InputParameter numSlotsPerLink = new InputParameter(
            "numSlotsPerLink",
            110,
            "Number of frequency slots per link");
    private final InputParameter kWorkingPaths = new InputParameter(
            "kWorkingPaths",
            3,
            "Number of working path candidates");
    private final InputParameter kBackupPaths = new InputParameter(
            "kBackupPaths",
            3,
            "Number of backup path candidates per working path");
    private final InputParameter maxPathSearchDepth = new InputParameter(
            "maxPathSearchDepth",
            12,
            "Maximum DFS depth for candidate path generation");
    private final InputParameter localControllerMaxLinkOccupancy = new InputParameter(
            "localControllerMaxLinkOccupancy",
            0.98D,
            "Local SDN controller module: reject local segments above this spectral occupancy; <=0 disables this local criterion");
    private final InputParameter existingConnectionQoTGuard = new InputParameter(
            "existingConnectionQoTGuard",
            "#select# RELAXED STRICT",
            "Existing-connection QoT guard mode: STRICT rejects candidates degrading established connections; RELAXED keeps candidate QoT/NLI only");

    private final InputParameter txPerNode = new InputParameter(
            "txPerNode",
            1400,
            "Number of TX resources per node");
    private final InputParameter rxPerNode = new InputParameter(
            "rxPerNode",
            1400,
            "Number of RX resources per node");

    private final InputParameter enableFailureInjection = new InputParameter(
            "enableFailureInjection",
            false,
            "Enable deterministic manual link failure injection");
    private final InputParameter failureInjectionMode = new InputParameter(
            "failureInjectionMode",
            "#select# TIME_BASED AFTER_N_ACCEPTED",
            "Failure injection mode");
    private final InputParameter failureAfterAcceptedConnections = new InputParameter(
            "failureAfterAcceptedConnections",
            1,
            "Accepted connections threshold for AFTER_N_ACCEPTED failure injection mode");
    private final InputParameter failureLinkSelection = new InputParameter(
            "failureLinkSelection",
            "#select# USER_DEFINED RANDOM MOST_USED",
            "How to choose the reference link for manual failure injection");
    private final InputParameter failureLinkId = new InputParameter(
            "failureLinkId",
            -1L,
            "Net2Plan link id to fail when failureLinkSelection=USER_DEFINED; the reverse directed link is also failed when present");
    private final InputParameter failureTime = new InputParameter(
            "failureTime",
            -1.0D,
            "Simulation time in seconds for deterministic link failure");
    private final InputParameter repairTime = new InputParameter(
            "repairTime",
            -1.0D,
            "Simulation time in seconds for deterministic link repair (-1 disables repair)");
    private final InputParameter revertiveMode = new InputParameter(
            "revertiveMode",
            true,
            "Return active backups to standby after repair");

    private final InputParameter debug = new InputParameter(
            "debug",
            false,
            "Print admission traces");
    private final InputParameter enableVisualization = new InputParameter(
            "enableVisualization",
            true,
            "Synchronize spectrum/link attributes and route protection states during the simulation");
    private final InputParameter uiRefreshEveryNEvents = new InputParameter(
            "uiRefreshEveryNEvents",
            100,
            "Refresh heavy Net2Plan visualization state every N add/remove events; use 1 for full refresh");
    private final InputParameter scientificTraceEnabled = new InputParameter(
            "scientificTraceEnabled",
            false,
            "If true, write a detailed scientific trace to a file without changing Java console output");
    private final InputParameter scientificTraceLevel = new InputParameter(
            "scientificTraceLevel",
            "#select# SUMMARY ADMISSION FULL",
            "Scientific trace level");
    private final InputParameter scientificTracePath = new InputParameter(
            "scientificTracePath",
            "",
            "Optional detailed scientific trace file path. Leave blank to disable file output.");
    private final InputParameter scientificTraceConnectionId = new InputParameter(
            "scientificTraceConnectionId",
            "-1",
            "Only trace this connection id. -1 traces all connections.");
    private final InputParameter scientificTraceBlockedOnly = new InputParameter(
            "scientificTraceBlockedOnly",
            false,
            "If true, keep only blocked-admission lines plus global failure/repair lines in the trace file.");
    private ActualNet2PlanAdapterFactory adapterFactory;
    private AdaptedNetwork adaptedNetwork;
    private NetworkNliEvaluator nliEvaluator;
    private NliSnapshotStore snapshotStore;
    private ExistingQoTGuard qotGuard;
    private GreedyRobustBitloading bitloading;
    private RmsaCoreProvisioner provisioner;
    private TransceiverState transceiverState;
    private GlobalCandidateAssemblyModule pathBuilder;
    private GlobalSdnBroker admissionBroker;
    private long brokerLocalPathsEvaluated;
    private long brokerLocalPathsRejected;
    private long brokerSbppRejectedPairs;
    private long brokerAssembledPairs;
    private long brokerFallbackRequests;
    private Net2PlanDecisionApplier decisionApplier;
    private Net2PlanSpectrumSynchronizer spectrumSynchronizer;
    private final RobustSbppRmsaMetrics metrics = new RobustSbppRmsaMetrics();
    private final SbppNliaStats optimizationStats = new SbppNliaStats();
    private final Map<Long, RmsaProvisioningDecision> decisionByWorkingRouteId =
            new HashMap<Long, RmsaProvisioningDecision>();
    private final Map<Long, Long> backupRouteIdToWorkingRouteId = new HashMap<Long, Long>();
    private final Set<Long> activeFailedExternalLinkIds = new HashSet<Long>();
    private final Set<String> activeBackupConnectionIds = new HashSet<String>();
    private boolean afterAcceptedFailureInjected;
    private long visualizationEventCounter;
    private ScientificTraceLogger scientificTraceLogger;
    private final Random failureRandom = new Random(1L);
    private long lastResolvedFailureLinkId = -1L;

    @Override
    public String getDescription() {
        return "Dynamic multi-domain robust SBPP-NLIA RMSA processor with configurable existing-connection QoT guard.";
    }

    @Override
    public List<Triple<String, String, String>> getParameters() {
        List<Triple<String, String, String>> parameters =
                InputParameter.getInformationAllInputParameterFieldsOfObject(this);
        Collections.sort(parameters, new Comparator<Triple<String, String, String>>() {
            public int compare(Triple<String, String, String> a, Triple<String, String, String> b) {
                int byGroup = Integer.compare(parameterOrderIndex(a.getFirst()), parameterOrderIndex(b.getFirst()));
                if (byGroup != 0) return byGroup;
                return a.getFirst().compareTo(b.getFirst());
            }
        });
        return parameters;
    }

    private int parameterOrderIndex(String name) {
        if ("numSlotsPerLink".equals(name)) return 10;
        if ("txPerNode".equals(name)) return 11;
        if ("rxPerNode".equals(name)) return 12;
        if ("kWorkingPaths".equals(name)) return 20;
        if ("kBackupPaths".equals(name)) return 21;
        if ("maxPathSearchDepth".equals(name)) return 22;
        if ("localControllerMaxLinkOccupancy".equals(name)) return 23;
        if ("existingConnectionQoTGuard".equals(name)) return 24;
        if ("enableFailureInjection".equals(name)) return 30;
        if ("failureInjectionMode".equals(name)) return 31;
        if ("failureLinkSelection".equals(name)) return 32;
        if ("failureLinkId".equals(name)) return 33;
        if ("failureTime".equals(name)) return 34;
        if ("failureAfterAcceptedConnections".equals(name)) return 35;
        if ("repairTime".equals(name)) return 36;
        if ("revertiveMode".equals(name)) return 37;
        if ("enableVisualization".equals(name)) return 40;
        if ("uiRefreshEveryNEvents".equals(name)) return 41;
        if ("debug".equals(name)) return 42;
        if ("scientificTraceEnabled".equals(name)) return 50;
        if ("scientificTraceLevel".equals(name)) return 51;
        if ("scientificTracePath".equals(name)) return 52;
        if ("scientificTraceConnectionId".equals(name)) return 53;
        if ("scientificTraceBlockedOnly".equals(name)) return 54;
        return 1000;
    }

    @Override
    public void initialize(
            NetPlan initialNetPlan,
            Map<String, String> algorithmParameters,
            Map<String, String> simulationParameters,
            Map<String, String> net2planParameters) {
        Map<String, String> parameters = InputParameter.getDefaultParameters(getParameters());
        if (algorithmParameters != null) {
            parameters.putAll(algorithmParameters);
        }
        InputParameter.initializeAllInputParameterFieldsOfObject(this, parameters);
        configureScientificTrace();
        rebuildCoreState(initialNetPlan);
        decisionByWorkingRouteId.clear();
        backupRouteIdToWorkingRouteId.clear();
        activeFailedExternalLinkIds.clear();
        activeBackupConnectionIds.clear();
        afterAcceptedFailureInjected = false;
        visualizationEventCounter = 0L;
        metrics.reset();
        optimizationStats.reset();
        scheduleConfiguredFailureEvents();
    }


    private void configureScientificTrace() {
        if (scientificTraceLogger == null) {
            scientificTraceLogger = new ScientificTraceLogger();
        }
        scientificTraceLogger.open(
                scientificTraceEnabled.getBoolean(),
                scientificTracePath.getString(),
                scientificTraceLevel.getString(),
                scientificTraceConnectionId.getString(),
                scientificTraceBlockedOnly.getBoolean());
    }

    private void traceHistoryGlobal(String tag, String message) {
        if (scientificTraceLogger != null) {
            scientificTraceLogger.traceGlobal(tag, message);
        }
    }

    private void traceHistoryConnection(String tag, String connectionId, String message) {
        if (scientificTraceLogger != null) {
            scientificTraceLogger.traceConnection(tag, connectionId, message);
        }
    }

    private boolean shouldTraceAdmission(String connectionId) {
        return scientificTraceLogger != null
                && scientificTraceLogger.isEnabled()
                && scientificTraceLogger.acceptsConnection(connectionId)
                && !scientificTraceLogger.isBlockedOnly()
                && scientificTraceLogger.isAdmissionLevel();
    }

    private void recordGlobalBrokerStats(GlobalBrokerStats stats) {
        if (stats == null) {
            return;
        }
        brokerLocalPathsEvaluated += stats.localPathsEvaluated();
        brokerLocalPathsRejected += stats.localPathsRejected();
        brokerSbppRejectedPairs += stats.sbppRejectedPairs();
        brokerAssembledPairs += stats.brokerAssembledPairs();
        if (stats.fallbackUsed()) {
            brokerFallbackRequests++;
        }
    }

    private String globalBrokerReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("globalBrokerLocalPathsEvaluated=").append(brokerLocalPathsEvaluated).append('\n');
        builder.append("globalBrokerLocalPathsRejected=").append(brokerLocalPathsRejected).append('\n');
        builder.append("globalBrokerSbppRejectedPairs=").append(brokerSbppRejectedPairs).append('\n');
        builder.append("globalBrokerAssembledPairs=").append(brokerAssembledPairs).append('\n');
        builder.append("globalBrokerFallbackRequests=").append(brokerFallbackRequests).append('\n');
        return builder.toString();
    }
    private boolean shouldTraceBlocked(String connectionId) {
        return scientificTraceLogger != null
                && scientificTraceLogger.isEnabled()
                && scientificTraceLogger.acceptsConnection(connectionId);
    }
    @Override
    public String finish(StringBuilder output, double simTime) {
        String report = metrics.report(adaptedNetwork == null ? null : adaptedNetwork.spectrum())
                + globalBrokerReport()
                + optimizationStats.report();
        traceHistoryGlobal("FINAL-REPORT", report.replace('\n', ' '));
        if (scientificTraceLogger != null) {
            scientificTraceLogger.close();
        }
        if (output != null) {
            output.append(report);
        }
        return report;
    }

    @Override
    public void processEvent(NetPlan currentNetPlan, SimEvent event) {
        Object eventObject = event.getEventObject();
        if (eventObject instanceof SimEvent.RouteAdd) {
            handleRouteAdd(currentNetPlan, event.getEventTime(), (SimEvent.RouteAdd) eventObject);
        } else if (eventObject instanceof SimEvent.RouteRemove) {
            handleRouteRemove(currentNetPlan, (SimEvent.RouteRemove) eventObject);
        } else if (eventObject instanceof SimEvent.NodesAndLinksChangeFailureState) {
            handleFailureStateChange(currentNetPlan, (SimEvent.NodesAndLinksChangeFailureState) eventObject);
        } else if (eventObject instanceof RuntimeLinkFailureEvent) {
            handleRuntimeLinkFailureEvent(currentNetPlan, (RuntimeLinkFailureEvent) eventObject);
        }
    }

    private int countDomainTransitions(String transitions) {
        if (transitions == null || transitions.trim().isEmpty()) {
            return 0;
        }
        return transitions.split(",").length;
    }
    private boolean isInterDomainDemand(Demand demand) {
        return !domainOf(demand.getIngressNode()).equals(domainOf(demand.getEgressNode()));
    }

    private String demandDomainPath(Demand demand) {
        return domainOf(demand.getIngressNode()) + "->" + domainOf(demand.getEgressNode());
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
    private void handleRouteAdd(NetPlan netPlan, double eventTime, SimEvent.RouteAdd event) {
        ensureReady(netPlan);
        if (event.demand == null) {
            return;
        }
        double requestedGbps = event.carriedTraffic > 0.0D
                ? event.carriedTraffic
                : event.demand.getOfferedTraffic();
        boolean interDomainRequest = isInterDomainDemand(event.demand);
        String requestDomainPath = demandDomainPath(event.demand);
        metrics.recordArrival(requestedGbps, interDomainRequest);
        optimizationStats.startRequest();
        traceHistoryGlobal("ARRIVAL",
                "t=" + eventTime
                + " demandId=" + event.demand.getId()
                + " src=" + event.demand.getIngressNode().getName()
                + " dst=" + event.demand.getEgressNode().getName()
                + " requestedGbps=" + requestedGbps
                + " interDomain=" + interDomainRequest
                + " domainPath=" + requestDomainPath
                + " activeConnectionsBefore=" + decisionByWorkingRouteId.size());
        GlobalAdmissionResult admission = admissionBroker.admit(
                netPlan,
                event.demand,
                requestedGbps,
                kWorkingPaths.getInt(),
                kBackupPaths.getInt());
        recordGlobalBrokerStats(admission.brokerStats());
        RmsaProvisioningDecision decision = admission.decision();
        String decisionConnectionId = decision.connection() == null
                ? "demand-" + event.demand.getId()
                : decision.connection().id();
        if (shouldTraceAdmission(decisionConnectionId)) {
            traceHistoryConnection("CANDIDATES-BUILT", decisionConnectionId,
                    "candidatePairs=" + admission.candidatePairs()
                    + " mode=ROBUST_QOT"
                    + " coreProfile=PAPER_ORDERED_SBPP_NLIA"
                    + " globalBroker=" + admission.brokerStats().summary()
                    + " requestedGbps=" + requestedGbps
                        + " interDomain=" + interDomainRequest
                        + " domainPath=" + requestDomainPath);
        }
        if (!decision.isFeasible()) {
            metrics.recordBlocked(requestedGbps, interDomainRequest);
            optimizationStats.endRequest(false);
            if (shouldTraceBlocked(decisionConnectionId)) {
                traceHistoryConnection("BLOCKED", decisionConnectionId,
                        "reason=" + decision.rejectionReason()
                        + " demandId=" + event.demand.getId()
                        + " requestedGbps=" + requestedGbps
                        + " interDomain=" + interDomainRequest
                        + " domainPath=" + requestDomainPath
                        + " candidatePairs=" + admission.candidatePairs()
                        + " selectedCandidatePairs=" + admission.selectedCandidatePairs());
            }
            trace("Blocked demand " + event.demand.getId() + ": " + decision.rejectionReason());
            return;
        }

        if (shouldTraceAdmission(decisionConnectionId)) {
            traceHistoryConnection("SELECTED-CANDIDATE", decisionConnectionId,
                    "objective=" + decision.objectiveValue()
                    + " workingPath=" + describeCorePath(decision.connection().workingPath())
                    + " backupPath=" + describeCorePath(decision.connection().backupPath())
                    + " workingSlots=" + decision.workingEvaluation().slotIndexes()
                    + " backupSlots=" + decision.backupEvaluation().slotIndexes()
                    + " workingModulations=" + decision.workingEvaluation().modulationFormats()
                    + " backupModulations=" + decision.backupEvaluation().modulationFormats());
        }

        Net2PlanDecisionApplier.AppliedRoutes routes = decisionApplier.apply(
                netPlan,
                event.demand,
                event.carriedTraffic,
                event.occupiedLinkCapacity,
                adaptedNetwork,
                decision);
        event.routeAddedToFillByProcessor = routes.workingRoute();
        decisionByWorkingRouteId.put(Long.valueOf(routes.workingRoute().getId()), decision);
        backupRouteIdToWorkingRouteId.put(
                Long.valueOf(routes.backupRoute().getId()),
                Long.valueOf(routes.workingRoute().getId()));
        reserveDecision(decision);
        updateRecursiveStateAfterAcceptedAllocation(decision);
        syncSpectrumAttributes(netPlan, false);
        metrics.recordAccepted(requestedGbps, interDomainRequest, countDomainTransitions(routes.workingRoute().getAttribute(Net2PlanDecisionApplier.ATTR_DOMAIN_TRANSITIONS)));
        optimizationStats.endRequest(true);
        if (shouldTraceAdmission(decision.connection().id())) {
            traceHistoryConnection("ACCEPTED", decision.connection().id(),
                    "demandId=" + event.demand.getId()
                    + " workingRouteId=" + routes.workingRoute().getId()
                    + " backupRouteId=" + routes.backupRoute().getId()
                    + " activeConnectionsAfter=" + decisionByWorkingRouteId.size()
                    + " interDomain=" + interDomainRequest
                    + " domainPath=" + requestDomainPath
                    + " routeDomainPath=" + routes.workingRoute().getAttribute(Net2PlanDecisionApplier.ATTR_DOMAIN_PATH)
                    + " domainTransitions=" + routes.workingRoute().getAttribute(Net2PlanDecisionApplier.ATTR_DOMAIN_TRANSITIONS)
                    + " acceptedSoFar=" + metrics.acceptedConnections());
        }
        trace("Accepted demand " + event.demand.getId() + " objective=" + decision.objectiveValue());
        maybeInjectFailureAfterAcceptedThreshold(netPlan);
    }

    private void handleRouteRemove(NetPlan netPlan, SimEvent.RouteRemove event) {
        if (event.route == null) {
            return;
        }
        Long workingRouteId = Long.valueOf(event.route.getId());
        if (backupRouteIdToWorkingRouteId.containsKey(workingRouteId)) {
            workingRouteId = backupRouteIdToWorkingRouteId.remove(workingRouteId);
        }
        RmsaProvisioningDecision decision = decisionByWorkingRouteId.remove(workingRouteId);
        if (decision != null) {
            activeBackupConnectionIds.remove(decision.connection().id());
            List<SlotAllocation> releasedAllocations = releaseDecision(decision);
            removeNet2PlanRoutes(event.route);
            updateRecursiveStateAfterReleasedAllocation(releasedAllocations);
            syncSpectrumAttributes(netPlan, false);
            metrics.recordDeparture();
            traceHistoryConnection("DEPARTURE", decision.connection().id(),
                    "workingRouteId=" + workingRouteId
                    + " activeConnectionsAfter=" + decisionByWorkingRouteId.size());
        } else if (isAttached(event.route)) {
            event.route.remove();
        }
    }

    private void rebuildCoreState(NetPlan netPlan) {
        adapterFactory = new ActualNet2PlanAdapterFactory(numSlotsPerLink.getInt());
        adaptedNetwork = adapterFactory.adaptNetPlan(netPlan);
        nliEvaluator = new NetworkNliEvaluator(
                adaptedNetwork.spectrum(),
                new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults()));
        nliEvaluator.setComputationListener(optimizationStats);
        snapshotStore = NliSnapshotStore.build(adaptedNetwork.spectrum(), nliEvaluator);
        qotGuard = new RecursiveExistingConnectionsQoTChecker(
                adaptedNetwork.spectrum(),
                nliEvaluator,
                snapshotStore);
        bitloading = new GreedyRobustBitloading(nliEvaluator);
        transceiverState = new TransceiverState(txPerNode.getInt(), rxPerNode.getInt());
        provisioner = new RmsaCoreProvisioner(
                adaptedNetwork.spectrum(),
                transceiverState,
                bitloading,
                qotGuard,
                isStrictExistingConnectionQoTGuard());
        provisioner.setStats(optimizationStats);
        pathBuilder = new GlobalCandidateAssemblyModule(adapterFactory.coreAdapter(), maxPathSearchDepth.getInt(), localControllerMaxLinkOccupancy.getDouble());
        admissionBroker = new GlobalSdnBroker(pathBuilder, provisioner, optimizationStats, adaptedNetwork);
        decisionApplier = new Net2PlanDecisionApplier();
        spectrumSynchronizer = new Net2PlanSpectrumSynchronizer();
        syncSpectrumAttributes(netPlan, true);
    }

    private boolean isStrictExistingConnectionQoTGuard() {
        return "STRICT".equalsIgnoreCase(existingConnectionQoTGuard.getString());
    }

    private void ensureReady(NetPlan netPlan) {
        if (adaptedNetwork == null || adaptedNetwork.coreLinks().size() != netPlan.getLinks().size()) {
            rebuildCoreState(netPlan);
        }
    }

    private void reserveDecision(RmsaProvisioningDecision decision) {
        adaptedNetwork.spectrum().reserveWorking(
                decision.connection(),
                decision.workingEvaluation().slotIndexes().get(0).intValue(),
                decision.workingEvaluation().modulationFormats());
        adaptedNetwork.spectrum().reserveBackup(
                decision.connection(),
                decision.backupEvaluation().slotIndexes().get(0).intValue(),
                decision.backupEvaluation().modulationFormats());
        transceiverState.reserveWorking(decision.connection(), decision.workingEvaluation().slotIndexes().size());
        transceiverState.reserveBackup(decision.connection(), decision.backupEvaluation().slotIndexes().size());
    }

private List<SlotAllocation> releaseDecision(RmsaProvisioningDecision decision) {
        Connection connection = decision.connection();
        List<SlotAllocation> releasedAllocations = slotAllocationsForDecision(decision);
        adaptedNetwork.spectrum().releaseWorking(connection, decision.workingEvaluation().slotIndexes());
        adaptedNetwork.spectrum().releaseBackup(connection, decision.backupEvaluation().slotIndexes());
        transceiverState.release(connection, PathRole.WORKING);
        transceiverState.release(connection, PathRole.BACKUP);
        return releasedAllocations;
    }

    private void updateRecursiveStateAfterAcceptedAllocation(RmsaProvisioningDecision decision) {
        snapshotStore.updateAfterAcceptedAllocation(
                adaptedNetwork.spectrum(),
                nliEvaluator,
                slotAllocationsForDecision(decision));
    }

    private void updateRecursiveStateAfterReleasedAllocation(List<SlotAllocation> releasedAllocations) {
        snapshotStore.updateAfterReleasedAllocation(
                adaptedNetwork.spectrum(),
                nliEvaluator,
                releasedAllocations);
    }

    private void syncSpectrumAttributes(NetPlan netPlan, boolean force) {
        if (!enableVisualization.getBoolean()) {
            return;
        }
        if (!force) {
            visualizationEventCounter++;
            int refreshEvery = Math.max(1, uiRefreshEveryNEvents.getInt());
            if ((visualizationEventCounter % refreshEvery) != 0) {
                return;
            }
        }
        if (spectrumSynchronizer != null && adaptedNetwork != null) {
            spectrumSynchronizer.sync(netPlan, adaptedNetwork, activeBackupConnectionIds);
        }
    }

    private void scheduleConfiguredFailureEvents() {
        if (!enableFailureInjection.getBoolean()) {
            return;
        }
        if ("AFTER_N_ACCEPTED".equalsIgnoreCase(normalizeSelectParameter(failureInjectionMode.getString()))) {
            trace("Failure injection will be triggered after "
                    + failureAfterAcceptedConnections.getInt()
                    + " accepted connection(s)");
            return;
        }

        long configuredFailureLinkId = configuredFailureLinkIdForScheduling();
        if (failureTime.getDouble() < 0.0D || (configuredFailureLinkId < 0L && "USER_DEFINED".equalsIgnoreCase(normalizeSelectParameter(failureLinkSelection.getString())))) {
            trace("Failure injection enabled but failure selection/failureTime is not configured");
            return;
        }

        scheduleEvent(new SimEvent(
                failureTime.getDouble(),
                SimEvent.DestinationModule.EVENT_PROCESSOR,
                -1,
                new RuntimeLinkFailureEvent(true, configuredFailureLinkId)));
        trace("Scheduled deterministic failure at t=" + failureTime.getDouble()
                + " for link " + configuredFailureLinkId);

        if (repairTime.getDouble() >= 0.0D && repairTime.getDouble() > failureTime.getDouble()) {
            scheduleEvent(new SimEvent(
                    repairTime.getDouble(),
                    SimEvent.DestinationModule.EVENT_PROCESSOR,
                    -1,
                    new RuntimeLinkFailureEvent(false, configuredFailureLinkId)));
            trace("Scheduled deterministic repair at t=" + repairTime.getDouble()
                    + " for link " + configuredFailureLinkId);
        }
    }

    private void maybeInjectFailureAfterAcceptedThreshold(NetPlan netPlan) {
        if (!enableFailureInjection.getBoolean()
                || afterAcceptedFailureInjected
                || !"AFTER_N_ACCEPTED".equalsIgnoreCase(normalizeSelectParameter(failureInjectionMode.getString()))) {
            return;
        }
        if (failureAfterAcceptedConnections.getInt() <= 0
                || metrics.acceptedConnections() < failureAfterAcceptedConnections.getInt()) {
            return;
        }
        long configuredFailureLinkId = configuredFailureLinkIdForScheduling();
        if (configuredFailureLinkId < 0L && "USER_DEFINED".equalsIgnoreCase(normalizeSelectParameter(failureLinkSelection.getString()))) {
            trace("AFTER_N_ACCEPTED failure injection skipped: failure selection is not configured");
            afterAcceptedFailureInjected = true;
            return;
        }
        afterAcceptedFailureInjected = true;
        handleRuntimeLinkFailureEvent(netPlan, new RuntimeLinkFailureEvent(true, configuredFailureLinkId));
    }


    private long configuredFailureLinkIdForScheduling() {
        String selection = normalizeSelectParameter(failureLinkSelection.getString());
        if ("USER_DEFINED".equalsIgnoreCase(selection)) {
            return failureLinkId.getLong().longValue();
        }
        return -1L;
    }

    private long resolveFailureLinkId(NetPlan netPlan, RuntimeLinkFailureEvent event) {
        if (!event.failure() && event.linkId() < 0L && lastResolvedFailureLinkId >= 0L) {
            return lastResolvedFailureLinkId;
        }
        if (event.linkId() >= 0L) {
            return event.linkId();
        }
        String selection = normalizeSelectParameter(failureLinkSelection.getString());
        if ("RANDOM".equalsIgnoreCase(selection)) {
            return randomLinkId(netPlan);
        }
        if ("MOST_USED".equalsIgnoreCase(selection)) {
            return mostUsedWorkingLinkId(netPlan);
        }
        return failureLinkId.getLong().longValue();
    }

    private long randomLinkId(NetPlan netPlan) {
        List<Link> links = new ArrayList<Link>(netPlan.getLinks());
        if (links.isEmpty()) {
            return -1L;
        }
        return links.get(failureRandom.nextInt(links.size())).getId();
    }

    private long mostUsedWorkingLinkId(NetPlan netPlan) {
        long bestLinkId = -1L;
        int bestWorkingSlots = -1;
        if (adaptedNetwork == null) {
            return failureLinkId.getLong().longValue();
        }
        for (Link externalLink : netPlan.getLinks()) {
            int workingSlots = 0;
            try {
                workingSlots = workingSlotsOnCoreLink(adaptedNetwork.coreLinkId(externalLink.getId()));
            } catch (RuntimeException ignored) {
                workingSlots = 0;
            }
            if (workingSlots > bestWorkingSlots) {
                bestWorkingSlots = workingSlots;
                bestLinkId = externalLink.getId();
            }
        }
        return bestLinkId >= 0L ? bestLinkId : failureLinkId.getLong().longValue();
    }

    private int workingSlotsOnCoreLink(int coreLinkId) {
        int count = 0;
        if (adaptedNetwork == null || coreLinkId < 0) {
            return count;
        }
        for (int slot = 0; slot < adaptedNetwork.spectrum().slotCount(); slot++) {
            if (adaptedNetwork.spectrum().slot(coreLinkId, slot).workingOwner() != null) {
                count++;
            }
        }
        return count;
    }
    private void handleRuntimeLinkFailureEvent(NetPlan netPlan, RuntimeLinkFailureEvent event) {
        ensureReady(netPlan);
        long resolvedLinkId = resolveFailureLinkId(netPlan, event);
        Link referenceLink = findLinkById(netPlan, resolvedLinkId);
        if (referenceLink == null) {
            trace("Configured failure link not found: " + resolvedLinkId);
            return;
        }
        if (event.failure()) {
            lastResolvedFailureLinkId = resolvedLinkId;
        }
        if (event.failure()) {
            handleFailureStateChange(
                    netPlan,
                    new SimEvent.NodesAndLinksChangeFailureState(
                            null,
                            null,
                            null,
                            Collections.singleton(referenceLink)));
            trace("Injected bidirectional failure for reference link " + resolvedLinkId + " selection=" + normalizeSelectParameter(failureLinkSelection.getString()));
        } else {
            Set<Link> affectedLinks = bidirectionalFailureSet(netPlan, referenceLink);
            if (revertiveMode.getBoolean()) {
                handleFailureStateChange(
                        netPlan,
                        new SimEvent.NodesAndLinksChangeFailureState(
                                null,
                                null,
                                affectedLinks,
                                null));
            } else {
                for (Link affected : affectedLinks) {
                    affected.setFailureState(true);
                }
                syncSpectrumAttributes(netPlan, true);
            }
            trace("Injected bidirectional repair for reference link " + resolvedLinkId + " selection=" + normalizeSelectParameter(failureLinkSelection.getString()));
        }
    }

    private String normalizeSelectParameter(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.toLowerCase().startsWith("#select#")) {
            String[] tokens = normalized.split("\\s+");
            return tokens.length > 1 ? tokens[1] : "";
        }
        return normalized;
    }

    private void handleFailureStateChange(NetPlan netPlan, SimEvent.NodesAndLinksChangeFailureState event) {
        traceHistoryGlobal("FAILURE-STATE-CHANGE", "linksToDown=" + describeLinks(event.linksToDown) + " linksToUp=" + describeLinks(event.linksToUp));
        ensureReady(netPlan);

        Set<String> activeBackupsBefore = new HashSet<String>(activeBackupConnectionIds);
        Set<Link> changedBidirectionalLinks = new HashSet<Link>();
        boolean hasDownEvent = event.linksToDown != null && !event.linksToDown.isEmpty();
        boolean hasUpEvent = event.linksToUp != null && !event.linksToUp.isEmpty();

        if (event.linksToDown != null) {
            for (Link link : event.linksToDown) {
                for (Link affected : bidirectionalFailureSet(netPlan, link)) {
                    affected.setFailureState(false);
                    activeFailedExternalLinkIds.add(Long.valueOf(affected.getId()));
                    changedBidirectionalLinks.add(affected);
                }
            }
        }
        if (event.linksToUp != null) {
            for (Link link : event.linksToUp) {
                for (Link affected : bidirectionalFailureSet(netPlan, link)) {
                    affected.setFailureState(true);
                    activeFailedExternalLinkIds.remove(Long.valueOf(affected.getId()));
                    changedBidirectionalLinks.add(affected);
                }
            }
        }
        recomputeActiveBackups(netPlan);

        if (hasDownEvent) {
            long affectedConnections = activeBackupConnectionIds.size();
            long activatedBackups = activeBackupConnectionIds.size();
            double affectedGbps = sumConnectionGbps(activeBackupConnectionIds);
            double restoredGbps = affectedGbps;
            double lostGbps = Math.max(0.0D, affectedGbps - restoredGbps);
            metrics.recordFailureOutcome(
                    describeLinks(changedBidirectionalLinks),
                    affectedConnections,
                    activatedBackups,
                    affectedGbps,
                    restoredGbps,
                    lostGbps,
                    activeBackupConnectionIds.size());
            metrics.recordPostFailureQoTAudit(
                    adaptedNetwork.spectrum(),
                    nliEvaluator,
                    currentCoreFailureScenario());
            traceHistoryGlobal("FAILURE-RESTORATION",
                    "failedLinks=" + describeLinks(changedBidirectionalLinks)
                    + " affectedConnections=" + affectedConnections
                    + " activatedBackups=" + activatedBackups
                    + " affectedGbps=" + affectedGbps
                    + " restoredGbps=" + restoredGbps
                    + " lostGbps=" + lostGbps
                    + " activeBackupConnectionIds=" + activeBackupConnectionIds);
        }
        if (hasUpEvent) {
            metrics.recordRepairOutcome(activeBackupsBefore.size(), activeBackupConnectionIds.size());
            traceHistoryGlobal("REPAIR-RESTORATION",
                    "repairedLinks=" + describeLinks(changedBidirectionalLinks)
                    + " activeBackupsBefore=" + activeBackupsBefore.size()
                    + " activeBackupsAfter=" + activeBackupConnectionIds.size());
        }

        syncSpectrumAttributes(netPlan, true);
    }

    private FailureScenario currentCoreFailureScenario() {
        Set<Integer> failedCoreLinkIds = new HashSet<Integer>();
        if (adaptedNetwork == null) {
            return FailureScenario.noFailure();
        }
        for (Long externalLinkId : activeFailedExternalLinkIds) {
            if (externalLinkId == null) {
                continue;
            }
            try {
                int coreLinkId = adaptedNetwork.coreLinkId(externalLinkId.longValue());
                if (coreLinkId >= 0) {
                    failedCoreLinkIds.add(Integer.valueOf(coreLinkId));
                }
            } catch (RuntimeException ignored) {
                // Net2Plan can transiently expose links that are not present in the adapted core topology.
            }
        }
        if (failedCoreLinkIds.isEmpty()) {
            return FailureScenario.noFailure();
        }
        return FailureScenario.failedLinks(failedCoreLinkIds);
    }

    private double sumConnectionGbps(Set<String> connectionIds) {
        if (connectionIds == null || connectionIds.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (RmsaProvisioningDecision decision : decisionByWorkingRouteId.values()) {
            if (decision == null || decision.connection() == null) {
                continue;
            }
            if (connectionIds.contains(decision.connection().id())) {
                sum += decision.connection().request().dataRateGbps();
            }
        }
        return sum;
    }

    private Set<Link> bidirectionalFailureSet(NetPlan netPlan, Link referenceLink) {
        Set<Link> links = new HashSet<Link>();
        if (referenceLink == null) {
            return links;
        }
        links.add(referenceLink);
        Node origin = referenceLink.getOriginNode();
        Node destination = referenceLink.getDestinationNode();
        for (Link candidate : netPlan.getLinks()) {
            if (candidate.getId() != referenceLink.getId()
                    && candidate.getOriginNode().equals(destination)
                    && candidate.getDestinationNode().equals(origin)) {
                links.add(candidate);
            }
        }
        return links;
    }

    private void recomputeActiveBackups(NetPlan netPlan) {
        activeBackupConnectionIds.clear();
        if (!activeFailedExternalLinkIds.isEmpty()) {
            Set<String> failedRisks = physicalRiskKeys(netPlan, activeFailedExternalLinkIds);
            for (RmsaProvisioningDecision decision : decisionByWorkingRouteId.values()) {
                if (workingPathTouchesFailedRisk(netPlan, decision, failedRisks)) {
                    activeBackupConnectionIds.add(decision.connection().id());
                }
            }
        }
        traceHistoryGlobal("ACTIVE-BACKUPS", "count=" + activeBackupConnectionIds.size() + " connectionIds=" + activeBackupConnectionIds);
        updateRouteProtectionStates(netPlan);
    }

    private boolean workingPathTouchesFailedRisk(NetPlan netPlan, RmsaProvisioningDecision decision, Set<String> failedRisks) {
        for (rmsa.core.Link coreLink : decision.connection().workingPath().links()) {
            Link link = findLinkById(netPlan, adaptedNetwork.externalLinkId(coreLink.id()));
            if (link != null && failedRisks.contains(physicalRiskKey(link))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> physicalRiskKeys(NetPlan netPlan, Collection<Long> externalLinkIds) {
        Set<String> risks = new HashSet<String>();
        for (Long linkId : externalLinkIds) {
            if (linkId != null) {
                Link link = findLinkById(netPlan, linkId.longValue());
                if (link != null) {
                    risks.add(physicalRiskKey(link));
                }
            }
        }
        return risks;
    }

    private String physicalRiskKey(Link link) {
        long firstNode = Math.min(link.getOriginNode().getId(), link.getDestinationNode().getId());
        long secondNode = Math.max(link.getOriginNode().getId(), link.getDestinationNode().getId());
        return "physical:" + firstNode + "-" + secondNode;
    }

    private void updateRouteProtectionStates(NetPlan netPlan) {
        for (Map.Entry<Long, RmsaProvisioningDecision> entry : decisionByWorkingRouteId.entrySet()) {
            Route workingRoute = findRouteById(netPlan, entry.getKey().longValue());
            if (workingRoute == null) {
                continue;
            }
            boolean backupActive = activeBackupConnectionIds.contains(entry.getValue().connection().id());
            workingRoute.setAttribute(
                    Net2PlanDecisionApplier.ATTR_PROTECTION_STATE,
                    backupActive ? "FAILED_WORKING" : "WORKING_ACTIVE");
            for (Route backupRoute : new ArrayList<Route>(workingRoute.getBackupRoutes())) {
                backupRoute.setAttribute(
                        Net2PlanDecisionApplier.ATTR_PROTECTION_STATE,
                        backupActive ? "BACKUP_ACTIVE" : "STANDBY");
            }
        }
    }

    private Link findLinkById(NetPlan netPlan, long linkId) {
        for (Link link : netPlan.getLinks()) {
            if (link.getId() == linkId) {
                return link;
            }
        }
        return null;
    }

    private Route findRouteById(NetPlan netPlan, long routeId) {
        for (Route route : netPlan.getRoutes()) {
            if (route.getId() == routeId) {
                return route;
            }
        }
        return null;
    }

    private List<SlotAllocation> slotAllocationsForDecision(RmsaProvisioningDecision decision) {
        List<SlotAllocation> allocations = new ArrayList<SlotAllocation>();
        for (int i = 0; i < decision.workingEvaluation().slotIndexes().size(); i++) {
            allocations.add(new SlotAllocation(
                    decision.connection(),
                    PathRole.WORKING,
                    decision.workingEvaluation().slotIndexes().get(i).intValue(),
                    decision.workingEvaluation().modulationFormats().get(i)));
        }
        for (int i = 0; i < decision.backupEvaluation().slotIndexes().size(); i++) {
            allocations.add(new SlotAllocation(
                    decision.connection(),
                    PathRole.BACKUP,
                    decision.backupEvaluation().slotIndexes().get(i).intValue(),
                    decision.backupEvaluation().modulationFormats().get(i)));
        }
        return allocations;
    }

    private void removeNet2PlanRoutes(Route route) {
        if (!isAttached(route)) {
            return;
        }
        for (Route backup : new ArrayList<Route>(route.getBackupRoutes())) {
            Long backupId = Long.valueOf(backup.getId());
            backupRouteIdToWorkingRouteId.remove(backupId);
            if (isAttached(backup)) {
                backup.remove();
            }
        }
        if (isAttached(route)) {
            route.remove();
        }
    }

    private boolean isAttached(Route route) {
        try {
            return route != null && route.getNetPlan() != null;
        } catch (RuntimeException e) {
            return false;
        }
    }


    private String describeCorePath(rmsa.core.NetworkPath path) {
        if (path == null || path.links().isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < path.links().size(); i++) {
            rmsa.core.Link link = path.links().get(i);
            if (i > 0) builder.append(" | ");
            builder.append(link.id()).append(":")
                    .append(link.origin()).append("->")
                    .append(link.destination());
        }
        return builder.toString();
    }

    private String describeLinks(Collection<Link> links) {
        if (links == null || links.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (Link link : links) {
            if (i++ > 0) builder.append(" | ");
            if (link == null) {
                builder.append("null");
            } else {
                builder.append(link.getId()).append(":")
                        .append(link.getOriginNode().getName())
                        .append("->")
                        .append(link.getDestinationNode().getName());
            }
        }
        return builder.toString();
    }
    private void trace(String message) {
        if (debug.getBoolean()) {
            System.out.println("[RobustSbppRmsa] " + message);
        }
    }

    private static final class RuntimeLinkFailureEvent {
        private final boolean failure;
        private final long linkId;

        private RuntimeLinkFailureEvent(boolean failure, long linkId) {
            this.failure = failure;
            this.linkId = linkId;
        }

        private boolean failure() {
            return failure;
        }

        private long linkId() {
            return linkId;
        }

        @Override
        public String toString() {
            return (failure ? "Fail" : "Repair") + " bidirectional link for reference link id " + linkId;
        }
    }
}


