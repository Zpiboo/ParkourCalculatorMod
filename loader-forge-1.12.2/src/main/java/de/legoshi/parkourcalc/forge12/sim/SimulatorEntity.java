package de.legoshi.parkourcalc.forge12.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** MCP 39: moveForward is the forward input, moveVertical is the swim/fly axis. */
public class SimulatorEntity extends EntityPlayer {

    public Vec3d startPosition;

    private InputRow currentInput = new InputRow();

    // sprintToggleTimer mirrors EntityPlayerSP. The prev* fields stand in for MC's
    // persistent movementInput so we can reconstruct its pre-updatePlayerMoveState
    // `flag1`/`flag2` snapshot at tick start.
    private int sprintToggleTimer = 0;
    private float prevMoveForward = 0.0F;
    private boolean prevSneak = false;

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
        this.sprintToggleTimer = 0;
        this.prevMoveForward = 0.0F;
        this.prevSneak = false;
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
    }

    @Override
    public void onLivingUpdate() {
        applyMovementInput();
        super.onLivingUpdate();
    }

    /** Mirrors EntityPlayerSP.onLivingUpdate sprint block (MCP stable-39). */
    private void applyMovementInput() {
        if (this.sprintToggleTimer > 0) {
            --this.sprintToggleTimer;
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

        if (this.isHandActive() && !this.isRiding()) {
            moveStrafe *= 0.2F;
            moveForward *= 0.2F;
            this.sprintToggleTimer = 0;
        }

        boolean flag4 = (float) this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;
        if (this.onGround && !flag1 && !flag2 && moveForward >= f && !this.isSprinting() && flag4 && !this.isHandActive() && !this.isPotionActive(MobEffects.BLINDNESS)) {
            if (this.sprintToggleTimer <= 0 && !sprintKey) {
                this.sprintToggleTimer = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting() && moveForward >= f && flag4 && !this.isHandActive() && !this.isPotionActive(MobEffects.BLINDNESS) && sprintKey) {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (moveForward < f || this.collidedHorizontally || !flag4)) {
            this.setSprinting(false);
        }

        this.prevMoveForward = moveForward;
        this.prevSneak = sneak;

        this.setSneaking(sneak);
        this.moveForward = moveForward;
        this.moveVertical = 0.0F;
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

    @Override
    public boolean isCreative() {
        return false;
    }

    /** Required: EntityLivingBase.func_191986_a (travel) is gated by isServerWorld. */
    @Override
    public boolean isServerWorld() {
        return true;
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
