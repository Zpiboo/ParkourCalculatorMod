package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.forge.core.sim.PlaybackMoveState;
import net.minecraft.util.MovementInput;

@SuppressWarnings("DuplicatedCode")
final class PlaybackMovementInput extends MovementInput {

    private final Forge12PlaybackBridge bridge;
    private final PlaybackMoveState state = new PlaybackMoveState();

    PlaybackMovementInput(Forge12PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void updatePlayerMoveState() {
        state.update(bridge.getCurrentRow());
        this.moveStrafe = state.moveStrafe;
        this.moveForward = state.moveForward;
        this.jump = state.jump;
        this.sneak = state.sneak;
    }
}
