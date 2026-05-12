package de.legoshi.parkourcalc.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ParkourCalculatorForge.MODID, version = ParkourCalculatorForge.VERSION, clientSideOnly = true, acceptableRemoteVersions = "*")
public class ParkourCalculatorForge {
    public static final String MODID = "parkourcalculator";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Wiring deferred until the LWJGL 2 ImGui bridge lands.
        // Core UI classes are on the classpath via `core-1.0.0.jar` and ready to be hooked up.
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
    }
}