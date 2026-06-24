package rmsa.net2plan;

import rmsa.core.NetworkPath;

public final class CandidatePathPair {
    private final NetworkPath workingPath;
    private final NetworkPath backupPath;

    public CandidatePathPair(NetworkPath workingPath, NetworkPath backupPath) {
        if (workingPath == null || backupPath == null) {
            throw new IllegalArgumentException("Working and backup paths are required");
        }
        this.workingPath = workingPath;
        this.backupPath = backupPath;
    }

    public NetworkPath workingPath() {
        return workingPath;
    }

    public NetworkPath backupPath() {
        return backupPath;
    }
}
