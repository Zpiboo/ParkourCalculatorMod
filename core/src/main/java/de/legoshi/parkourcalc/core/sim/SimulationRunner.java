package de.legoshi.parkourcalc.core.sim;

import de.legoshi.parkourcalc.core.ports.SimulatedTicker;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a SimulatedTicker through the rows of an InputData and collects the
 * tick-by-tick positions. The iteration logic is identical across all loaders;
 * only the per-tick mechanics differ, which the ticker abstracts.
 */
public final class SimulationRunner {

    private final SimulatedTicker ticker;

    public SimulationRunner(SimulatedTicker ticker) {
        this.ticker = ticker;
    }

    public List<Vec3d> simulate(InputData inputData) {
        ticker.resetToStart();

        List<Vec3d> path = new ArrayList<Vec3d>();
        path.add(ticker.getStartPosition());

        for (InputRow row : inputData.getRows()) {
            ticker.applyInput(row);
            ticker.tick();
            path.add(ticker.getCurrentPosition());
        }

        return path;
    }

    public Vec3d getStartPosition() {
        return ticker.getStartPosition();
    }

    public void setStartPosition(Vec3d pos) {
        ticker.setStartPosition(pos);
    }

    public void setStartFromPlayer() {
        ticker.setStartFromPlayer();
    }
}
