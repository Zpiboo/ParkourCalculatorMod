package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;

/**
 * Forge / MC 1.8.9 implementation of the Simulator port. Mirrors Forge12Simulator
 * but against 1.8.9's net.minecraft.util.Vec3 (xCoord/yCoord/zCoord accessors).
 */
public final class Forge8Simulator implements Simulator {

    private SimulatorEntity entity;

    /** null means "use the player's current position when the entity is first created". */
    private Vec3dCore pendingStart;

    @Override
    public void resetToStart() {
        ensureEntity().resetPlayer();
    }

    @Override
    public void applyInput(InputRow row) {
        SimulatorEntity e = ensureEntity();
        e.setInput(row);
        if (row.getYaw() != null) {
            e.rotationYaw += row.getYaw();
        }
    }

    @Override
    public void tick() {
        ensureEntity().onUpdate();
    }

    @Override
    public Vec3dCore getCurrentPosition() {
        SimulatorEntity e = ensureEntity();
        return new Vec3dCore(e.posX, e.posY, e.posZ);
    }

    @Override
    public Vec3dCore getStartPosition() {
        if (entity != null) {
            Vec3 p = entity.startPosition;
            return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
        }
        return pendingStart != null ? pendingStart : Vec3dCore.ZERO;
    }

    @Override
    public void setStartPosition(Vec3dCore pos) {
        if (entity != null) {
            entity.startPosition = new Vec3(pos.x, pos.y, pos.z);
        } else {
            pendingStart = pos;
        }
    }

    @Override
    public void setStartFromPlayer() {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            setStartPosition(new Vec3dCore(player.posX, player.posY, player.posZ));
        }
    }

    private SimulatorEntity ensureEntity() {
        if (entity == null) {
            entity = createEntity();
        }
        return entity;
    }

    private SimulatorEntity createEntity() {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;

        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }

        Vec3 start = pendingStart != null
                ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3(player.posX, player.posY, player.posZ);

        return new SimulatorEntity(world, player.getGameProfile(), start);
    }
}
