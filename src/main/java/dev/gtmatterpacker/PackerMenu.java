package dev.gtmatterpacker;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.api.widget.Widget;
import com.recursive_pineapple.matter_manipulator.client.gui.RadialMenu;
import com.recursive_pineapple.matter_manipulator.client.gui.RadialMenu.RadialMenuClickHandler;
import com.recursive_pineapple.matter_manipulator.client.gui.RadialMenu.RadialMenuOption;

final class PackerMenu {

    static ModularWindow extend(ModularWindow window, UIBuildContext context) {
        ItemStack held = context.getPlayer()
            .getHeldItem();
        for (Widget widget : window.getChildren()) if (widget instanceof RadialMenu) {
            configure((RadialMenu) widget, PackerControl.active(held), PackerControl.loaded(held), action -> {
                if (action == PackerAction.LIBRARY) {
                    int slot = context.getPlayer().inventory.currentItem;
                    context.getPlayer()
                        .closeScreen();
                    Minecraft.getMinecraft()
                        .displayGuiScreen(new GuiPackageLibrary(slot));
                    LibraryNetwork.send(0, slot, 0, "", "");
                    return;
                }
                PackerNetwork.send(action, context.getPlayer().inventory.currentItem);
                context.getPlayer()
                    .closeScreen();
            });
        }
        return window;
    }

    static void configure(RadialMenu menu, boolean active, boolean loaded, Consumer<PackerAction> send) {
        RadialMenuOption mode = null;
        for (RadialMenuOption option : menu.options) if (text("mm.gui.set_mode").equals(option.label.get())) {
            mode = option;
            break;
        }
        if (mode == null) throw new IllegalStateException("Matter Manipulator mode selector not found");
        RadialMenuClickHandler original = mode.onClick;
        mode.onClick = (radial, option, button, twice) -> {
            original.onClick(radial, option, button, twice);
            wireModes(radial, send);
            radial.options.add(option("gtmatterpacker.mode", PackerAction.PACK_MODE, send));
        };
        if (!active) return;
        menu.options.clear();
        menu.options.add(mode);
        menu.options.add(
            option(
                loaded ? "gtmatterpacker.mark_target" : "gtmatterpacker.mark_source",
                loaded ? PackerAction.MARK_TARGET : PackerAction.MARK_SOURCE,
                send));
        menu.options.add(option(loaded ? "gtmatterpacker.deploy" : "gtmatterpacker.pack", PackerAction.EXECUTE, send));
        RadialMenuOption recovery = new RadialMenuOption();
        recovery.label = () -> text("gtmatterpacker.records");
        recovery.onClick = (radial, option, button, twice) -> {
            radial.options.clear();
            radial.options.add(option("gtmatterpacker.status", PackerAction.STATUS, send));
            radial.options.add(
                option(
                    loaded ? "gtmatterpacker.clear" : "gtmatterpacker.recover",
                    loaded ? PackerAction.CLEAR : PackerAction.RECOVER,
                    send));
            radial.options.add(option("gtmatterpacker.library", PackerAction.LIBRARY, send));
        };
        menu.options.add(recovery);
    }

    private static void wireModes(RadialMenu menu, Consumer<PackerAction> send) {
        String[] keys = { "geometry", "moving", "copying", "exchanging", "cables" };
        PackerAction[] actions = { PackerAction.GEOMETRY, PackerAction.MOVING, PackerAction.COPYING,
            PackerAction.EXCHANGING, PackerAction.CABLES };
        for (RadialMenuOption option : menu.options) for (int i = 0; i < keys.length; i++) {
            if (text("mm.gui." + keys[i]).equals(option.label.get())) {
                PackerAction action = actions[i];
                option.onClick = (radial, selected, button, twice) -> send.accept(action);
            }
        }
    }

    private static RadialMenuOption option(String key, PackerAction action, Consumer<PackerAction> send) {
        RadialMenuOption option = new RadialMenuOption();
        option.label = () -> text(key);
        option.onClick = (radial, selected, button, twice) -> send.accept(action);
        return option;
    }

    private static String text(String key) {
        return StatCollector.translateToLocal(key);
    }
}
