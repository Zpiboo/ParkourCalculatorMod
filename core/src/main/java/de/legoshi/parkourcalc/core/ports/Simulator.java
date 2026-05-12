package de.legoshi.parkourcalc.core.ports;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;

/**
 * Per-tick simulator port the loader implements to drive its version-specific
 * physics body. Core's SimulationRunner iterates InputData against it.
 *
 * Implementations translate between core's Vec3dCore and Minecraft's own position
 * types at the boundary; core never sees an MC type.
 */
public interface Simulator {

    /** Snap the simulated body to its start position and clear transient state. */
    void resetToStart();

    /** Apply this row's keys + yaw delta as the next-tick input. */
    void applyInput(InputRow row);

    /** Step one tick using the input set by applyInput. */
    void tick();

    /** Current world position of the simulated body (after the last tick). */
    Vec3dCore getCurrentPosition();

    /** The start position the next resetToStart() will return to. */
    Vec3dCore getStartPosition();

    /** Override the start position; takes effect on the next resetToStart(). */
    void setStartPosition(Vec3dCore pos);

    /** Snap the start position to wherever the real player currently stands. */
    void setStartFromPlayer();
}
