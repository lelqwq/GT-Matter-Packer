package dev.gtmatterpacker;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;

public final class GuiPackageLibrary extends GuiScreen {

    private final int slot;
    private int page = 0, pages = 1, selectedRow = -1;
    private String activeId = "", message = "";
    private List<PackageStore.PackageInfo> entries = Collections.emptyList();
    private GuiTextField name;

    GuiPackageLibrary(int slot) {
        this.slot = slot;
    }

    private String tr(String key) {
        return StatCollector.translateToLocal("gtmatterpacker.library." + key);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int left = width / 2 - 150, top = Math.max(12, height / 2 - 110);
        buttonList.clear();
        for (int i = 0; i < LibraryNetwork.PAGE_SIZE; i++)
            buttonList.add(new GuiButton(10 + i, left, top + 20 + i * 18, 300, 18, ""));
        buttonList.add(new GuiButton(1, left, top + 166, 62, 20, tr("previous")));
        buttonList.add(new GuiButton(2, left + 65, top + 166, 62, 20, tr("next")));
        buttonList.add(new GuiButton(3, left + 130, top + 166, 83, 20, tr("select")));
        buttonList.add(new GuiButton(4, left + 216, top + 166, 84, 20, tr("stash")));
        name = new GuiTextField(fontRendererObj, left, top + 192, 213, 20);
        name.setMaxStringLength(64);
        buttonList.add(new GuiButton(5, left + 216, top + 192, 84, 20, tr("rename")));
        updateButtons();
    }

    void receive(LibraryNetwork.Response response) {
        if (response.message.startsWith("Error / 错误:")) {
            message = response.message;
            return;
        }
        page = response.page;
        pages = response.pages;
        entries = response.entries;
        activeId = response.selected;
        message = response.message;
        selectedRow = -1;
        if (name != null) name.setText("");
        updateButtons();
    }

    private void updateButtons() {
        if (buttonList == null) return;
        for (Object object : buttonList) {
            GuiButton button = (GuiButton) object;
            if (button.id >= 10 && button.id < 18) {
                int i = button.id - 10;
                button.visible = i < entries.size();
                if (button.visible) {
                    PackageStore.PackageInfo entry = entries.get(i);
                    String label = (i == selectedRow ? "> " : "") + (entry.id.equals(activeId) ? "[*] " : "")
                        + "["
                        + entry.id.substring(0, 8)
                        + "] "
                        + entry.name
                        + " ["
                        + entry.state
                        + "]";
                    button.displayString = fontRendererObj.trimStringToWidth(label, 288);
                }
            }
            if (button.id == 1) button.enabled = page > 0;
            if (button.id == 2) button.enabled = page + 1 < pages;
            if (button.id == 3 || button.id == 5) button.enabled = selectedRow >= 0 && selectedRow < entries.size()
                && "PACKED".equals(entries.get(selectedRow).state);
            if (button.id == 4) button.enabled = !activeId.isEmpty();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 10 && button.id < 18) {
            selectedRow = button.id - 10;
            name.setText(entries.get(selectedRow).name);
            updateButtons();
            return;
        }
        if (button.id == 1 && page > 0) LibraryNetwork.send(0, slot, page - 1, "", "");
        if (button.id == 2 && page + 1 < pages) LibraryNetwork.send(0, slot, page + 1, "", "");
        if (button.id == 3 && selectedRow >= 0) LibraryNetwork.send(1, slot, page, entries.get(selectedRow).id, "");
        if (button.id == 4) LibraryNetwork.send(3, slot, page, "", "");
        if (button.id == 5 && selectedRow >= 0)
            LibraryNetwork.send(2, slot, page, entries.get(selectedRow).id, name.getText());
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (name.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        name.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        name.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        int top = Math.max(12, height / 2 - 110);
        drawCenteredString(fontRendererObj, tr("title") + " " + (page + 1) + "/" + pages, width / 2, top + 5, 0xFFFFFF);
        super.drawScreen(x, y, partial);
        name.drawTextBox();
        if (!message.isEmpty()) drawCenteredString(fontRendererObj, message, width / 2, top + 215, 0xFFFF80);
    }
}
