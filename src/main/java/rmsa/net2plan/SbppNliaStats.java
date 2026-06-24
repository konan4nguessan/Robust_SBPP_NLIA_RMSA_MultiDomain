package rmsa.net2plan;

import java.util.ArrayList;
import java.util.List;

import rmsa.core.ExistingQoTCheck;
import rmsa.core.PathRole;
import rmsa.core.ScientificComputationListener;
import rmsa.core.SlotAllocation;

public final class SbppNliaStats implements ScientificComputationListener {
    private static final int MAX_RECENT_EXISTING_QOT_VIOLATIONS = 20;

    private long requests;
    private long acceptedRequests;
    private long blockedRequests;
    private long candidatePairsGenerated;
    private long candidatePairsTested;
    private long candidatePairsSelectedForValidation;
    private long fastQotAvoidedFullValidations;
    private long robustQoTValidations;
    private long existingQoTValidations;
    private long failureScenariosEvaluated;
    private long nliSinrComputations;
    private long nliCacheHits;
    private long nliCacheMisses;
    private long workingBlocksEvaluated;
    private long workingBlocksFeasible;
    private long workingBlocksRejected;
    private long backupBlocksEvaluated;
    private long backupBlocksFeasible;
    private long backupBlocksRejected;
    private long objectiveEvaluations;
    private long feasibleCouples;
    private long blockedNoInterDomainWindow;
    private long blockedNoWorkingWindow;
    private long blockedNoBackupWindow;
    private long blockedWindowPairIncompatible;
    private long blockedWorkingSelfQoT;
    private long blockedWorkingExistingQoT;
    private long blockedWorkingExistingQoTOnExistingWorking;
    private long blockedWorkingExistingQoTOnExistingBackup;
    private long blockedWorkingExistingQoTNoFailure;
    private long blockedWorkingExistingQoTFailureScenario;
    private long blockedWorkingFailureScenarioQoT;
    private long blockedBackupSelfQoT;
    private long blockedBackupExistingQoT;
    private long blockedBackupExistingQoTOnExistingWorking;
    private long blockedBackupExistingQoTOnExistingBackup;
    private long blockedBackupExistingQoTNoFailure;
    private long blockedBackupExistingQoTFailureScenario;
    private long blockedBackupFailureScenarioQoT;
    private long blockedCoupledWorkingBackupQoT;
    private long blockedRobustScenarioSetQoT;
    private long blockedTransceiver;
    private long blockedAfterPairLimit;
    private long blockedNoFeasiblePathPair;
    private long existingQoTViolationHeadroomSamples;
    private double existingQoTViolationHeadroomDbSum;
    private double existingQoTViolationWorstHeadroomDb;
    private long workingExistingQoTViolationHeadroomSamples;
    private double workingExistingQoTViolationHeadroomDbSum;
    private double workingExistingQoTViolationWorstHeadroomDb;
    private long backupExistingQoTViolationHeadroomSamples;
    private double backupExistingQoTViolationHeadroomDbSum;
    private double backupExistingQoTViolationWorstHeadroomDb;
    private long existingQoTViolationsWithin0p01Db;
    private long existingQoTViolationsWithin0p02Db;
    private long existingQoTViolationsWithin0p05Db;
    private long existingQoTViolationsWithin0p10Db;
    private long existingQoTViolationsOver0p10Db;
    private long requestCpuNanosTotal;
    private long lastRequestCpuNanos;
    private long requestStartNanos;
    private final List<String> recentExistingQoTViolations = new ArrayList<String>();

    public void reset() {
        requests = 0L;
        acceptedRequests = 0L;
        blockedRequests = 0L;
        candidatePairsGenerated = 0L;
        candidatePairsTested = 0L;
        candidatePairsSelectedForValidation = 0L;
        fastQotAvoidedFullValidations = 0L;
        robustQoTValidations = 0L;
        existingQoTValidations = 0L;
        failureScenariosEvaluated = 0L;
        nliSinrComputations = 0L;
        nliCacheHits = 0L;
        nliCacheMisses = 0L;
        workingBlocksEvaluated = 0L;
        workingBlocksFeasible = 0L;
        workingBlocksRejected = 0L;
        backupBlocksEvaluated = 0L;
        backupBlocksFeasible = 0L;
        backupBlocksRejected = 0L;
        objectiveEvaluations = 0L;
        feasibleCouples = 0L;
        blockedNoInterDomainWindow = 0L;
        blockedNoWorkingWindow = 0L;
        blockedNoBackupWindow = 0L;
        blockedWindowPairIncompatible = 0L;
        blockedWorkingSelfQoT = 0L;
        blockedWorkingExistingQoT = 0L;
        blockedWorkingExistingQoTOnExistingWorking = 0L;
        blockedWorkingExistingQoTOnExistingBackup = 0L;
        blockedWorkingExistingQoTNoFailure = 0L;
        blockedWorkingExistingQoTFailureScenario = 0L;
        blockedWorkingFailureScenarioQoT = 0L;
        blockedBackupSelfQoT = 0L;
        blockedBackupExistingQoT = 0L;
        blockedBackupExistingQoTOnExistingWorking = 0L;
        blockedBackupExistingQoTOnExistingBackup = 0L;
        blockedBackupExistingQoTNoFailure = 0L;
        blockedBackupExistingQoTFailureScenario = 0L;
        blockedBackupFailureScenarioQoT = 0L;
        blockedCoupledWorkingBackupQoT = 0L;
        blockedRobustScenarioSetQoT = 0L;
        blockedTransceiver = 0L;
        blockedAfterPairLimit = 0L;
        blockedNoFeasiblePathPair = 0L;
        existingQoTViolationHeadroomSamples = 0L;
        existingQoTViolationHeadroomDbSum = 0.0D;
        existingQoTViolationWorstHeadroomDb = 0.0D;
        workingExistingQoTViolationHeadroomSamples = 0L;
        workingExistingQoTViolationHeadroomDbSum = 0.0D;
        workingExistingQoTViolationWorstHeadroomDb = 0.0D;
        backupExistingQoTViolationHeadroomSamples = 0L;
        backupExistingQoTViolationHeadroomDbSum = 0.0D;
        backupExistingQoTViolationWorstHeadroomDb = 0.0D;
        existingQoTViolationsWithin0p01Db = 0L;
        existingQoTViolationsWithin0p02Db = 0L;
        existingQoTViolationsWithin0p05Db = 0L;
        existingQoTViolationsWithin0p10Db = 0L;
        existingQoTViolationsOver0p10Db = 0L;
        requestCpuNanosTotal = 0L;
        lastRequestCpuNanos = 0L;
        requestStartNanos = 0L;
        recentExistingQoTViolations.clear();
    }


    public void startRequest() {
        requests++;
        requestStartNanos = System.nanoTime();
    }

    public void endRequest(boolean accepted) {
        lastRequestCpuNanos = requestStartNanos == 0L ? 0L : System.nanoTime() - requestStartNanos;
        requestCpuNanosTotal += lastRequestCpuNanos;
        requestStartNanos = 0L;
        if (accepted) {
            acceptedRequests++;
        } else {
            blockedRequests++;
        }
    }

    public void recordCandidatePairsGenerated(int count) {
        if (count > 0) candidatePairsGenerated += count;
    }

    public void recordCandidatePairTested() {
        candidatePairsTested++;
    }

    public void recordCandidatePairsSelectedForValidation(int count) {
        if (count > 0) candidatePairsSelectedForValidation += count;
    }

    public void recordFastQotAvoidedFullValidation() {
        fastQotAvoidedFullValidations++;
    }
    @Override
    public void recordRobustQoTValidation(int scenarioCount) {
        robustQoTValidations++;
        if (scenarioCount > 0) failureScenariosEvaluated += scenarioCount;
    }

    @Override
    public void recordExistingQoTValidation(int scenarioCount) {
        existingQoTValidations++;
        if (scenarioCount > 0) failureScenariosEvaluated += scenarioCount;
    }

    @Override
    public void recordNliSinrComputation(boolean cacheHit) {
        nliSinrComputations++;
        if (cacheHit) {
            nliCacheHits++;
        } else {
            nliCacheMisses++;
        }
    }

    public void recordWorkingEvaluation(boolean feasible) {
        workingBlocksEvaluated++;
        if (feasible) workingBlocksFeasible++; else workingBlocksRejected++;
    }

    public void recordBackupEvaluation(boolean feasible) {
        backupBlocksEvaluated++;
        if (feasible) backupBlocksFeasible++; else backupBlocksRejected++;
    }

    public void recordObjectiveEvaluation() {
        objectiveEvaluations++;
    }

    public void recordFeasibleCouple() {
        feasibleCouples++;
    }

    public void recordBlockedNoInterDomainWindow() {
        blockedNoInterDomainWindow++;
    }

    public void recordBlockedNoWorkingWindow() {
        blockedNoWorkingWindow++;
    }

    public void recordBlockedNoBackupWindow() {
        blockedNoBackupWindow++;
    }

    public void recordBlockedWindowPairIncompatible() {
        blockedWindowPairIncompatible++;
    }

    public void recordBlockedWorkingSelfQoT() {
        blockedWorkingSelfQoT++;
    }

    public void recordBlockedWorkingExistingQoT() {
        blockedWorkingExistingQoT++;
    }

    public void recordBlockedWorkingExistingQoT(ExistingQoTCheck check, int candidateSlotStart) {
        blockedWorkingExistingQoT++;
        recordDetailedExistingQoTViolation(PathRole.WORKING, check, candidateSlotStart);
    }

    public void recordBlockedWorkingFailureScenarioQoT() {
        blockedWorkingFailureScenarioQoT++;
    }

    public void recordBlockedBackupSelfQoT() {
        blockedBackupSelfQoT++;
    }

    public void recordBlockedBackupExistingQoT() {
        blockedBackupExistingQoT++;
    }

    public void recordBlockedBackupExistingQoT(ExistingQoTCheck check, int candidateSlotStart) {
        blockedBackupExistingQoT++;
        recordDetailedExistingQoTViolation(PathRole.BACKUP, check, candidateSlotStart);
    }

    public void recordBlockedBackupFailureScenarioQoT() {
        blockedBackupFailureScenarioQoT++;
    }

    public void recordBlockedCoupledWorkingBackupQoT() {
        blockedCoupledWorkingBackupQoT++;
    }

    public void recordBlockedRobustScenarioSetQoT() {
        blockedRobustScenarioSetQoT++;
    }

    public void recordBlockedTransceiver() {
        blockedTransceiver++;
    }

    public void recordBlockedAfterPairLimit() {
        blockedAfterPairLimit++;
    }

    public void recordBlockedNoFeasiblePathPair() {
        blockedNoFeasiblePathPair++;
    }

    public long requests() {
        return requests;
    }

    public String report() {
        StringBuilder builder = new StringBuilder();
        builder.append("coreProfile=PAPER_ORDERED_SBPP_NLIA").append('\n');
        builder.append("optimizationRequests=").append(requests).append('\n');
        builder.append("optimizationAcceptedRequests=").append(acceptedRequests).append('\n');
        builder.append("optimizationBlockedRequests=").append(blockedRequests).append('\n');
        builder.append("candidatePairsGenerated=").append(candidatePairsGenerated).append('\n');
        builder.append("candidatePairsTested=").append(candidatePairsTested).append('\n');
        builder.append("candidatePairsSentToCore=").append(candidatePairsSelectedForValidation).append('\n');
        builder.append("fastQotAvoidedFullValidations=").append(fastQotAvoidedFullValidations).append('\n');
        builder.append("robustQoTValidations=").append(robustQoTValidations).append('\n');
        builder.append("existingQoTValidations=").append(existingQoTValidations).append('\n');
        builder.append("failureScenariosEvaluated=").append(failureScenariosEvaluated).append('\n');
        builder.append("nliSinrComputations=").append(nliSinrComputations).append('\n');
        builder.append("nliCacheHits=").append(nliCacheHits).append('\n');
        builder.append("nliCacheMisses=").append(nliCacheMisses).append('\n');
        builder.append("workingBlocksEvaluated=").append(workingBlocksEvaluated).append('\n');
        builder.append("workingBlocksFeasible=").append(workingBlocksFeasible).append('\n');
        builder.append("workingBlocksRejected=").append(workingBlocksRejected).append('\n');
        builder.append("backupBlocksEvaluated=").append(backupBlocksEvaluated).append('\n');
        builder.append("backupBlocksFeasible=").append(backupBlocksFeasible).append('\n');
        builder.append("backupBlocksRejected=").append(backupBlocksRejected).append('\n');
        builder.append("objectiveEvaluations=").append(objectiveEvaluations).append('\n');
        builder.append("feasibleCouples=").append(feasibleCouples).append('\n');
        builder.append("blockedNoInterDomainWindow=").append(blockedNoInterDomainWindow).append('\n');
        builder.append("blockedNoWorkingWindow=").append(blockedNoWorkingWindow).append('\n');
        builder.append("blockedNoBackupWindow=").append(blockedNoBackupWindow).append('\n');
        builder.append("blockedWindowPairIncompatible=").append(blockedWindowPairIncompatible).append('\n');
        builder.append("blockedWorkingSelfQoT=").append(blockedWorkingSelfQoT).append('\n');
        builder.append("blockedWorkingExistingQoT=").append(blockedWorkingExistingQoT).append('\n');
        builder.append("blockedWorkingExistingQoTOnExistingWorking=").append(blockedWorkingExistingQoTOnExistingWorking).append('\n');
        builder.append("blockedWorkingExistingQoTOnExistingBackup=").append(blockedWorkingExistingQoTOnExistingBackup).append('\n');
        builder.append("blockedWorkingExistingQoTNoFailure=").append(blockedWorkingExistingQoTNoFailure).append('\n');
        builder.append("blockedWorkingExistingQoTFailureScenario=").append(blockedWorkingExistingQoTFailureScenario).append('\n');
        builder.append("blockedWorkingFailureScenarioQoT=").append(blockedWorkingFailureScenarioQoT).append('\n');
        builder.append("blockedBackupSelfQoT=").append(blockedBackupSelfQoT).append('\n');
        builder.append("blockedBackupExistingQoT=").append(blockedBackupExistingQoT).append('\n');
        builder.append("blockedBackupExistingQoTOnExistingWorking=").append(blockedBackupExistingQoTOnExistingWorking).append('\n');
        builder.append("blockedBackupExistingQoTOnExistingBackup=").append(blockedBackupExistingQoTOnExistingBackup).append('\n');
        builder.append("blockedBackupExistingQoTNoFailure=").append(blockedBackupExistingQoTNoFailure).append('\n');
        builder.append("blockedBackupExistingQoTFailureScenario=").append(blockedBackupExistingQoTFailureScenario).append('\n');
        builder.append("blockedBackupFailureScenarioQoT=").append(blockedBackupFailureScenarioQoT).append('\n');
        builder.append("blockedCoupledWorkingBackupQoT=").append(blockedCoupledWorkingBackupQoT).append('\n');
        builder.append("blockedRobustScenarioSetQoT=").append(blockedRobustScenarioSetQoT).append('\n');
        builder.append("blockedTransceiver=").append(blockedTransceiver).append('\n');
        builder.append("blockedAfterPairLimit=").append(blockedAfterPairLimit).append('\n');
        builder.append("blockedNoFeasiblePathPair=").append(blockedNoFeasiblePathPair).append('\n');
        builder.append("blockedCandidateQoT=").append(blockedCandidateQoT()).append('\n');
        builder.append("blockedSpectrumWindow=").append(blockedSpectrumWindow()).append('\n');
        builder.append("blockedResourceOrSearchLimit=").append(blockedResourceOrSearchLimit()).append('\n');
        builder.append("existingQoTViolationAverageHeadroomDb=").append(averageExistingQoTViolationHeadroomDb()).append('\n');
        builder.append("existingQoTViolationWorstHeadroomDb=").append(existingQoTViolationWorstHeadroomDb).append('\n');
        builder.append("workingExistingQoTViolationAverageHeadroomDb=").append(averageWorkingExistingQoTViolationHeadroomDb()).append('\n');
        builder.append("workingExistingQoTViolationWorstHeadroomDb=").append(workingExistingQoTViolationWorstHeadroomDb).append('\n');
        builder.append("backupExistingQoTViolationAverageHeadroomDb=").append(averageBackupExistingQoTViolationHeadroomDb()).append('\n');
        builder.append("backupExistingQoTViolationWorstHeadroomDb=").append(backupExistingQoTViolationWorstHeadroomDb).append('\n');
        builder.append("existingQoTViolationsWithin0p01Db=").append(existingQoTViolationsWithin0p01Db).append('\n');
        builder.append("existingQoTViolationsWithin0p02Db=").append(existingQoTViolationsWithin0p02Db).append('\n');
        builder.append("existingQoTViolationsWithin0p05Db=").append(existingQoTViolationsWithin0p05Db).append('\n');
        builder.append("existingQoTViolationsWithin0p10Db=").append(existingQoTViolationsWithin0p10Db).append('\n');
        builder.append("existingQoTViolationsOver0p10Db=").append(existingQoTViolationsOver0p10Db).append('\n');
        builder.append("lastRequestCpuMs=").append(nanosToMillis(lastRequestCpuNanos)).append('\n');
        builder.append("averageRequestCpuMs=").append(averageRequestCpuMs()).append('\n');
        builder.append("totalRequestCpuMs=").append(nanosToMillis(requestCpuNanosTotal)).append('\n');
        for (int i = 0; i < recentExistingQoTViolations.size(); i++) {
            builder.append("recentExistingQoTViolation").append(i).append('=')
                    .append(recentExistingQoTViolations.get(i)).append('\n');
        }
        return builder.toString();
    }

    private void recordDetailedExistingQoTViolation(
            PathRole candidateRole,
            ExistingQoTCheck check,
            int candidateSlotStart) {
        if (check == null || check.isFeasible()
                || check.violatingAllocation() == null
                || check.violatingQoT() == null) {
            return;
        }
        SlotAllocation allocation = check.violatingAllocation();
        boolean existingWorking = allocation.role() == PathRole.WORKING;
        boolean noFailure = check.violatingQoT().worstCaseScenario().isNoFailure();
        if (candidateRole == PathRole.WORKING) {
            if (existingWorking) {
                blockedWorkingExistingQoTOnExistingWorking++;
            } else {
                blockedWorkingExistingQoTOnExistingBackup++;
            }
            if (noFailure) {
                blockedWorkingExistingQoTNoFailure++;
            } else {
                blockedWorkingExistingQoTFailureScenario++;
            }
        } else if (candidateRole == PathRole.BACKUP) {
            if (existingWorking) {
                blockedBackupExistingQoTOnExistingWorking++;
            } else {
                blockedBackupExistingQoTOnExistingBackup++;
            }
            if (noFailure) {
                blockedBackupExistingQoTNoFailure++;
            } else {
                blockedBackupExistingQoTFailureScenario++;
            }
        }
        addRecentExistingQoTViolation(candidateRole, candidateSlotStart, check);
        recordExistingQoTViolationHeadroom(candidateRole, check);
    }

    private void recordExistingQoTViolationHeadroom(PathRole candidateRole, ExistingQoTCheck check) {
        SlotAllocation allocation = check.violatingAllocation();
        double headroomDb = check.violatingQoT().sinrMinDb()
                - allocation.modulationFormat().sinrThresholdDb();

        existingQoTViolationHeadroomSamples++;
        existingQoTViolationHeadroomDbSum += headroomDb;
        if (existingQoTViolationHeadroomSamples == 1L
                || headroomDb < existingQoTViolationWorstHeadroomDb) {
            existingQoTViolationWorstHeadroomDb = headroomDb;
        }

        if (candidateRole == PathRole.WORKING) {
            workingExistingQoTViolationHeadroomSamples++;
            workingExistingQoTViolationHeadroomDbSum += headroomDb;
            if (workingExistingQoTViolationHeadroomSamples == 1L
                    || headroomDb < workingExistingQoTViolationWorstHeadroomDb) {
                workingExistingQoTViolationWorstHeadroomDb = headroomDb;
            }
        } else if (candidateRole == PathRole.BACKUP) {
            backupExistingQoTViolationHeadroomSamples++;
            backupExistingQoTViolationHeadroomDbSum += headroomDb;
            if (backupExistingQoTViolationHeadroomSamples == 1L
                    || headroomDb < backupExistingQoTViolationWorstHeadroomDb) {
                backupExistingQoTViolationWorstHeadroomDb = headroomDb;
            }
        }
        recordExistingQoTViolationHeadroomBucket(headroomDb);
    }

    private void recordExistingQoTViolationHeadroomBucket(double headroomDb) {
        double deficitDb = Math.max(0.0D, -headroomDb);
        if (deficitDb <= 0.01D) {
            existingQoTViolationsWithin0p01Db++;
        } else if (deficitDb <= 0.02D) {
            existingQoTViolationsWithin0p02Db++;
        } else if (deficitDb <= 0.05D) {
            existingQoTViolationsWithin0p05Db++;
        } else if (deficitDb <= 0.10D) {
            existingQoTViolationsWithin0p10Db++;
        } else {
            existingQoTViolationsOver0p10Db++;
        }
    }

    private void addRecentExistingQoTViolation(
            PathRole candidateRole,
            int candidateSlotStart,
            ExistingQoTCheck check) {
        SlotAllocation allocation = check.violatingAllocation();
        String line = "violatingConnectionId=" + allocation.connection().id()
                + " violatingRole=" + allocation.role()
                + " violatingSlot=" + allocation.slotIndex()
                + " violatingModulation=" + allocation.modulationFormat()
                + " sinrMinDb=" + check.violatingQoT().sinrMinDb()
                + " requiredSinrDb=" + allocation.modulationFormat().sinrThresholdDb()
                + " headroomDb=" + (check.violatingQoT().sinrMinDb() - allocation.modulationFormat().sinrThresholdDb())
                + " worstScenario=" + check.violatingQoT().worstCaseScenario()
                + " candidateRole=" + candidateRole
                + " candidateSlotStart=" + candidateSlotStart;
        if (recentExistingQoTViolations.size() >= MAX_RECENT_EXISTING_QOT_VIOLATIONS) {
            recentExistingQoTViolations.remove(0);
        }
        recentExistingQoTViolations.add(line);
    }

    private double averageRequestCpuMs() {
        return requests <= 0L ? 0.0D : nanosToMillis(requestCpuNanosTotal) / (double) requests;
    }

    private long blockedCandidateQoT() {
        return blockedWorkingSelfQoT
                + blockedWorkingExistingQoT
                + blockedWorkingFailureScenarioQoT
                + blockedBackupSelfQoT
                + blockedBackupExistingQoT
                + blockedBackupFailureScenarioQoT
                + blockedCoupledWorkingBackupQoT
                + blockedRobustScenarioSetQoT;
    }

    private long blockedSpectrumWindow() {
        return blockedNoInterDomainWindow
                + blockedNoWorkingWindow
                + blockedNoBackupWindow
                + blockedWindowPairIncompatible
                + blockedNoFeasiblePathPair;
    }

    private long blockedResourceOrSearchLimit() {
        return blockedTransceiver + blockedAfterPairLimit;
    }

    private double averageExistingQoTViolationHeadroomDb() {
        return existingQoTViolationHeadroomSamples <= 0L
                ? 0.0D
                : existingQoTViolationHeadroomDbSum / (double) existingQoTViolationHeadroomSamples;
    }

    private double averageWorkingExistingQoTViolationHeadroomDb() {
        return workingExistingQoTViolationHeadroomSamples <= 0L
                ? 0.0D
                : workingExistingQoTViolationHeadroomDbSum / (double) workingExistingQoTViolationHeadroomSamples;
    }

    private double averageBackupExistingQoTViolationHeadroomDb() {
        return backupExistingQoTViolationHeadroomSamples <= 0L
                ? 0.0D
                : backupExistingQoTViolationHeadroomDbSum / (double) backupExistingQoTViolationHeadroomSamples;
    }

    private static double nanosToMillis(long nanos) {
        return (double) nanos / 1000000.0D;
    }
}
