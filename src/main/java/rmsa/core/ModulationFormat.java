package rmsa.core;

public enum ModulationFormat {
    BPSK(1, 50, 12.6),
    QPSK(2, 100, 15.6),
    QAM_8(3, 150, 19.2),
    QAM_16(4, 200, 22.4);

    private final int spectralEfficiency;
    private final int dataRateGbps;
    private final double sinrThresholdDb;

    ModulationFormat(int spectralEfficiency, int dataRateGbps, double sinrThresholdDb) {
        this.spectralEfficiency = spectralEfficiency;
        this.dataRateGbps = dataRateGbps;
        this.sinrThresholdDb = sinrThresholdDb;
    }

    public int spectralEfficiency() {
        return spectralEfficiency;
    }

    public int dataRateGbps() {
        return dataRateGbps;
    }

    public double sinrThresholdDb() {
        return sinrThresholdDb;
    }

    public static ModulationFormat highestSupportedBy(double sinrDb) {
        ModulationFormat best = null;
        for (ModulationFormat format : values()) {
            if (sinrDb >= format.sinrThresholdDb) {
                best = format;
            }
        }
        return best;
    }
}
