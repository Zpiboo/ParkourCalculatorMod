package de.legoshi.parkourcalc.core.anglesolver.solver;

public final class SolveProgress {

    private final boolean maximize;
    private final boolean stopOnFeasible;

    private double[] bestYaws;
    private double bestObjective;
    private double bestViolation;
    private boolean bestFeasible;
    private boolean haveBest;
    private int version;
    private String stage;
    private String bestSolver;

    public SolveProgress(boolean maximize, boolean stopOnFeasible) {
        this.maximize = maximize;
        this.stopOnFeasible = stopOnFeasible;
    }

    public boolean stopOnFeasible() {
        return stopOnFeasible;
    }

    public synchronized void setStage(String stage) {
        this.stage = stage;
    }

    public synchronized String bestSolver() {
        return bestSolver;
    }

    public synchronized void report(double[] absWrappedYaws, double objective, double violation, boolean feasible) {
        if (absWrappedYaws == null) return;
        if (!haveBest || isBetter(feasible, objective, violation)) {
            bestYaws = absWrappedYaws.clone();
            bestObjective = objective;
            bestViolation = violation;
            bestFeasible = feasible;
            bestSolver = stage;
            haveBest = true;
            version++;
        }
    }

    public synchronized int version() {
        return version;
    }

    private boolean isBetter(boolean feasible, double objective, double violation) {
        if (feasible != bestFeasible) return feasible;
        return maximize ? objective > bestObjective : objective < bestObjective;
    }

    public synchronized boolean haveBest() {
        return haveBest;
    }

    public synchronized boolean isBestFeasible() {
        return bestFeasible;
    }

    public synchronized double bestObjective() {
        return bestObjective;
    }

    public synchronized double bestViolation() {
        return bestViolation;
    }

    public synchronized double[] bestYaws() {
        return bestYaws == null ? null : bestYaws.clone();
    }
}
