package rmsa.core;

import java.util.Arrays;
import java.util.List;

public final class ScientificCoreTargetedTest {
    public static void main(String[] args) {
        testPhysicalContributions();
        testRobustScenarioSelection();
        testBidirectionalRobustScenarioSelection();
        testBackupSpectrumSharing();
        testBidirectionalPhysicalRiskDisjointness();
        testExistingQoTGuardEquivalence();
        testBitloadingCanDegradeExistingSlot();

        System.out.println("Scientific core targeted test passed");
    }

    private static void testPhysicalContributions() {
        Link l01 = new Link(0, 0, 1, 100);
        Link l12 = new Link(1, 1, 2, 200);
        NetworkPath oneLink = new NetworkPath(Arrays.asList(l01));
        NetworkPath twoLinks = new NetworkPath(Arrays.asList(l01, l12));
        PhysicalLayerModel physical = new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults());

        double sci = physical.sciPerSpanW();
        double nearXci = physical.xciPerSpanW(4, 5);
        double farXci = physical.xciPerSpanW(4, 9);
        assertTrue(sci > 0.0, "SCI must be positive");
        assertTrue(nearXci > farXci, "XCI must decrease with spectral distance");
        assertTrue(physical.aseForPathW(twoLinks) > physical.aseForPathW(oneLink), "ASE must grow with path length");
    }

    private static void testRobustScenarioSelection() {
        Link l01 = new Link(0, 0, 1, 100);
        Link l12 = new Link(1, 1, 2, 100);
        NetworkPath working = new NetworkPath(Arrays.asList(l01, l12));

        List<FailureScenario> workingScenarios = RobustScenarioGenerator.forWorkingPath(working, 3);
        assertTrue(workingScenarios.contains(FailureScenario.noFailure()), "working scenarios must include no failure");
        assertTrue(!workingScenarios.contains(FailureScenario.failedLink(0)), "working scenarios must exclude on-path failures");
        assertTrue(workingScenarios.contains(FailureScenario.failedLink(2)), "working scenarios must include off-path failures");

        List<FailureScenario> backupScenarios = RobustScenarioGenerator.forBackupPath(working);
        assertTrue(!backupScenarios.contains(FailureScenario.noFailure()), "backup scenarios must exclude no failure");
        assertTrue(backupScenarios.contains(FailureScenario.failedLink(0)), "backup scenarios must include working path failures");
        assertTrue(!backupScenarios.contains(FailureScenario.failedLink(2)), "backup scenarios must exclude off-working-path failures");
        assertEquals(2, backupScenarios.size(), "backup scenarios must contain one case per working link");

    }

    private static void testBidirectionalRobustScenarioSelection() {
        Link l01 = new Link(0, 0, 1, 100);
        Link l10 = new Link(1, 1, 0, 100);
        Link l12 = new Link(2, 1, 2, 100);
        Link l21 = new Link(3, 2, 1, 100);
        Link l23 = new Link(4, 2, 3, 100);
        SpectrumState spectrum = new SpectrumState(Arrays.asList(l01, l10, l12, l21, l23), 8);
        NetworkPath working = new NetworkPath(Arrays.asList(l01, l12));

        List<FailureScenario> workingScenarios = RobustScenarioGenerator.forWorkingPath(working, spectrum);
        assertTrue(workingScenarios.contains(FailureScenario.noFailure()),
                "bidirectional working scenarios must include no failure");
        assertTrue(!workingScenarios.contains(FailureScenario.failedLinks(Arrays.asList(Integer.valueOf(0), Integer.valueOf(1)))),
                "working scenarios must exclude the whole physical risk of an on-path directed link");
        assertTrue(workingScenarios.contains(FailureScenario.failedLink(4)),
                "working scenarios must include off-path physical risks");

        List<FailureScenario> backupScenarios = RobustScenarioGenerator.forBackupPath(working, spectrum);
        assertTrue(backupScenarios.contains(FailureScenario.failedLinks(Arrays.asList(Integer.valueOf(0), Integer.valueOf(1)))),
                "backup scenarios must include the full bidirectional risk of a working link");
        assertTrue(backupScenarios.contains(FailureScenario.failedLinks(Arrays.asList(Integer.valueOf(2), Integer.valueOf(3)))),
                "backup scenarios must include every working physical risk exactly once");
        assertEquals(2, backupScenarios.size(),
                "backup scenarios must deduplicate opposite directed links into one physical failure");
    }

    private static void testBackupSpectrumSharing() {
        Link l01 = new Link(0, 0, 1, 100);
        Link l13 = new Link(1, 1, 3, 100);
        Link l02 = new Link(2, 0, 2, 100);
        Link l23 = new Link(3, 2, 3, 100);
        Link l04 = new Link(4, 0, 4, 100);
        Link l43 = new Link(5, 4, 3, 100);

        NetworkPath w1 = new NetworkPath(Arrays.asList(l01, l13));
        NetworkPath w2 = new NetworkPath(Arrays.asList(l04, l43));
        NetworkPath w3 = new NetworkPath(Arrays.asList(l01, l13));
        NetworkPath sharedBackup = new NetworkPath(Arrays.asList(l02, l23));

        Connection c1 = new Connection("c1", new ConnectionRequest("r1", 0, 3, 100), w1, sharedBackup);
        Connection c2 = new Connection("c2", new ConnectionRequest("r2", 0, 3, 100), w2, sharedBackup);
        Connection c3 = new Connection("c3", new ConnectionRequest("r3", 0, 3, 100), w3, sharedBackup);

        SpectrumState spectrum = new SpectrumState(6, 8);
        List<ModulationFormat> oneSlot = Arrays.asList(ModulationFormat.BPSK);
        spectrum.reserveBackup(c1, 2, oneSlot);
        assertTrue(spectrum.backupContiguousStarts(sharedBackup, 1, c2).contains(Integer.valueOf(2)),
                "backup slots must be shareable when working paths are link-disjoint");
        spectrum.reserveBackup(c2, 2, oneSlot);
        assertEquals(2, spectrum.slot(2, 2).backupOwners().size(), "shared backup slot must hold both owners");
        assertTrue(!spectrum.backupContiguousStarts(sharedBackup, 1, c3).contains(Integer.valueOf(2)),
                "backup slots must not be shareable when working paths overlap");
    }

    private static void testBidirectionalPhysicalRiskDisjointness() {
        Link forward = new Link(0, 0, 1, 100);
        Link reverse = new Link(1, 1, 0, 100);
        Link independent = new Link(2, 0, 2, 100);
        NetworkPath forwardPath = new NetworkPath(Arrays.asList(forward));
        NetworkPath reversePath = new NetworkPath(Arrays.asList(reverse));
        NetworkPath independentPath = new NetworkPath(Arrays.asList(independent));

        assertTrue(!forwardPath.isLinkDisjointWith(reversePath),
                "opposite directed links must share the same physical failure risk");
        assertTrue(forwardPath.isLinkDisjointWith(independentPath),
                "different physical links should remain disjoint");
    }


    private static void testExistingQoTGuardEquivalence() {
        Link l01 = new Link(0, 0, 1, 100);
        Link l12 = new Link(1, 1, 2, 100);
        Link l23 = new Link(2, 2, 3, 100);
        Link l04 = new Link(3, 0, 4, 100);
        Link l43 = new Link(4, 4, 3, 100);

        NetworkPath working = new NetworkPath(Arrays.asList(l01, l12, l23));
        NetworkPath backup = new NetworkPath(Arrays.asList(l04, l43));
        Connection existing = new Connection("existing", new ConnectionRequest("r1", 0, 3, 100), working, backup);
        Connection candidateConnection = new Connection("candidate", new ConnectionRequest("r2", 0, 3, 100), working, backup);
        SpectrumState spectrum = new SpectrumState(5, 8);
        spectrum.reserveWorking(existing, 0, Arrays.asList(ModulationFormat.BPSK));

        NetworkNliEvaluator nliEvaluator = new NetworkNliEvaluator(
                spectrum,
                new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults()));
        ExistingQoTGuard direct = new ExistingConnectionsQoTChecker(spectrum, nliEvaluator);
        ExistingQoTGuard recursive = new RecursiveExistingConnectionsQoTChecker(
                spectrum,
                nliEvaluator,
                NliSnapshotStore.build(spectrum, nliEvaluator));
        HypotheticalSlotAllocation candidate = new HypotheticalSlotAllocation(
                candidateConnection,
                PathRole.WORKING,
                candidateConnection.workingPath(),
                1);

        assertEquals(
                direct.checkWithHypothetical(candidate).isFeasible(),
                recursive.checkWithHypothetical(candidate).isFeasible(),
                "recursive existing QoT guard must match direct guard");
    }

    private static void testBitloadingCanDegradeExistingSlot() {
        Link l01 = new Link(0, 0, 1, 400);
        NetworkPath working = new NetworkPath(Arrays.asList(l01));
        NetworkPath backup = new NetworkPath(Arrays.asList(new Link(1, 0, 2, 100), new Link(2, 2, 1, 100)));
        Connection connection = new Connection("bitload", new ConnectionRequest("r", 0, 1, 100), working, backup);
        SpectrumState spectrum = new SpectrumState(3, 8);
        PhysicalLayerParameters nonlinear = new PhysicalLayerParameters(
                3.0,
                PhysicalLayerParameters.PAPER_BETA2_SECONDS_SQUARED_PER_KM,
                PhysicalLayerParameters.dbPerKmToLinearPerKm(PhysicalLayerParameters.DEFAULT_ALPHA_DB_PER_KM),
                PhysicalLayerParameters.PAPER_SLOT_BANDWIDTH_HZ,
                PhysicalLayerParameters.dbmToW(PhysicalLayerParameters.DEFAULT_SIGNAL_POWER_DBM),
                PhysicalLayerParameters.DEFAULT_SPAN_LENGTH_KM,
                PhysicalLayerParameters.dbToLinear(PhysicalLayerParameters.DEFAULT_NOISE_FIGURE_DB),
                PhysicalLayerParameters.DEFAULT_OPTICAL_CARRIER_FREQUENCY_HZ);
        GreedyRobustBitloading bitloading = new GreedyRobustBitloading(
                new NetworkNliEvaluator(spectrum, new PhysicalLayerModel(nonlinear)));

        BitloadingDecision decision = bitloading.addWorkingSlot(
                connection,
                1,
                Arrays.asList(Integer.valueOf(0)),
                Arrays.asList(ModulationFormat.QAM_16));
        assertTrue(decision.isFeasible(), "bitloading should keep a feasible modulation under controlled nonlinear load");
        assertTrue(decision.modulationFormats().get(0).sinrThresholdDb() < ModulationFormat.QAM_16.sinrThresholdDb(),
                "bitloading must degrade an existing slot when the new slot lowers its robust SINR");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
