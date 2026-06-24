package rmsa.core;

public final class PhysicalLayerParameters {
    public static final double PAPER_GAMMA_PER_W_PER_KM = 1.33;
    public static final double PAPER_BETA2_SECONDS_SQUARED_PER_KM = -21.7e-24;
    public static final double PAPER_SLOT_BANDWIDTH_HZ = 37.5e9;
    public static final double DEFAULT_ALPHA_DB_PER_KM = 0.2;
    public static final double DEFAULT_SIGNAL_POWER_DBM = 0.0;
    public static final double DEFAULT_SPAN_LENGTH_KM = 100.0;
    public static final double DEFAULT_NOISE_FIGURE_DB = 5.0;
    public static final double DEFAULT_OPTICAL_CARRIER_FREQUENCY_HZ = 193.414e12;

    private final double gammaPerWPerKm;
    private final double beta2SecondsSquaredPerKm;
    private final double alphaPerKm;
    private final double slotBandwidthHz;
    private final double signalPowerW;
    private final double spanLengthKm;
    private final double noiseFigureLinear;
    private final double opticalCarrierFrequencyHz;

    public PhysicalLayerParameters(
            double gammaPerWPerKm,
            double beta2SecondsSquaredPerKm,
            double alphaPerKm,
            double slotBandwidthHz,
            double signalPowerW,
            double spanLengthKm,
            double noiseFigureLinear,
            double opticalCarrierFrequencyHz) {
        if (gammaPerWPerKm <= 0 || beta2SecondsSquaredPerKm == 0 || alphaPerKm <= 0
                || slotBandwidthHz <= 0 || signalPowerW <= 0 || spanLengthKm <= 0
                || noiseFigureLinear <= 0 || opticalCarrierFrequencyHz <= 0) {
            throw new IllegalArgumentException("Physical parameters must be positive, except beta2 which must be non-zero");
        }
        this.gammaPerWPerKm = gammaPerWPerKm;
        this.beta2SecondsSquaredPerKm = beta2SecondsSquaredPerKm;
        this.alphaPerKm = alphaPerKm;
        this.slotBandwidthHz = slotBandwidthHz;
        this.signalPowerW = signalPowerW;
        this.spanLengthKm = spanLengthKm;
        this.noiseFigureLinear = noiseFigureLinear;
        this.opticalCarrierFrequencyHz = opticalCarrierFrequencyHz;
    }

    public static PhysicalLayerParameters paperLikeDefaults() {
        return new PhysicalLayerParameters(
                PAPER_GAMMA_PER_W_PER_KM,
                PAPER_BETA2_SECONDS_SQUARED_PER_KM,
                dbPerKmToLinearPerKm(DEFAULT_ALPHA_DB_PER_KM),
                PAPER_SLOT_BANDWIDTH_HZ,
                dbmToW(DEFAULT_SIGNAL_POWER_DBM),
                DEFAULT_SPAN_LENGTH_KM,
                dbToLinear(DEFAULT_NOISE_FIGURE_DB),
                DEFAULT_OPTICAL_CARRIER_FREQUENCY_HZ);
    }

    public static double dbToLinear(double db) {
        return Math.pow(10.0, db / 10.0);
    }

    public static double linearToDb(double linear) {
        if (linear <= 0) {
            throw new IllegalArgumentException("Linear value must be positive");
        }
        return 10.0 * Math.log10(linear);
    }

    public static double dbmToW(double dbm) {
        return Math.pow(10.0, dbm / 10.0) * 1.0e-3;
    }

    public static double wToDbm(double watt) {
        if (watt <= 0) {
            throw new IllegalArgumentException("Power must be positive");
        }
        return 10.0 * Math.log10(watt / 1.0e-3);
    }

    public static double dbPerKmToLinearPerKm(double dbPerKm) {
        return dbPerKm / (10.0 * Math.log10(Math.E));
    }

    public static double linearPerKmToDbPerKm(double linearPerKm) {
        if (linearPerKm <= 0) {
            throw new IllegalArgumentException("Attenuation must be positive");
        }
        return linearPerKm * 10.0 * Math.log10(Math.E);
    }

    public double gammaPerWPerKm() {
        return gammaPerWPerKm;
    }

    public double beta2SecondsSquaredPerKm() {
        return beta2SecondsSquaredPerKm;
    }

    public double alphaPerKm() {
        return alphaPerKm;
    }

    public double alphaDbPerKm() {
        return linearPerKmToDbPerKm(alphaPerKm);
    }

    public double slotBandwidthHz() {
        return slotBandwidthHz;
    }

    public double signalPowerW() {
        return signalPowerW;
    }

    public double signalPowerDbm() {
        return wToDbm(signalPowerW);
    }

    public double signalPowerSpectralDensity() {
        return signalPowerW / slotBandwidthHz;
    }

    public double spanLengthKm() {
        return spanLengthKm;
    }

    public double noiseFigureLinear() {
        return noiseFigureLinear;
    }

    public double noiseFigureDb() {
        return linearToDb(noiseFigureLinear);
    }

    public double opticalCarrierFrequencyHz() {
        return opticalCarrierFrequencyHz;
    }
}
