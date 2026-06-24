package rmsa.core;

import java.util.Arrays;

public final class TransceiverStateTest {
    public static void main(String[] args) {
        Link l01 = new Link(0, 0, 1, 100);
        Link l13 = new Link(1, 1, 3, 100);
        Link l02 = new Link(2, 0, 2, 100);
        Link l23 = new Link(3, 2, 3, 100);
        Link l04 = new Link(4, 0, 4, 100);
        Link l43 = new Link(5, 4, 3, 100);

        NetworkPath w1 = new NetworkPath(Arrays.asList(l01, l13));
        NetworkPath b1 = new NetworkPath(Arrays.asList(l02, l23));
        NetworkPath w2 = new NetworkPath(Arrays.asList(l04, l43));
        NetworkPath b2 = new NetworkPath(Arrays.asList(l02, l23));
        NetworkPath w3 = new NetworkPath(Arrays.asList(l01, l13));
        NetworkPath b3 = new NetworkPath(Arrays.asList(l02, l23));

        Connection c1 = new Connection("c1", new ConnectionRequest("r1", 0, 3, 100), w1, b1);
        Connection c2 = new Connection("c2", new ConnectionRequest("r2", 0, 3, 100), w2, b2);
        Connection c3 = new Connection("c3", new ConnectionRequest("r3", 0, 3, 100), w3, b3);

        TransceiverState exclusive = new TransceiverState(1, 1);
        exclusive.reserveWorking(c1, 1);
        assertThrows(new Runnable() {
            public void run() {
                exclusive.reserveWorking(c2, 1);
            }
        }, "working Tx/Rx must be exclusive");
        exclusive.release(c1, PathRole.WORKING);
        exclusive.reserveWorking(c2, 1);

        TransceiverState sharedBackup = new TransceiverState(2, 2);
        sharedBackup.reserveWorking(c1, 1);
        sharedBackup.reserveBackup(c1, 1);
        assertEquals(2, sharedBackup.usedTransmitters(), "working plus backup should consume two TX units");
        assertEquals(1.0, sharedBackup.txShareability(), "one backup owner on one TX backup unit has shareability one");

        sharedBackup.reserveBackup(c2, 1);
        assertEquals(2, sharedBackup.usedTransmitters(), "link-disjoint backups should share existing TX unit");
        assertEquals(2, sharedBackup.usedReceivers(), "link-disjoint backups should share existing RX unit");
        assertEquals(2.0, sharedBackup.txShareability(), "two backup owners sharing one TX unit gives shareability two");
        assertEquals(2.0, sharedBackup.rxShareability(), "two backup owners sharing one RX unit gives shareability two");

        assertThrows(new Runnable() {
            public void run() {
                sharedBackup.reserveBackup(c3, 1);
            }
        }, "overlapping working paths must not share backup Tx/Rx when no free unit exists");

        System.out.println("Transceiver state test passed");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-12) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
