package rmsa.core;

public final class SlotQoT {
    private final double nliW;
    private final double aseW;
    private final double sinrLinear;
    private final double sinrDb;

    public SlotQoT(double nliW, double aseW, double sinrLinear) {
        if (nliW < 0 || aseW < 0 || sinrLinear <= 0) {
            throw new IllegalArgumentException("QoT values must be physically valid");
        }
        this.nliW = nliW;
        this.aseW = aseW;
        this.sinrLinear = sinrLinear;
        this.sinrDb = 10.0 * Math.log10(sinrLinear);
    }

    public double nliW() {
        return nliW;
    }

    public double aseW() {
        return aseW;
    }

    public double sinrLinear() {
        return sinrLinear;
    }

    public double sinrDb() {
        return sinrDb;
    }

    public ModulationFormat highestSupportedModulation() {
        return ModulationFormat.highestSupportedBy(sinrDb);
    }
}
