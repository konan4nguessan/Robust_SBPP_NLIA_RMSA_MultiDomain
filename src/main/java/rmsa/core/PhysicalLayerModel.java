package rmsa.core;

public final class PhysicalLayerModel {
    private static final double PLANCK_CONSTANT = 6.62607015e-34;

    private final PhysicalLayerParameters parameters;

    public PhysicalLayerModel(PhysicalLayerParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Physical layer parameters are required");
        }
        this.parameters = parameters;
    }

    public double sciPerSpanW() {
        double omega = omega();
        double bandwidth = parameters.slotBandwidthHz();
        double g = parameters.signalPowerSpectralDensity();
        double betaTerm = Math.PI * Math.PI
                * Math.abs(parameters.beta2SecondsSquaredPerKm())
                * bandwidth * bandwidth
                / parameters.alphaPerKm();
        return omega * bandwidth * g * g * g * Math.log(betaTerm);
    }

    public double xciPerSpanW(int targetSlotIndex, int interferingSlotIndex) {
        if (targetSlotIndex == interferingSlotIndex) {
            return 0.0;
        }
        double spacingHz = Math.abs(targetSlotIndex - interferingSlotIndex) * parameters.slotBandwidthHz();
        double halfBandwidth = parameters.slotBandwidthHz() / 2.0;
        double denominator = spacingHz - halfBandwidth;
        if (denominator <= 0) {
            throw new IllegalArgumentException("Slot spacing is too small for XCI calculation");
        }

        double omega = omega();
        double bandwidth = parameters.slotBandwidthHz();
        double g = parameters.signalPowerSpectralDensity();
        double mu = (spacingHz + halfBandwidth) / denominator;
        return omega * bandwidth * g * g * g * Math.log(mu);
    }

    public double aseForPathW(NetworkPath path) {
        double total = 0.0;
        for (Link link : path.links()) {
            total += aseForLinkW(link.lengthKm());
        }
        return total;
    }

    public double aseForLinkW(double linkLengthKm) {
        if (linkLengthKm <= 0) {
            throw new IllegalArgumentException("Link length must be positive");
        }
        int spans = spanCount(linkLengthKm);
        double gainLinear = Math.exp(parameters.alphaPerKm() * parameters.spanLengthKm());
        return spans
                * PLANCK_CONSTANT
                * parameters.opticalCarrierFrequencyHz()
                * parameters.slotBandwidthHz()
                * parameters.noiseFigureLinear()
                * (gainLinear - 1.0);
    }

    public SlotQoT slotQoT(NetworkPath path, double nliW) {
        double aseW = aseForPathW(path);
        double sinr = sinrLinear(parameters.signalPowerW(), aseW, nliW);
        return new SlotQoT(nliW, aseW, sinr);
    }

    public double sinrLinear(double signalPowerW, double aseW, double nliW) {
        if (signalPowerW <= 0 || aseW < 0 || nliW < 0) {
            throw new IllegalArgumentException("Invalid SINR inputs");
        }
        return signalPowerW / (aseW + nliW);
    }

    public int spanCount(double linkLengthKm) {
        return (int) Math.ceil(linkLengthKm / parameters.spanLengthKm());
    }

    private double omega() {
        double gamma = parameters.gammaPerWPerKm();
        return 3.0 * gamma * gamma
                / (2.0 * Math.PI * parameters.alphaPerKm() * Math.abs(parameters.beta2SecondsSquaredPerKm()));
    }
}
