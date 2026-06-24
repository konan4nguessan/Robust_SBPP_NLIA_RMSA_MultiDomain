package rmsa.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RobustScenarioGenerator {
    private RobustScenarioGenerator() {
    }

    public static List<FailureScenario> forWorkingPath(NetworkPath workingPath, int linkCount) {
        if (workingPath == null) {
            throw new IllegalArgumentException("Working path is required");
        }
        if (linkCount <= 0) {
            throw new IllegalArgumentException("Link count must be positive");
        }
        List<FailureScenario> scenarios = new ArrayList<FailureScenario>();
        scenarios.add(FailureScenario.noFailure());
        for (int linkId = 0; linkId < linkCount; linkId++) {
            if (!workingPath.containsLink(linkId)) {
                scenarios.add(FailureScenario.failedLink(linkId));
            }
        }
        return scenarios;
    }

    public static List<FailureScenario> forWorkingPath(NetworkPath workingPath, SpectrumState spectrum) {
        if (workingPath == null || spectrum == null) {
            throw new IllegalArgumentException("Working path and spectrum are required");
        }
        List<FailureScenario> scenarios = new ArrayList<FailureScenario>();
        scenarios.add(FailureScenario.noFailure());
        for (Set<Integer> failureGroup : spectrum.uniqueBidirectionalFailureGroups()) {
            if (!workingPath.containsAnyLink(failureGroup)) {
                scenarios.add(FailureScenario.failedLinks(failureGroup));
            }
        }
        return scenarios;
    }

    public static List<FailureScenario> forBackupPath(NetworkPath correspondingWorkingPath) {
        if (correspondingWorkingPath == null) {
            throw new IllegalArgumentException("Corresponding working path is required");
        }
        List<FailureScenario> scenarios = new ArrayList<FailureScenario>();
        for (Integer linkId : correspondingWorkingPath.linkIds()) {
            scenarios.add(FailureScenario.failedLink(linkId.intValue()));
        }
        return scenarios;
    }

    public static List<FailureScenario> forBackupPath(NetworkPath correspondingWorkingPath, SpectrumState spectrum) {
        if (correspondingWorkingPath == null || spectrum == null) {
            throw new IllegalArgumentException("Corresponding working path and spectrum are required");
        }
        List<FailureScenario> scenarios = new ArrayList<FailureScenario>();
        List<String> seen = new ArrayList<String>();
        for (Integer linkId : correspondingWorkingPath.linkIds()) {
            Set<Integer> failureGroup = spectrum.bidirectionalFailureGroupForLink(linkId.intValue());
            String key = failureGroup.toString();
            if (!seen.contains(key)) {
                seen.add(key);
                scenarios.add(FailureScenario.failedLinks(failureGroup));
            }
        }
        return scenarios;
    }
}
