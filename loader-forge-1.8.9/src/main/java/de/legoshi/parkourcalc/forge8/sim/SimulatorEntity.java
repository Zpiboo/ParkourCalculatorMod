package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** 1.8.9 API surface uses net.minecraft.util.Vec3 and has no moveVertical field. */
public class SimulatorEntity extends EntityPlayer {

    public Vec3 startPosition;

    private InputRow currentInput = new InputRow();

    // sprintToggleTimer mirrors EntityPlayerSP. The prev* fields stand in for MC's
    // persistent movementInput so we can reconstruct its pre-updatePlayerMoveState
    // `flag1`/`flag2` snapshot at tick start.
    private int sprintToggleTimer = 0;
    private float prevMoveForward = 0.0F;
    private boolean prevSneak = false;

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
        this.sprintToggleTimer = 0;
        this.prevMoveForward = 0.0F;
        this.prevSneak = false;
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
    }

    @Override
    public void onLivingUpdate() {
        applyMovementInput();
        super.onLivingUpdate();
    }

    /** Mirrors EntityPlayerSP.onLivingUpdate sprint block (MCP stable-22). */
    private void applyMovementInput() {
        if (this.sprintToggleTimer > 0) {
            this.sprintToggleTimer--;
        }

        boolean flag1 = this.prevSneak;
        float f = 0.8F;
        boolean flag2 = this.prevMoveForward >= f;

        // x0.3 sneak scaling mirrors MovementInputFromOptions so `moveForward >= f`
        // matches MC's threshold test (sneak drops it below 0.8).
        boolean fwd = currentInput.isKeyActive(InputRow.Key.W);
        boolean back = currentInput.isKeyActive(InputRow.Key.S);
        boolean left = currentInput.isKeyActive(InputRow.Key.A);
        boolean right = currentInput.isKeyActive(InputRow.Key.D);
        boolean jump = currentInput.isKeyActive(InputRow.Key.JUMP);
        boolean sneak = currentInput.isKeyActive(InputRow.Key.SNEAK);
        boolean sprintKey = currentInput.isKeyActive(InputRow.Key.SPRINT);
        float moveForward = axis(fwd, back);
        float moveStrafe = axis(left, right);
        if (sneak) {
            moveForward *= 0.3F;
            moveStrafe *= 0.3F;
        }

        if (this.isUsingItem() && !this.isRiding()) {
            moveStrafe *= 0.2F;
            moveForward *= 0.2F;
            this.sprintToggleTimer = 0;
        }

        boolean flag3 = (float) this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;
        if (this.onGround
                && !flag1
                && !flag2
                && moveForward >= f
                && !this.isSprinting()
                && flag3
                && !this.isUsingItem()
                && !this.isPotionActive(Potion.blindness)) {
            if (this.sprintToggleTimer <= 0 && !sprintKey) {
                this.sprintToggleTimer = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting()
                && moveForward >= f
                && flag3
                && !this.isUsingItem()
                && !this.isPotionActive(Potion.blindness)
                && sprintKey) {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (moveForward < f || this.isCollidedHorizontally || !flag3)) {
            this.setSprinting(false);
        }

        this.prevMoveForward = moveForward;
        this.prevSneak = sneak;

        this.setSneaking(sneak);
        this.moveForward = moveForward;
        this.moveStrafing = moveStrafe;
        this.isJumping = jump;
    }

    /** Prevent the simulator from pushing the real player or any other world entity. */
    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    /** Required: EntityLivingBase.moveEntityWithHeading is gated by isServerWorld. */
    @Override
    public boolean isServerWorld() {
        return true;
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
