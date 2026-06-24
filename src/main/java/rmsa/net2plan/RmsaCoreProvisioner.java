package rmsa.net2plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rmsa.core.CfssEvaluation;
import rmsa.core.Connection;
import rmsa.core.ConnectionRequest;
import rmsa.core.ContiguousSlotBlock;
import rmsa.core.ExistingQoTCheck;
import rmsa.core.ExistingQoTGuard;
import rmsa.core.GreedyRobustBitloading;
import rmsa.core.HypotheticalSlotAllocation;
import rmsa.core.Link;
import rmsa.core.NetworkPath;
import rmsa.core.ObjectiveFunction;
import rmsa.core.PathRole;
import rmsa.core.RmsaSizing;
import rmsa.core.RobustCfssBackupEvaluator;
import rmsa.core.RobustCfssWorkingEvaluator;
import rmsa.core.SpectrumState;
import rmsa.core.TransceiverState;

public final class RmsaCoreProvisioner {
    private final SpectrumState spectrum;
    private final TransceiverState transceivers;
    private final RobustCfssWorkingEvaluator workingEvaluator;
    private final RobustCfssBackupEvaluator backupEvaluator;
    private final ExistingQoTGuard existingQoTGuard;
    private final boolean checkExistingConnectionQoT;
    private SbppNliaStats stats;

    public RmsaCoreProvisioner(
            SpectrumState spectrum,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard) {
        this(spectrum, null, bitloading, existingQoTGuard, true);
    }

    public RmsaCoreProvisioner(
            SpectrumState spectrum,
            TransceiverState transceivers,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard) {
        this(spectrum, transceivers, bitloading, existingQoTGuard, true);
    }

    public RmsaCoreProvisioner(
            SpectrumState spectrum,
            TransceiverState transceivers,
            GreedyRobustBitloading bitloading,
            ExistingQoTGuard existingQoTGuard,
            boolean checkExistingConnectionQoT) {
        if (spectrum == null || bitloading == null || existingQoTGuard == null) {
            throw new IllegalArgumentException("Spectrum, bitloading and QoT guard are required");
        }
        this.spectrum = spectrum;
        this.transceivers = transceivers;
        this.existingQoTGuard = existingQoTGuard;
        this.checkExistingConnectionQoT = checkExistingConnectionQoT;
        this.workingEvaluator = new RobustCfssWorkingEvaluator(
                spectrum,
                bitloading,
                existingQoTGuard,
                checkExistingConnectionQoT);
        this.backupEvaluator = new RobustCfssBackupEvaluator(
                spectrum,
                bitloading,
                existingQoTGuard,
                checkExistingConnectionQoT);
    }

    public void setStats(SbppNliaStats stats) {
        this.stats = stats;
    }

    public RmsaProvisioningDecision chooseFirstFeasible(
            String connectionId,
            ConnectionRequest request,
            List<CandidatePathPair> candidatePairs) {
        if (request == null || candidatePairs == null || candidatePairs.isEmpty()) {
            throw new IllegalArgumentException("Request and candidate pairs are required");
        }

        int minimumWidth = RmsaSizing.minimumSlotsForBestModulation(request.dataRateGbps());
        RmsaProvisioningDecision bestDecision = null;
        Map<String, List<CandidatePathPair>> pairsByWorkingPath = groupByWorkingPath(candidatePairs);
        for (List<CandidatePathPair> workingGroup : pairsByWorkingPath.values()) {
            CandidatePathPair firstPair = workingGroup.get(0);
            Connection workingCandidate = new Connection(connectionId, request, firstPair.workingPath(), firstPair.backupPath());
            CfssEvaluation bestWorking = null;
            double bestWorkingObjective = Double.POSITIVE_INFINITY;
            List<ContiguousSlotBlock> workingBlocks = spectrum.workingCandidateBlocks(firstPair.workingPath(), minimumWidth);
            if (workingBlocks.isEmpty() && stats != null) {
                stats.recordBlockedNoWorkingWindow();
            }
            for (ContiguousSlotBlock workingBlock : workingBlocks) {
                CfssEvaluation working = workingEvaluator.evaluate(workingCandidate, workingBlock);
                if (stats != null) {
                    stats.recordWorkingEvaluation(working.isFeasible());
                    if (!working.isFeasible()) {
                        recordWorkingRejection(working);
                    }
                }
                if (!working.isFeasible()) {
                    continue;
                }
                double workingObjective = workingObjectiveValue(workingCandidate, working);
                if (Double.isInfinite(workingObjective) || Double.isNaN(workingObjective)) {
                    continue;
                }
                if (bestWorking == null || workingObjective < bestWorkingObjective) {
                    bestWorking = working;
                    bestWorkingObjective = workingObjective;
                }
            }
            if (bestWorking == null) {
                continue;
            }

            for (CandidatePathPair pair : workingGroup) {
                if (stats != null) {
                    stats.recordCandidatePairTested();
                }
                Connection candidate = new Connection(connectionId, request, pair.workingPath(), pair.backupPath());
                List<ContiguousSlotBlock> backupBlocks = spectrum.backupCandidateBlocks(pair.backupPath(), minimumWidth, candidate);
                if (backupBlocks.isEmpty() && stats != null) {
                    stats.recordBlockedNoBackupWindow();
                }
                for (ContiguousSlotBlock backupBlock : backupBlocks) {
                    CfssEvaluation backup = backupEvaluator.evaluateWithWorkingAllocation(candidate, backupBlock, bestWorking);
                    if (stats != null) {
                        stats.recordBackupEvaluation(backup.isFeasible());
                        if (!backup.isFeasible()) {
                            recordBackupRejection(backup);
                        }
                    }
                    if (!backup.isFeasible()) {
                        continue;
                    }
                    if (checkExistingConnectionQoT) {
                        ExistingQoTCheck existingCheck = existingQoTGuard.checkWithHypotheticals(
                                fullCandidateHypotheticals(candidate, bestWorking, backup));
                        if (!existingCheck.isFeasible()) {
                            if (stats != null) {
                                stats.recordBlockedCoupledWorkingBackupQoT();
                            }
                            continue;
                        }
                    }
                    if (stats != null) {
                        stats.recordFeasibleCouple();
                        stats.recordObjectiveEvaluation();
                    }
                    double objectiveValue = objectiveValue(candidate, bestWorking, backup);
                    if (!Double.isInfinite(objectiveValue) && !Double.isNaN(objectiveValue)) {
                        RmsaProvisioningDecision decision = RmsaProvisioningDecision.feasible(
                                candidate,
                                bestWorking,
                                backup,
                                objectiveValue);
                        if (bestDecision == null || decision.objectiveValue() < bestDecision.objectiveValue()) {
                            bestDecision = decision;
                        }
                    }
                }
            }
        }

        if (bestDecision != null) {
            return bestDecision;
        }
        if (stats != null) {
            stats.recordBlockedNoFeasiblePathPair();
        }
        return RmsaProvisioningDecision.rejected("No feasible working/backup CFSS pair found");
    }

    private void recordWorkingRejection(CfssEvaluation working) {
        if (stats == null || working == null || working.isFeasible()) {
            return;
        }
        String reason = working.rejectionReason();
        if (reason == null) {
            return;
        }
        if (reason.contains("existing connection")) {
            stats.recordBlockedWorkingExistingQoT(
                    workingEvaluator.lastExistingQoTViolation(),
                    workingEvaluator.lastRejectedCandidateSlot());
        } else if (reason.contains("QoT") || reason.contains("SINR")) {
            stats.recordBlockedWorkingSelfQoT();
        }
    }

    private void recordBackupRejection(CfssEvaluation backup) {
        if (stats == null || backup == null || backup.isFeasible()) {
            return;
        }
        String reason = backup.rejectionReason();
        if (reason == null) {
            return;
        }
        if (reason.contains("existing connection")) {
            stats.recordBlockedBackupExistingQoT(
                    backupEvaluator.lastExistingQoTViolation(),
                    backupEvaluator.lastRejectedCandidateSlot());
        } else if (reason.contains("QoT") || reason.contains("SINR")) {
            stats.recordBlockedBackupSelfQoT();
        }
    }

    private Map<String, List<CandidatePathPair>> groupByWorkingPath(List<CandidatePathPair> candidatePairs) {
        Map<String, List<CandidatePathPair>> groups = new LinkedHashMap<String, List<CandidatePathPair>>();
        for (CandidatePathPair pair : candidatePairs) {
            String key = pathKey(pair.workingPath());
            List<CandidatePathPair> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<CandidatePathPair>();
                groups.put(key, group);
            }
            group.add(pair);
        }
        return groups;
    }

    private String pathKey(NetworkPath path) {
        StringBuilder builder = new StringBuilder();
        for (Link link : path.links()) {
            if (builder.length() > 0) {
                builder.append('-');
            }
            builder.append(link.id());
        }
        return builder.toString();
    }

    private List<HypotheticalSlotAllocation> fullCandidateHypotheticals(
            Connection candidate,
            CfssEvaluation working,
            CfssEvaluation backup) {
        List<HypotheticalSlotAllocation> hypotheticals = new ArrayList<HypotheticalSlotAllocation>();
        for (Integer slot : working.slotIndexes()) {
            hypotheticals.add(new HypotheticalSlotAllocation(
                    candidate,
                    PathRole.WORKING,
                    candidate.workingPath(),
                    slot.intValue()));
        }
        for (Integer slot : backup.slotIndexes()) {
            hypotheticals.add(new HypotheticalSlotAllocation(
                    candidate,
                    PathRole.BACKUP,
                    candidate.backupPath(),
                    slot.intValue()));
        }
        return hypotheticals;
    }

    private double workingObjectiveValue(Connection candidate, CfssEvaluation working) {
        SpectrumState simulated = spectrum.copy();
        simulated.reserveWorking(candidate, working.slotIndexes().get(0).intValue(), working.modulationFormats());

        if (transceivers != null) {
            try {
                TransceiverState simulatedTransceivers = transceivers.copy();
                simulatedTransceivers.reserveWorking(candidate, working.slotIndexes().size());
                return ObjectiveFunction.value(simulatedTransceivers, simulated);
            } catch (RuntimeException e) {
                return Double.POSITIVE_INFINITY;
            }
        }

        int usedTransmitters = working.slotIndexes().size();
        int usedReceivers = working.slotIndexes().size();
        return ObjectiveFunction.value(usedTransmitters, usedReceivers, simulated);
    }

    private double objectiveValue(Connection candidate, CfssEvaluation working, CfssEvaluation backup) {
        SpectrumState simulated = spectrum.copy();
        simulated.reserveWorking(candidate, working.slotIndexes().get(0).intValue(), working.modulationFormats());
        simulated.reserveBackup(candidate, backup.slotIndexes().get(0).intValue(), backup.modulationFormats());

        if (transceivers != null) {
            try {
                TransceiverState simulatedTransceivers = transceivers.copy();
                simulatedTransceivers.reserveWorking(candidate, working.slotIndexes().size());
                simulatedTransceivers.reserveBackup(candidate, backup.slotIndexes().size());
                return ObjectiveFunction.value(simulatedTransceivers, simulated);
            } catch (RuntimeException e) {
                return Double.POSITIVE_INFINITY;
            }
        }

        int usedTransmitters = working.slotIndexes().size() + backup.slotIndexes().size();
        int usedReceivers = working.slotIndexes().size() + backup.slotIndexes().size();
        return ObjectiveFunction.value(usedTransmitters, usedReceivers, simulated);
    }
}
