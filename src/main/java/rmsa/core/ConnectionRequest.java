package rmsa.core;

public final class ConnectionRequest {
    private final String id;
    private final int source;
    private final int destination;
    private final int dataRateGbps;

    public ConnectionRequest(String id, int source, int destination, int dataRateGbps) {
        if (source == destination) {
            throw new IllegalArgumentException("Source and destination must be different");
        }
        if (dataRateGbps <= 0) {
            throw new IllegalArgumentException("Data rate must be positive");
        }
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.dataRateGbps = dataRateGbps;
    }

    public String id() {
        return id;
    }

    public int source() {
        return source;
    }

    public int destination() {
        return destination;
    }

    public int dataRateGbps() {
        return dataRateGbps;
    }
}
