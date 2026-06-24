package rmsa.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContiguousSlotBlock {
    private final int startSlot;
    private final int width;

    public ContiguousSlotBlock(int startSlot, int width) {
        if (startSlot < 0) {
            throw new IllegalArgumentException("Start slot must be non-negative");
        }
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be positive");
        }
        this.startSlot = startSlot;
        this.width = width;
    }

    public int startSlot() {
        return startSlot;
    }

    public int width() {
        return width;
    }

    public int endSlotInclusive() {
        return startSlot + width - 1;
    }

    public List<Integer> slotIndexes() {
        List<Integer> indexes = new ArrayList<Integer>();
        for (int slot = startSlot; slot <= endSlotInclusive(); slot++) {
            indexes.add(slot);
        }
        return Collections.unmodifiableList(indexes);
    }

    @Override
    public String toString() {
        return "[" + startSlot + ".." + endSlotInclusive() + "]";
    }
}
