package dev.gtmatterpacker;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.StatCollector;

import org.junit.Test;

import com.recursive_pineapple.matter_manipulator.client.gui.RadialMenu;
import com.recursive_pineapple.matter_manipulator.client.gui.RadialMenu.RadialMenuOption;

public class PackerMenuTest {

    private RadialMenu base() {
        RadialMenu menu = new RadialMenu();
        RadialMenuOption modes = option("mm.gui.set_mode");
        modes.onClick = (radial, selected, button, twice) -> {
            radial.options.clear();
            for (String name : new String[] { "geometry", "moving", "copying", "exchanging", "cables" }) {
                RadialMenuOption leaf = option("mm.gui." + name);
                leaf.onClick = (m, o, b, t) -> fail("Original network callback should be replaced");
                radial.options.add(leaf);
            }
        };
        menu.options.add(modes);
        menu.options.add(option("original-option"));
        return menu;
    }

    private RadialMenuOption option(String key) {
        RadialMenuOption option = new RadialMenuOption();
        option.label = () -> StatCollector.translateToLocal(key);
        return option;
    }

    private void click(RadialMenu menu, int index) {
        RadialMenuOption o = menu.options.get(index);
        o.onClick.onClick(menu, o, 0, false);
    }

    @Test
    public void packingAppearsAlongsideAllFiveOriginalModes() {
        RadialMenu menu = base();
        List<PackerAction> sent = new ArrayList<>();
        PackerMenu.configure(menu, false, false, sent::add);
        assertEquals(2, menu.options.size());
        click(menu, 0);
        assertEquals(6, menu.options.size());
        click(menu, 5);
        assertEquals(PackerAction.PACK_MODE, sent.get(0));
        click(menu, 1);
        assertEquals(PackerAction.MOVING, sent.get(1));
    }

    @Test
    public void emptyModeOffersMarkSourceAndExecution() {
        RadialMenu menu = base();
        List<PackerAction> sent = new ArrayList<>();
        PackerMenu.configure(menu, true, false, sent::add);
        assertEquals(4, menu.options.size());
        click(menu, 1);
        click(menu, 2);
        assertEquals(PackerAction.MARK_SOURCE, sent.get(0));
        assertEquals(PackerAction.EXECUTE, sent.get(1));
        click(menu, 3);
        click(menu, 1);
        assertEquals(PackerAction.RECOVER, sent.get(2));
    }

    @Test
    public void loadedModeOffersMarkTargetAndClearConsumedReference() {
        RadialMenu menu = base();
        List<PackerAction> sent = new ArrayList<>();
        PackerMenu.configure(menu, true, true, sent::add);
        click(menu, 1);
        click(menu, 2);
        assertEquals(PackerAction.MARK_TARGET, sent.get(0));
        assertEquals(PackerAction.EXECUTE, sent.get(1));
        click(menu, 3);
        click(menu, 1);
        assertEquals(PackerAction.CLEAR, sent.get(2));
    }
}
