package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.SimulatedTicker;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fabric / MC 1.21.10 implementation of the per-tick simulator port.
 *
 * Owns a SimulatorEntity that's lazy-created on the first call that needs it,
 * because at mod-init time the player and world don't yet exist.
 */
public final class FabricSimulatedTicker implements SimulatedTicker {

    private SimulatorEntity entity;

    /** null means "use the player's current position when the entity is first created". */
    private de.legoshi.parkourcalc.core.sim.Vec3d pendingStart;

    @Override
    public void resetToStart() {
        ensureEntity().resetPlayer();
    }

    @Override
    public void applyInput(InputRow row) {
        SimulatorEntity e = ensureEntity();
        e.input.setData(row);
        if (row.getYaw() != null) {
            e.setYaw(e.getYaw() + row.getYaw());
        }
    }

    @Override
    public void tick() {
        ensureEntity().tick();
    }

    @Override
    public de.legoshi.parkourcalc.core.sim.Vec3d getCurrentPosition() {
        Vec3d p = ensureEntity().getEntityPos();
        return new de.legoshi.parkourcalc.core.sim.Vec3d(p.x, p.y, p.z);
    }

    @Override
    public de.legoshi.parkourcalc.core.sim.Vec3d getStartPosition() {
        if (entity != null) {
            Vec3d p = entity.startPosition;
            return new de.legoshi.parkourcalc.core.sim.Vec3d(p.x, p.y, p.z);
        }
        return pendingStart != null ? pendingStart : de.legoshi.parkourcalc.core.sim.Vec3d.ZERO;
    }

    @Override
    public void setStartPosition(de.legoshi.parkourcalc.core.sim.Vec3d pos) {
        if (entity != null) {
            entity.startPosition = new Vec3d(pos.x, pos.y, pos.z);
        } else {
            pendingStart = pos;
        }
    }

    @Override
    public void setStartFromPlayer() {
        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            Vec3d p = player.getEntityPos();
            setStartPosition(new de.legoshi.parkourcalc.core.sim.Vec3d(p.x, p.y, p.z));
        }
    }

    private SimulatorEntity ensureEntity() {
        if (entity == null) {
            entity = createEntity();
        }
        return entity;
    }

    private SimulatorEntity createEntity() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        PlayerEntity player = client.player;

        if (player == null || world == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }

        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : player.getEntityPos();

        return new SimulatorEntity(world, player.getGameProfile(), start, Vec3d.ZERO);
    }
}
