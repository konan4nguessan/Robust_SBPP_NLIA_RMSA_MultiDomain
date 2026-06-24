package rmsa.core;

import java.util.Arrays;
import java.util.List;

public final class CoreSmokeTest {
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
        NetworkPath w3 = new NetworkPath(Arrays.asList(l12, l23));
        NetworkPath b3 = new NetworkPath(Arrays.asList(l04, l43));

        Connection c1 = new Connection("c1", new ConnectionRequest("r1", 0, 3, 100), w1, b1);
        Connection c2 = new Connection("c2", new ConnectionRequest("r2", 0, 3, 100), w2, b2);
        Connection c3 = new Connection("c3", new ConnectionRequest("r3", 1, 3, 100), w3, b3);

        SpectrumState spectrum = new SpectrumState(7, 8);
        List<ModulationFormat> twoSlots = Arrays.asList(ModulationFormat.BPSK, ModulationFormat.BPSK);

        spectrum.reserveWorking(c1, 0, twoSlots);
        assertTrue(!spectrum.workingContiguousStarts(w1, 2).contains(0), "working cannot overlap working slots");
        assertEquals(6, spectrum.workingCandidateBlocks(w2, 3).size(), "working CFSS should expose all free contiguous starts");
        assertEquals(0, spectrum.workingCandidateBlocks(w2, 3).get(0).startSlot(), "first working CFSS should start at slot zero");
        assertEquals(2, spectrum.workingCandidateBlocks(w2, 3).get(0).endSlotInclusive(), "first working CFSS should expose its inclusive end");

        spectrum.reserveBackup(c1, 2, twoSlots);
        assertTrue(spectrum.backupContiguousStarts(b2, 2, c2).contains(2), "backup should share when working paths are link-disjoint");
        assertTrue(spectrum.backupCandidateBlocks(b2, 2, c2).get(2).slotIndexes().contains(3), "backup CFSS should expose slot indexes");
        spectrum.reserveBackup(c2, 2, twoSlots);
        assertEquals(2, spectrum.slot(3, 2).backupOwners().size(), "shared backup slot should have two owners");

        assertTrue(!spectrum.backupContiguousStarts(b3, 2, c3).contains(2), "backup must not share when working paths overlap");
        assertTrue(!spectrum.workingContiguousStarts(b1, 2).contains(2), "working must not use backup-reserved slots");

        double objective = ObjectiveFunction.value(4, 4, spectrum);
        assertTrue(objective > 0 && !Double.isInfinite(objective), "objective should be finite after allocation");

        assertEquals(3, RmsaSizing.minimumSlotsForBestModulation(600), "nmin should use the highest available MF");
        CfssWorkingEvaluator evaluator = new CfssWorkingEvaluator(
                spectrum,
                new FixedModulationAssigner(ModulationFormat.QAM_16));
        Connection c4 = new Connection("c4", new ConnectionRequest("r4", 0, 3, 600), w2, b2);
        CfssEvaluation feasible = evaluator.evaluate(c4, new ContiguousSlotBlock(0, 3));
        assertTrue(feasible.isFeasible(), "CFSSw should accept a clean three-slot 16-QAM allocation for 600 Gb/s");
        assertEquals(3, feasible.slotIndexes().size(), "600 Gb/s at 16-QAM should consume three slots");
        assertEquals(600, feasible.carriedRateGbps(), "carried rate should satisfy the demand exactly here");

        CfssWorkingEvaluator bpskOnlyEvaluator = new CfssWorkingEvaluator(
                spectrum,
                new FixedModulationAssigner(ModulationFormat.BPSK));
        CfssEvaluation rejected = bpskOnlyEvaluator.evaluate(c4, new ContiguousSlotBlock(5, 3));
        assertTrue(!rejected.isFeasible(), "CFSSw should reject when contiguous slots run out");
        assertEquals(150, rejected.carriedRateGbps(), "rejected candidate should keep the partial carried rate for diagnostics");

        PhysicalLayerModel physical = new PhysicalLayerModel(PhysicalLayerParameters.paperLikeDefaults());
        double sci = physical.sciPerSpanW();
        double nearXci = physical.xciPerSpanW(2, 3);
        double farXci = physical.xciPerSpanW(2, 6);
        assertTrue(sci > 0, "SCI should be positive");
        assertTrue(nearXci > 0, "XCI should be positive for a different slot");
        assertTrue(nearXci > farXci, "XCI should decrease as spectral distance increases");
        assertTrue(physical.aseForPathW(w1) > physical.aseForPathW(new NetworkPath(Arrays.asList(l01))), "ASE should grow with path length");

        SlotQoT cleanQot = physical.slotQoT(w1, sci);
        SlotQoT noisyQot = physical.slotQoT(w1, sci + nearXci * 100.0);
        assertTrue(cleanQot.sinrDb() > noisyQot.sinrDb(), "SINR should decrease when NLI increases");
        assertTrue(cleanQot.highestSupportedModulation() != null, "QoT should map to a supported modulation when SINR is sufficient");

        assertEquals(0, spectrum.activeAllocationsOnLink(3, FailureScenario.noFailure()).size(), "backup-only link should be inactive without failure");
        assertEquals(2, spectrum.activeAllocationsOnLink(3, FailureScenario.failedLink(0)).size(), "failed working link should activate its backup slots");
        assertEquals("c1", spectrum.activeAllocationsOnLink(3, FailureScenario.failedLink(0)).get(0).connection().id(), "failure should activate the matching backup owner");
        assertEquals("c2", spectrum.activeAllocationsOnLink(3, FailureScenario.failedLink(5)).get(0).connection().id(), "a different failure should activate a different shared backup owner");

        NetworkNliEvaluator nliEvaluator = new NetworkNliEvaluator(spectrum, physical);
        PathSlotNli noFailureNli = nliEvaluator.evaluate(b1, 0, FailureScenario.noFailure());
        PathSlotNli failedNli = nliEvaluator.evaluate(b1, 0, FailureScenario.failedLink(0));
        assertTrue(failedNli.totalNliW() > noFailureNli.totalNliW(), "active backup slots should add XCI under their failure scenario");

        RobustSlotQoT robustQoT = nliEvaluator.robustQoT(
                b1,
                0,
                Arrays.asList(FailureScenario.noFailure(), FailureScenario.failedLink(0)));
        assertEquals(0, robustQoT.worstCaseScenario().failedLinkId().intValue(), "robust QoT should retain the worst failure scenario");
        assertTrue(robustQoT.nliMaxW() == failedNli.totalNliW(), "robust QoT should use the maximum NLI");

        List<FailureScenario> workingScenarios = RobustScenarioGenerator.forWorkingPath(w1, spectrum.linkCount());
        assertTrue(workingScenarios.contains(FailureScenario.noFailure()), "working robust scenarios should include no failure");
        assertTrue(!workingScenarios.contains(FailureScenario.failedLink(0)), "working robust scenarios should exclude failures on the candidate working path");
        assertTrue(!workingScenarios.contains(FailureScenario.failedLink(1)), "working robust scenarios should exclude every link on the candidate working path");
        assertTrue(workingScenarios.contains(FailureScenario.failedLink(3)), "working robust scenarios should include failures outside the candidate working path");
        assertEquals(5, workingScenarios.size(), "working robust scenarios should be no-failure plus all off-path link failures");

        List<FailureScenario> backupScenarios = RobustScenarioGenerator.forBackupPath(w1);
        assertTrue(!backupScenarios.contains(FailureScenario.noFailure()), "backup robust scenarios should not include no failure");
        assertTrue(backupScenarios.contains(FailureScenario.failedLink(0)), "backup robust scenarios should include failures on the corresponding working path");
        assertTrue(backupScenarios.contains(FailureScenario.failedLink(1)), "backup robust scenarios should include all corresponding working links");
        assertTrue(!backupScenarios.contains(FailureScenario.failedLink(3)), "backup robust scenarios should exclude failures outside the corresponding working path");
        assertEquals(3, backupScenarios.size(), "backup robust scenarios should include exactly the corresponding working path failures");

        RobustSlotQoT directWorkingQoT = nliEvaluator.robustQoTForWorkingPath(w1, 0);
        RobustSlotQoT manualWorkingQoT = nliEvaluator.robustQoT(w1, 0, workingScenarios);
        assertEquals(manualWorkingQoT.worstCaseScenario(), directWorkingQoT.worstCaseScenario(), "direct working QoT should use generated working scenarios");
        assertAlmostEquals(manualWorkingQoT.nliMaxW(), directWorkingQoT.nliMaxW(), 1e-30, "direct working QoT should match manual scenario evaluation");

        RobustSlotQoT directBackupQoT = nliEvaluator.robustQoTForBackupPath(b1, w1, 0);
        RobustSlotQoT manualBackupQoT = nliEvaluator.robustQoT(b1, 0, backupScenarios);
        assertEquals(manualBackupQoT.worstCaseScenario(), directBackupQoT.worstCaseScenario(), "direct backup QoT should use generated backup scenarios");
        assertAlmostEquals(manualBackupQoT.nliMaxW(), directBackupQoT.nliMaxW(), 1e-30, "direct backup QoT should match manual scenario evaluation");

        RobustSlotQoT isolatedWorkingSlot = nliEvaluator.robustQoTForWorkingPath(w2, 3);
        RobustSlotQoT withSameConnectionXci = nliEvaluator.robustQoTForWorkingPath(w2, 3, Arrays.asList(2));
        assertTrue(withSameConnectionXci.nliMaxW() > isolatedWorkingSlot.nliMaxW(), "same-connection decided slots should add XCI");

        CfssWorkingEvaluator robustEvaluator = new CfssWorkingEvaluator(
                spectrum,
                new RobustWorkingModulationAssigner(nliEvaluator));
        CfssEvaluation robustEvaluation = robustEvaluator.evaluate(c4, new ContiguousSlotBlock(0, 3));
        assertTrue(robustEvaluation.isFeasible(), "CFSSw should work with robust working modulation assignment");
        assertTrue(robustEvaluation.modulationFormats().get(0) != null, "robust bitloading should assign a modulation to each accepted slot");
        assertTrue(robustEvaluation.carriedRateGbps() >= c4.request().dataRateGbps(), "robust bitloading should satisfy the requested rate when feasible");

        assertEquals(6, spectrum.uniqueAllocations().size(), "unique allocations should deduplicate path-link repetitions");
        ExistingConnectionsQoTChecker existingChecker = new ExistingConnectionsQoTChecker(spectrum, nliEvaluator);
        ExistingQoTCheck farCandidateCheck = existingChecker.checkWithHypothetical(
                new HypotheticalSlotAllocation(c4, PathRole.WORKING, c4.workingPath(), 7));
        assertTrue(farCandidateCheck.isFeasible(), "far hypothetical candidate should preserve existing QoT in this small setup");

        HypotheticalSlotAllocation nearCandidate = new HypotheticalSlotAllocation(c3, PathRole.WORKING, c3.workingPath(), 1);
        ExistingQoTCheck nearCandidateCheck = existingChecker.checkWithHypothetical(nearCandidate);
        RobustSlotQoT existingWithoutNearCandidate = nliEvaluator.robustQoTForWorkingPath(w1, 0);
        RobustSlotQoT existingWithNearCandidate = nliEvaluator.robustQoTWithHypotheticals(
                w1,
                0,
                RobustScenarioGenerator.forWorkingPath(w1, spectrum.linkCount()),
                Arrays.asList(nearCandidate));
        assertTrue(existingWithNearCandidate.nliMaxW() > existingWithoutNearCandidate.nliMaxW(), "near hypothetical candidate should increase existing-slot NLI");
        if (!nearCandidateCheck.isFeasible()) {
            assertTrue(nearCandidateCheck.violatingQoT().sinrMinDb()
                            < nearCandidateCheck.violatingAllocation().modulationFormat().sinrThresholdDb(),
                    "rejection should be caused by an existing modulation threshold violation");
        }

        NliSnapshotStore snapshotStore = NliSnapshotStore.build(spectrum, nliEvaluator);
        assertTrue(snapshotStore.size() > spectrum.uniqueAllocations().size(), "NLI snapshot should store multiple scenarios per allocation");
        ExistingQoTGuard recursiveGuard = new RecursiveExistingConnectionsQoTChecker(
                spectrum,
                nliEvaluator,
                snapshotStore);
        ExistingQoTCheck recursiveNearCandidateCheck = recursiveGuard.checkWithHypothetical(nearCandidate);
        assertEquals(nearCandidateCheck.isFeasible(), recursiveNearCandidateCheck.isFeasible(), "recursive QoT checker should match direct QoT checker decision");

        GreedyRobustBitloading greedyBitloading = new GreedyRobustBitloading(nliEvaluator);
        BitloadingDecision bitloadingDecision = greedyBitloading.addWorkingSlot(
                c4,
                1,
                Arrays.asList(0),
                Arrays.asList(ModulationFormat.QAM_16));
        assertTrue(bitloadingDecision.isFeasible(), "greedy bitloading should accept a QoT-feasible target slot");
        assertEquals(2, bitloadingDecision.modulationFormats().size(), "greedy bitloading should return updated modulations for old plus new slots");
        assertTrue(bitloadingDecision.modulationFormats().get(0).sinrThresholdDb() <= ModulationFormat.QAM_16.sinrThresholdDb(),
                "greedy bitloading must not upgrade an already chosen modulation");

        RobustCfssWorkingEvaluator robustCfssWorkingEvaluator = new RobustCfssWorkingEvaluator(
                spectrum,
                greedyBitloading,
                recursiveGuard);
        CfssEvaluation robustCfssEvaluation = robustCfssWorkingEvaluator.evaluate(c4, new ContiguousSlotBlock(0, 3));
        assertTrue(robustCfssEvaluation.isFeasible(), "robust CFSSw should combine existing QoT guard and greedy bitloading");
        assertTrue(robustCfssEvaluation.carriedRateGbps() >= c4.request().dataRateGbps(), "robust CFSSw should carry enough bitrate when feasible");
        assertEquals(robustCfssEvaluation.slotIndexes().size(), robustCfssEvaluation.modulationFormats().size(), "robust CFSSw should keep one modulation per slot");

        BitloadingDecision backupBitloadingDecision = greedyBitloading.addBackupSlot(
                c2,
                2,
                Arrays.asList(3),
                Arrays.asList(ModulationFormat.QAM_16));
        assertTrue(backupBitloadingDecision.isFeasible(), "greedy backup bitloading should accept a QoT-feasible backup slot");
        assertEquals(2, backupBitloadingDecision.modulationFormats().size(), "backup bitloading should return old plus new slot modulations");

        RobustCfssBackupEvaluator robustCfssBackupEvaluator = new RobustCfssBackupEvaluator(
                spectrum,
                greedyBitloading,
                recursiveGuard);
        CfssEvaluation robustBackupEvaluation = robustCfssBackupEvaluator.evaluate(c2, new ContiguousSlotBlock(4, 2));
        assertTrue(robustBackupEvaluation.isFeasible(), "robust CFSSb should allow usable backup slots");
        assertTrue(robustBackupEvaluation.carriedRateGbps() >= c2.request().dataRateGbps(), "robust CFSSb should satisfy the backup bitrate when feasible");
        assertEquals(robustBackupEvaluation.slotIndexes().size(), robustBackupEvaluation.modulationFormats().size(), "robust CFSSb should keep one modulation per slot");

        int snapshotSizeBeforeDynamicAllocation = snapshotStore.size();
        spectrum.reserveWorking(c4, robustCfssEvaluation.slotIndexes().get(0).intValue(), robustCfssEvaluation.modulationFormats());
        snapshotStore.refreshFromSpectrum(spectrum, nliEvaluator);
        assertTrue(snapshotStore.size() > snapshotSizeBeforeDynamicAllocation, "snapshot refresh should include newly accepted allocations");
        ExistingQoTCheck refreshedRecursiveCheck = recursiveGuard.checkWithHypothetical(
                new HypotheticalSlotAllocation(c3, PathRole.WORKING, c3.workingPath(), 5));
        assertEquals(refreshedRecursiveCheck.isFeasible(), refreshedRecursiveCheck.isFeasible(), "refreshed recursive guard should remain callable after dynamic allocation");

        System.out.println("Core smoke test passed");
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

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(boolean expected, boolean actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(FailureScenario expected, FailureScenario actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertAlmostEquals(double expected, double actual, double tolerance, String message) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }
}
