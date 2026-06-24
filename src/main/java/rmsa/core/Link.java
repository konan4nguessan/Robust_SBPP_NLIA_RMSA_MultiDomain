package rmsa.core;

import java.util.Objects;

public final class Link {
    private final int id;
    private final int origin;
    private final int destination;
    private final double lengthKm;

    public Link(int id, int origin, int destination, double lengthKm) {
        if (origin == destination) {
            throw new IllegalArgumentException("A link must connect two different nodes");
        }
        if (lengthKm <= 0) {
            throw new IllegalArgumentException("Link length must be positive");
        }
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.lengthKm = lengthKm;
    }

    public int id() {
        return id;
    }

    public int origin() {
        return origin;
    }

    public int destination() {
        return destination;
    }

    public double lengthKm() {
        return lengthKm;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Link)) return false;
        Link link = (Link) other;
        return id == link.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "L" + id + "(" + origin + "->" + destination + ")";
    }
}
