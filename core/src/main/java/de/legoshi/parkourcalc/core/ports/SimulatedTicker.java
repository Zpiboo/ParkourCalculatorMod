package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3d;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Per-tick port the loader implements to drive its version-specific
 * simulator entity. Core's SimulationRunner iterates InputData against it.
 *
 * Implementations translate between core's Vec3d and Minecraft's own Vec3d
 * (or equivalent) at the boundary; core never sees an MC type.
 */
public interface SimulatedTicker {

    /** Snap the entity to its start position and clear transient state. */
    void resetToStart();

    /** Apply this row's keys + yaw delta as the next-tick input. */
    void applyInput(InputRow row);

    /** Step the entity forward one tick using the input set by applyInput. */
    void tick();

    /** Current world position of the simulated entity (after the last tick). */
    Vec3d getCurrentPosition();

    /** The start position the next resetToStart() will return to. */
    Vec3d getStartPosition();

    /** Override the start position; takes effect on the next resetToStart(). */
    void setStartPosition(Vec3d pos);

    /** Snap the start position to wherever the real player currently stands. */
    void setStartFromPlayer();
}
