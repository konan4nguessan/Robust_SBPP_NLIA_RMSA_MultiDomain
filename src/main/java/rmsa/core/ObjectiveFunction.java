package rmsa.core;

public final class ObjectiveFunction {
    private ObjectiveFunction() {
    }

    public static double fragmentationTerm(SpectrumState spectrum) {
        int denominator = 0;
        for (int linkId = 0; linkId < spectrum.linkCount(); linkId++) {
            int highest = spectrum.highestAllocatedSlotOnLink(linkId);
            int highestOneBased = highest + 1;
            denominator += spectrum.slotCount() - highestOneBased;
        }
        return denominator;
    }

    public static double value(int usedTransmitters, int usedReceivers, SpectrumState spectrum) {
        double denominator = fragmentationTerm(spectrum);
        if (denominator <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return (usedTransmitters + usedReceivers) / denominator;
    }

    public static double value(TransceiverState transceivers, SpectrumState spectrum) {
        if (transceivers == null) {
            throw new IllegalArgumentException("Transceiver state is required");
        }
        return value(transceivers.usedTransmitters(), transceivers.usedReceivers(), spectrum);
    }
}
