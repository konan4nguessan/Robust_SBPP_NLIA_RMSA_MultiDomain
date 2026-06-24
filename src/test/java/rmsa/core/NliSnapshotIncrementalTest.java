package rmsa.core;

import java.util.Arrays;
import java.util.List;

public final class NliSnapshotIncrementalTest {
    public static void main(String[] args) {
        Link l01 = new Link(0, 0, 1, 100);
        Link l12 = new Link(1, 1, 2, 100);
        Link l23 = new Link(2, 2, 3, 100);
        Link l04 = new Link(3, 0, 4, 100);
        Link l43 = new Link(4, 4, 3, 100);
        Link l05 = new Link(5, 0, 5, 100);
        Link l53 = new Link(6, 5, 3, 100);

        NetworkPath w1 = new NetworkPath(Arrays.asList(l01, l12, l23));
        NetworkPath b1 = new NetworkPath(Arrays.asList(l04, l43));
        NetworkPath w2 = new NetworkPath(Arrays.asList(l05, l53));
        NetworkPath b2 = new NetworkPath(Arrays.asList(l04, l43));
        NetworkPath w3 = new NetworkPath(Arrays.asList(l01, l12));
        NetworkPath b3 = new NetworkPath(Arrays.asList(l05, l53));

        Connection c1 = new Connection("c1", new ConnectionRequest("r1", 0, 3, 100), w1, b1);
        Connection c2 = new Connection("c2", new ConnectionRequest("r2", 0, 3, 100), w2, b2);
        Connection c3 = new Connection("c3", new ConnectionRequest("r3", 0, 2, 100), w3, b3);

        SpectrumState spectrum = new SpectrumState(7, 10);
        List<ModulationFormat> twoSlots = Arrays.asList(ModulationFormat.BPSK, ModulationFormat.BPSK);
        spectrum.reserveWorking(c1, 0, twoSlots);
        spectrum.reserveBackup(c1, 2, twoSlots);
        spectrum.reserveWorking(c2, 0, twoSlots);
        spectrum.reserveBackup(c2, 2, twoSlots);

        NetworkNliEvaluator nliEvaluator = new NetworkNliEvaluator(
                spectrum,
                new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults()));
        NliSnapshotStore incremental = NliSnapshotStore.build(spectrum, nliEvaluator);

        spectrum.reserveWorking(c3, 4, twoSlots);
        spectrum.reserveBackup(c3, 4, twoSlots);
        incremental.updateAfterAcceptedAllocation(
                spectrum,
                nliEvaluator,
                Arrays.asList(
                        new SlotAllocation(c3, PathRole.WORKING, 4, ModulationFormat.BPSK),
                        new SlotAllocation(c3, PathRole.WORKING, 5, ModulationFormat.BPSK),
                        new SlotAllocation(c3, PathRole.BACKUP, 4, ModulationFormat.BPSK),
                        new SlotAllocation(c3, PathRole.BACKUP, 5, ModulationFormat.BPSK)));

        NliSnapshotStore global = NliSnapshotStore.build(spectrum, nliEvaluator);
        assertSnapshotsMatch(global, incremental, spectrum, "incremental accepted NLI should match global refresh");

        List<SlotAllocation> releasedAllocations = Arrays.asList(
                new SlotAllocation(c3, PathRole.WORKING, 4, ModulationFormat.BPSK),
                new SlotAllocation(c3, PathRole.WORKING, 5, ModulationFormat.BPSK),
                new SlotAllocation(c3, PathRole.BACKUP, 4, ModulationFormat.BPSK),
                new SlotAllocation(c3, PathRole.BACKUP, 5, ModulationFormat.BPSK));
        spectrum.releaseWorking(c3, Arrays.asList(Integer.valueOf(4), Integer.valueOf(5)));
        spectrum.releaseBackup(c3, Arrays.asList(Integer.valueOf(4), Integer.valueOf(5)));
        incremental.updateAfterReleasedAllocation(spectrum, nliEvaluator, releasedAllocations);

        NliSnapshotStore globalAfterRelease = NliSnapshotStore.build(spectrum, nliEvaluator);
        assertSnapshotsMatch(globalAfterRelease, incremental, spectrum, "incremental released NLI should match global refresh");

        System.out.println("NLI snapshot incremental test passed");
    }

    private static void assertSnapshotsMatch(
            NliSnapshotStore expected,
            NliSnapshotStore actual,
            SpectrumState spectrum,
            String message) {
        assertEquals(expected.size(), actual.size(), message + " size");
        for (SlotAllocation allocation : spectrum.uniqueAllocations()) {
            NetworkPath path = allocation.connection().pathForRole(allocation.role());
            List<FailureScenario> scenarios = allocation.role() == PathRole.WORKING
                    ? RobustScenarioGenerator.forWorkingPath(path, spectrum.linkCount())
                    : RobustScenarioGenerator.forBackupPath(allocation.connection().workingPath());
            for (FailureScenario scenario : scenarios) {
                assertAlmostEquals(
                        expected.get(allocation, scenario),
                        actual.get(allocation, scenario),
                        1e-21,
                        message);
            }
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertAlmostEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
