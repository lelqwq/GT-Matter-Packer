package dev.gtmatterpacker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.registry.GameRegistry;

@Mod(
    modid = "gtmatterpacker",
    name = "GT Matter Packer",
    version = "1.1.1",
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "required-after:matter-manipulator@[0.1.75-GTNH];required-after:gregtech;required-after:ForgeMultipart")
public class GTMatterPacker {

    static final Logger LOG = LogManager.getLogger("gtmatterpacker");
    public static ItemPackedManipulator ITEM;
    @SidedProxy(clientSide = "dev.gtmatterpacker.ClientProxy", serverSide = "dev.gtmatterpacker.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ITEM = new ItemPackedManipulator();
        GameRegistry.addRecipe(new UpgradeRecipe());
        PackerNetwork.init();
        LibraryNetwork.init();
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        PackerNetwork.clear();
        LibraryNetwork.clear();
    }
}
