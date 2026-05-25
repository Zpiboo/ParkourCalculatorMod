package de.legoshi.parkourcalc.fabric.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {

    @Accessor("lastXClient") void pkc$setLastXClient(double v);

    @Accessor("lastYClient") void pkc$setLastYClient(double v);

    @Accessor("lastZClient") void pkc$setLastZClient(double v);

    @Accessor("lastYawClient") void pkc$setLastYawClient(float v);

    @Accessor("lastPitchClient") void pkc$setLastPitchClient(float v);

    @Accessor("ticksSinceLastPositionPacketSent") void pkc$setTicksSinceLastPositionPacketSent(int v);
}
