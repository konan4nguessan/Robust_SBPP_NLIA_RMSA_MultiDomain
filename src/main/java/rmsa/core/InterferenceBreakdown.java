package rmsa.core;

public final class InterferenceBreakdown {
    private final double sciW;
    private final double xciW;

    public InterferenceBreakdown(double sciW, double xciW) {
        if (sciW < 0 || xciW < 0) {
            throw new IllegalArgumentException("Interference values must be non-negative");
        }
        this.sciW = sciW;
        this.xciW = xciW;
    }

    public double sciW() {
        return sciW;
    }

    public double xciW() {
        return xciW;
    }

    public double totalNliW() {
        return sciW + xciW;
    }

    public InterferenceBreakdown plus(InterferenceBreakdown other) {
        return new InterferenceBreakdown(sciW + other.sciW, xciW + other.xciW);
    }
}
