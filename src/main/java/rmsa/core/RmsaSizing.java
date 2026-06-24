package rmsa.core;

public final class RmsaSizing {
    private static final int BASE_RATE_GBPS_PER_BPSK_SLOT = 50;

    private RmsaSizing() {
    }

    public static int minimumSlotsForBestModulation(int requestedRateGbps) {
        if (requestedRateGbps <= 0) {
            throw new IllegalArgumentException("Requested rate must be positive");
        }
        int bestRate = BASE_RATE_GBPS_PER_BPSK_SLOT * highestSpectralEfficiency();
        return (requestedRateGbps + bestRate - 1) / bestRate;
    }

    public static int highestSpectralEfficiency() {
        int best = 0;
        for (ModulationFormat format : ModulationFormat.values()) {
            best = Math.max(best, format.spectralEfficiency());
        }
        return best;
    }
}
