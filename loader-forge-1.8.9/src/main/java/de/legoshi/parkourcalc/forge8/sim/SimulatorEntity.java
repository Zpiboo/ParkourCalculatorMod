package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * MC 1.8.9 stand-in for a moving player. Same shape as the 1.12.2 SimulatorEntity but
 * against the older API surface: net.minecraft.util.Vec3 (no math.Vec3d), and 1.8.9
 * only requires the isSpectator abstract method (no isCreative override).
 */
public class SimulatorEntity extends EntityPlayer {

    public Vec3 startPosition;

    private InputRow currentInput = new InputRow();

    public SimulatorEntity(World world, GameProfile profile, Vec3 startPosition) {
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
        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        this.rotationYaw = 0.0F;
        this.rotationPitch = 0.0F;

        this.currentInput = new InputRow();
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
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
    public boolean isServerWorld() {
        return true;
    }
}
