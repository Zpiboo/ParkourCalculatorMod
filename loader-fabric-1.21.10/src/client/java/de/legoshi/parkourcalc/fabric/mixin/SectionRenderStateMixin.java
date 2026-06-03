package de.legoshi.parkourcalc.fabric.mixin;

import de.legoshi.parkourcalc.fabric.FabricParkourCalculator;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.SectionRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderState.class)
public class SectionRenderStateMixin {

    // Fires once per world render, right before translucent terrain (water/lava/stained glass) writes depth.
    @Inject(method = "renderSection", at = @At("HEAD"))
    private void parkourcalc$beforeTranslucent(BlockRenderLayerGroup group, CallbackInfo ci) {
        if (group == BlockRenderLayerGroup.TRANSLUCENT) {
            FabricParkourCalculator.renderWorldOverlayBeforeTranslucent();
        }
    }
}
