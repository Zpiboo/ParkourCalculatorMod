package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

final class PlaybackInput extends Input {

    private final FabricPlaybackBridge bridge;

    PlaybackInput(FabricPlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void tick() {
        InputRow row = bridge.getCurrentRow();
        boolean fwd = false, back = false, left = false, right = false, jump = false, sneak = false, sprint = false;
        if (row != null) {
            fwd = row.isKeyActive(InputRow.Key.W);
            back = row.isKeyActive(InputRow.Key.S);
            left = row.isKeyActive(InputRow.Key.A);
            right = row.isKeyActive(InputRow.Key.D);
            jump = row.isKeyActive(InputRow.Key.JUMP);
            sneak = row.isKeyActive(InputRow.Key.SNEAK);
            sprint = row.isKeyActive(InputRow.Key.SPRINT);
        }
        this.playerInput = new PlayerInput(fwd, back, left, right, jump, sneak, sprint);
        float f = axis(fwd, back);
        float g = axis(left, right);
        this.movementVector = new Vec2f(g, f).normalize();
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
