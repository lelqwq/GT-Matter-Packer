package dev.gtmatterpacker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(
    modid = "gtmatterpacker",
    name = "GT Matter Packer",
    version = "0.3.1",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:matter-manipulator@[0.0.51-GTNH];required-after:gregtech;required-after:ForgeMultipart")
public class GTMatterPacker {

    static final Logger LOG = LogManager.getLogger("gtmatterpacker");
    public static ItemPackedManipulator ITEM;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ITEM = new ItemPackedManipulator();
        GameRegistry.addRecipe(new UpgradeRecipe());
        PackerNetwork.init();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        PackerNetwork.clear();
    }
}
