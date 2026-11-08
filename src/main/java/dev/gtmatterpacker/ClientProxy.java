package dev.gtmatterpacker;

import net.minecraft.client.Minecraft;

public class ClientProxy extends CommonProxy {

    @Override
    public void libraryResponse(LibraryNetwork.Response response) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(
            () -> {
                if (mc.currentScreen instanceof GuiPackageLibrary)
                    ((GuiPackageLibrary) mc.currentScreen).receive(response);
            });
    }
}
