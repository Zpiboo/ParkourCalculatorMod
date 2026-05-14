package de.legoshi.parkourcalc.forge12.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * MC 1.12.2 stand-in for a moving player. Accepts an InputRow per tick and runs through
 * Minecraft's own physics via super.onLivingUpdate().
 *
 * Quick port of the Fabric SimulatorEntity. The fidelity decorations (sprint state
 * gating, sneak-pose handling, input normalization) are deliberately skipped in this
 * first cut — they're the bulk of the Fabric code and can be ported once the path
 * shape on screen tells us what's missing.
 */
public class SimulatorEntity extends EntityPlayer {

    public Vec3d startPosition;

    private InputRow currentInput = new InputRow();

    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition) {
        super(world, profile);
        this.startPosition = startPosition;
        resetPlayer();
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void resetPlayer() {
        this.noClip = true;
        this.motionX = 0.0;
        this.motionY = 0.0;
        this.motionZ = 0.0;
        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
        this.rotationYaw = 0.0F;
        this.rotationPitch = 0.0F;

        this.currentInput = new InputRow();
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
    }

    @Override
    public void onLivingUpdate() {
        applyCurrentInput();
        super.onLivingUpdate();
    }

    private void applyCurrentInput() {
        boolean forward = currentInput.isKeyActive(InputRow.Key.W);
        boolean back = currentInput.isKeyActive(InputRow.Key.S);
        boolean left = currentInput.isKeyActive(InputRow.Key.A);
        boolean right = currentInput.isKeyActive(InputRow.Key.D);
        boolean jump = currentInput.isKeyActive(InputRow.Key.JUMP);
        boolean sneak = currentInput.isKeyActive(InputRow.Key.SNEAK);
        boolean sprint = currentInput.isKeyActive(InputRow.Key.SPRINT);

        this.moveForward = axis(forward, back);
        this.moveStrafing = axis(left, right);
        this.isJumping = jump;
        this.setSneaking(sneak);
        this.setSprinting(sprint);
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isUser() {
        return true;
    }

    @Override
    public boolean isServerWorld() {
        return true;
    }
}
