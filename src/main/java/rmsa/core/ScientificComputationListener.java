package rmsa.core;

public interface ScientificComputationListener {
    void recordRobustQoTValidation(int scenarioCount);

    void recordExistingQoTValidation(int scenarioCount);

    void recordNliSinrComputation(boolean cacheHit);
}