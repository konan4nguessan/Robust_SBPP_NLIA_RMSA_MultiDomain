package rmsa.core;

public final class PhysicalLayerCalibrationTest {
    public static void main(String[] args) {
        PhysicalLayerParameters parameters = PhysicalLayerParameters.paperLikeDefaults();

        assertAlmostEquals(1.33, parameters.gammaPerWPerKm(), 1e-12, "paper gamma should be locked");
        assertAlmostEquals(-21.7e-24, parameters.beta2SecondsSquaredPerKm(), 1e-36, "paper beta2 should be locked");
        assertAlmostEquals(37.5e9, parameters.slotBandwidthHz(), 1e-3, "paper slot bandwidth should be locked");
        assertAlmostEquals(0.2, parameters.alphaDbPerKm(), 1e-12, "default alpha should be explicit");
        assertAlmostEquals(0.0, parameters.signalPowerDbm(), 1e-12, "default signal power should be explicit");
        assertAlmostEquals(100.0, parameters.spanLengthKm(), 1e-12, "default span length should be explicit");
        assertAlmostEquals(5.0, parameters.noiseFigureDb(), 1e-12, "default noise figure should be explicit");
        assertAlmostEquals(193.414e12, parameters.opticalCarrierFrequencyHz(), 1e-3, "default optical carrier frequency should be explicit");

        assertAlmostEquals(0.2, PhysicalLayerParameters.linearPerKmToDbPerKm(
                PhysicalLayerParameters.dbPerKmToLinearPerKm(0.2)), 1e-12, "attenuation conversion should round trip");
        assertAlmostEquals(0.0, PhysicalLayerParameters.wToDbm(
                PhysicalLayerParameters.dbmToW(0.0)), 1e-12, "power conversion should round trip");

        System.out.println("Physical layer calibration test passed");
    }

    private static void assertAlmostEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
