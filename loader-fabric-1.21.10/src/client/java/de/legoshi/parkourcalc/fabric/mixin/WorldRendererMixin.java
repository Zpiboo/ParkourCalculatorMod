package de.legoshi.parkourcalc.fabric.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    // Captured here, rendered from SectionRenderStateMixin just before the translucent terrain pass
    // so path boxes stay visible through water, lava, and stained/tinted glass while opaque blocks
    // still occlude them.
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderWorld(
            ObjectAllocator allocator,
            RenderTickCounter tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f matrix4f,
            Matrix4f projectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci
    ) {
        FabricParkourCalculator.captureWorldMatrix(positionMatrix);
    }
}