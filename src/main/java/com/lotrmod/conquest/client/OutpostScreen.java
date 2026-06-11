package com.lotrmod.conquest.client;

import com.lotrmod.conquest.network.C2SOutpostActionPacket;
import com.lotrmod.conquest.network.S2COutpostScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Outpost management screen — hire guards and abandon, opened by right-clicking a flag. */
public class OutpostScreen extends Screen {

    private S2COutpostScreenPacket data;

    private static final int BG     = 0xF21A1A2E;
    private static final int BORDER = 0xFFAA8800;
    private static final int HEADER = 0xFFFFDD44;
    private static final int TEXT   = 0xFFE0E0E0;
    private static final int GOLD   = 0xFFFFAA00;
    private static final int AQUA   = 0xFF55DDDD;

    public OutpostScreen(S2COutpostScreenPacket data) {
        super(Component.literal("Outpost"));
        this.data = data;
    }

    public void refresh(S2COutpostScreenPacket newData) {
        this.data = newData;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int cx = width / 2;
        int y = height / 2 - 30;

        boolean full = data.guardCount() >= data.maxGuards();

        Button hireGold = Button.builder(
            Component.literal("Hire Guard  (" + data.hireGoldCost() + " Gold)"),
            b -> send("HIRE_GOLD"))
            .pos(cx - 110, y).size(220, 20).build();
        hireGold.active = !full && data.treasuryGold() >= data.hireGoldCost();
        addRenderableWidget(hireGold);
        y += 24;

        Button hireSilver = Button.builder(
            Component.literal("Hire Guard  (" + data.hireSilverCost() + " Silver)"),
            b -> send("HIRE_SILVER"))
            .pos(cx - 110, y).size(220, 20).build();
        hireSilver.active = !full && data.treasurySilver() >= data.hireSilverCost();
        addRenderableWidget(hireSilver);
        y += 28;

        if (data.canManage()) {
            addRenderableWidget(Button.builder(
                Component.literal("Abandon Outpost"),
                b -> { send("ABANDON"); onClose(); })
                .pos(cx - 110, y).size(220, 20).build());
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(cx - 40, y + 4).size(80, 20).build());
    }

    private void send(String action) {
        PacketDistributor.sendToServer(new C2SOutpostActionPacket(data.pos(), action));
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int panelW = 260;
        int panelH = 150;
        int px = (width - panelW) / 2;
        int py = height / 2 - 78;

        gfx.fill(px, py, px + panelW, py + panelH, BG);
        gfx.fill(px, py, px + panelW, py + 1, BORDER);
        gfx.fill(px, py + panelH - 1, px + panelW, py + panelH, BORDER);
        gfx.fill(px, py, px + 1, py + panelH, BORDER);
        gfx.fill(px + panelW - 1, py, px + panelW, py + panelH, BORDER);

        String title = data.guildName().isEmpty() ? "Outpost" : data.guildName() + " Outpost";
        gfx.drawCenteredString(font, title, width / 2, py + 6, HEADER);
        gfx.drawCenteredString(font, "Guards: " + data.guardCount() + " / " + data.maxGuards(),
            width / 2, py + 20, TEXT);
        gfx.drawCenteredString(font, "Treasury  Gold: " + data.treasuryGold() + "   Silver: " + data.treasurySilver(),
            width / 2, py + 32, data.canManage() ? GOLD : AQUA);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
