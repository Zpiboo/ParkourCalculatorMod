package de.legoshi.parkourcalc.forge.sim;

import de.legoshi.parkourcalc.core.ports.Simulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.Vec3d;

/**
 * Forge / MC 1.12.2 implementation of the Simulator port. Lazy-creates the underlying
 * SimulatorEntity once the player and world exist (mod init runs before either).
 */
public final class Forge12Simulator implements Simulator {

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
            Vec3d p = entity.startPosition;
            return new Vec3dCore(p.x, p.y, p.z);
        }
        return pendingStart != null ? pendingStart : Vec3dCore.ZERO;
    }

    @Override
    public void setStartPosition(Vec3dCore pos) {
        if (entity != null) {
            entity.startPosition = new Vec3d(pos.x, pos.y, pos.z);
        } else {
            pendingStart = pos;
        }
    }

    @Override
    public void setStartFromPlayer() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
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
        WorldClient world = mc.world;
        EntityPlayerSP player = mc.player;

        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }

        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3d(player.posX, player.posY, player.posZ);

        return new SimulatorEntity(world, player.getGameProfile(), start);
    }
}
