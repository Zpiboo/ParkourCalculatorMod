package de.legoshi.parkourcalc.forge.core.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;

/** Maps a playback InputRow to MovementInput axis values; shared by both Forge loaders' PlaybackMovementInput. */
public final class PlaybackMoveState {

    public float moveStrafe;
    public float moveForward;
    public boolean jump;
    public boolean sneak;

    public void update(InputRow row) {
        moveStrafe = 0.0F;
        moveForward = 0.0F;
        jump = false;
        sneak = false;
        if (row == null) return;
        if (row.isKeyActive(InputRow.Key.W)) moveForward += 1.0F;
        if (row.isKeyActive(InputRow.Key.S)) moveForward -= 1.0F;
        if (row.isKeyActive(InputRow.Key.A)) moveStrafe += 1.0F;
        if (row.isKeyActive(InputRow.Key.D)) moveStrafe -= 1.0F;
        jump = row.isKeyActive(InputRow.Key.JUMP);
        sneak = row.isKeyActive(InputRow.Key.SNEAK);
        if (sneak) {
            moveStrafe *= 0.3F;
            moveForward *= 0.3F;
        }
    }
}
