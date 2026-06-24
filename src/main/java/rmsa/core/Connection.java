package rmsa.core;

public final class Connection {
    private final String id;
    private final ConnectionRequest request;
    private final NetworkPath workingPath;
    private final NetworkPath backupPath;

    public Connection(String id, ConnectionRequest request, NetworkPath workingPath, NetworkPath backupPath) {
        if (!workingPath.isLinkDisjointWith(backupPath)) {
            throw new IllegalArgumentException("Working and backup paths must be link-disjoint");
        }
        this.id = id;
        this.request = request;
        this.workingPath = workingPath;
        this.backupPath = backupPath;
    }

    public String id() {
        return id;
    }

    public ConnectionRequest request() {
        return request;
    }

    public NetworkPath workingPath() {
        return workingPath;
    }

    public NetworkPath backupPath() {
        return backupPath;
    }

    public NetworkPath pathForRole(PathRole role) {
        if (role == PathRole.WORKING) {
            return workingPath;
        }
        if (role == PathRole.BACKUP) {
            return backupPath;
        }
        throw new IllegalArgumentException("Unknown path role");
    }

    @Override
    public String toString() {
        return id;
    }
}
