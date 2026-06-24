package rmsa.net2plan;

import rmsa.core.CfssEvaluation;
import rmsa.core.Connection;

public final class RmsaProvisioningDecision {
    private final boolean feasible;
    private final String rejectionReason;
    private final Connection connection;
    private final CfssEvaluation workingEvaluation;
    private final CfssEvaluation backupEvaluation;
    private final double objectiveValue;

    private RmsaProvisioningDecision(
            boolean feasible,
            String rejectionReason,
            Connection connection,
            CfssEvaluation workingEvaluation,
            CfssEvaluation backupEvaluation,
            double objectiveValue) {
        this.feasible = feasible;
        this.rejectionReason = rejectionReason;
        this.connection = connection;
        this.workingEvaluation = workingEvaluation;
        this.backupEvaluation = backupEvaluation;
        this.objectiveValue = objectiveValue;
    }

    public static RmsaProvisioningDecision feasible(
            Connection connection,
            CfssEvaluation workingEvaluation,
            CfssEvaluation backupEvaluation,
            double objectiveValue) {
        return new RmsaProvisioningDecision(true, "", connection, workingEvaluation, backupEvaluation, objectiveValue);
    }

    public static RmsaProvisioningDecision rejected(String reason) {
        return new RmsaProvisioningDecision(false, reason, null, null, null, Double.POSITIVE_INFINITY);
    }

    public boolean isFeasible() {
        return feasible;
    }

    public String rejectionReason() {
        return rejectionReason;
    }

    public Connection connection() {
        return connection;
    }

    public CfssEvaluation workingEvaluation() {
        return workingEvaluation;
    }

    public CfssEvaluation backupEvaluation() {
        return backupEvaluation;
    }

    public double objectiveValue() {
        return objectiveValue;
    }
}
