package com.lotrmod.conquest.client;

import com.lotrmod.conquest.network.C2SGuildActionPacket;
import com.lotrmod.conquest.network.S2CGuildDataPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side guild information screen.
 * Opened via /guild ui — server sends S2CGuildDataPacket which opens this screen.
 */
public class GuildScreen extends Screen {

    private S2CGuildDataPacket data;
    private int scrollOffset = 0;
    private static final int LINE_HEIGHT = 12;
    private static final int PANEL_COLOR = 0xF21A1A2E;
    private static final int BORDER_COLOR = 0xFFAA8800;
    private static final int HEADER_COLOR = 0xFFFFDD44;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int MUTED_COLOR = 0xFF999999;
    private static final int WAR_COLOR = 0xFFFF4444;
    private static final int GOLD_COLOR = 0xFFFFAA00;

    private final List<ScreenLine> lines = new ArrayList<>();

    public GuildScreen(S2CGuildDataPacket data) {
        super(Component.literal("Guild Info: " + data.guildName()));
        this.data = data;
    }

    /** Refresh in place (e.g. after a deposit) without reopening. */
    public void refresh(S2CGuildDataPacket newData) {
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
        buildLines();

        // Master/officer footer: deposit-all buttons for each resource.
        if (data.canManage()) {
            String[] res    = {"bread", "cobblestone", "logs", "gold", "iron", "silver"};
            String[] labels = {"+Bread", "+Cobble", "+Logs", "+Gold", "+Iron", "+Silver"};
            int btnW = 76, gap = 4;
            int totalW = btnW * 3 + gap * 2;
            int startX = (width - totalW) / 2;
            for (int i = 0; i < 6; i++) {
                int col = i % 3, row = i / 3;
                int x = startX + col * (btnW + gap);
                int y = (row == 0 ? height - 78 : height - 56);
                final String r = res[i];
                addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> deposit(r))
                    .pos(x, y).size(btnW, 18).build());
            }
        }

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(width / 2 - 40, height - 28)
            .size(80, 20)
            .build());
    }

    private void deposit(String resource) {
        PacketDistributor.sendToServer(new C2SGuildActionPacket("DEPOSIT", resource));
    }

    private void buildLines() {
        lines.clear();
        lines.add(new ScreenLine("  " + data.guildName() + " [" + data.guildTag() + "]", HEADER_COLOR));
        lines.add(new ScreenLine("  Faction: " + data.factionId() + "  |  Join: " + data.joinMode(), MUTED_COLOR));
        lines.add(new ScreenLine("  Online Day: " + data.onlineDay(), MUTED_COLOR));
        lines.add(new ScreenLine("", TEXT_COLOR));

        lines.add(new ScreenLine("  Guild Master", GOLD_COLOR));
        lines.add(new ScreenLine("    " + data.masterName(), TEXT_COLOR));
        lines.add(new ScreenLine("", TEXT_COLOR));

        if (!data.officerNames().isEmpty()) {
            lines.add(new ScreenLine("  Officers (" + data.officerNames().size() + ")", GOLD_COLOR));
            for (String o : data.officerNames()) lines.add(new ScreenLine("    " + o, TEXT_COLOR));
            lines.add(new ScreenLine("", TEXT_COLOR));
        }

        lines.add(new ScreenLine("  Members (" + data.memberNames().size() + ")", HEADER_COLOR));
        for (String m : data.memberNames()) lines.add(new ScreenLine("    " + m, TEXT_COLOR));
        lines.add(new ScreenLine("", TEXT_COLOR));

        lines.add(new ScreenLine("  Territory  -  " + data.bannerCount() + " claim banners", GOLD_COLOR));
        lines.add(new ScreenLine("", TEXT_COLOR));

        lines.add(new ScreenLine("  Treasury", GOLD_COLOR));
        lines.add(new ScreenLine("    Bread:        " + data.bread(),        TEXT_COLOR));
        lines.add(new ScreenLine("    Cobblestone:  " + data.cobblestone(),  TEXT_COLOR));
        lines.add(new ScreenLine("    Logs:         " + data.logs(),         TEXT_COLOR));
        lines.add(new ScreenLine("    Gold:         " + data.gold(),         TEXT_COLOR));
        lines.add(new ScreenLine("    Iron:         " + data.iron(),         TEXT_COLOR));
        lines.add(new ScreenLine("    Silver:       " + data.silver(),       TEXT_COLOR));
        lines.add(new ScreenLine("", TEXT_COLOR));

        if (data.warOpponents().isEmpty()) {
            lines.add(new ScreenLine("  No active wars.", MUTED_COLOR));
        } else {
            lines.add(new ScreenLine("  At War with:", WAR_COLOR));
            for (String w : data.warOpponents()) lines.add(new ScreenLine("    " + w, WAR_COLOR));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int panelW = Math.min(360, width - 40);
        // Leave a taller footer for the deposit buttons when the viewer can manage the treasury.
        int panelH = height - (data.canManage() ? 100 : 60);
        int panelX = (width - panelW) / 2;
        int panelY = 20;

        // Panel background + border
        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_COLOR);
        gfx.fill(panelX, panelY, panelX + panelW, panelY + 1, BORDER_COLOR);
        gfx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, BORDER_COLOR);
        gfx.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER_COLOR);
        gfx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, BORDER_COLOR);

        // Title bar
        gfx.fill(panelX, panelY, panelX + panelW, panelY + 14, 0xCC2A2A0E);
        gfx.drawString(font, title, panelX + 6, panelY + 3, HEADER_COLOR, true);

        // Scrollable content area
        int contentX = panelX + 4;
        int contentY = panelY + 18;
        int contentH = panelH - 36;
        int visibleLines = contentH / LINE_HEIGHT;

        // Clamp scroll
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        gfx.enableScissor(panelX + 1, contentY, panelX + panelW - 1, contentY + contentH);
        for (int i = 0; i < visibleLines && (i + scrollOffset) < lines.size(); i++) {
            ScreenLine line = lines.get(i + scrollOffset);
            gfx.drawString(font, line.text(), contentX, contentY + i * LINE_HEIGHT, line.color(), true);
        }
        gfx.disableScissor();

        // Scroll indicator
        if (lines.size() > visibleLines) {
            int barH = Math.max(10, contentH * visibleLines / lines.size());
            int barY = contentY + (int)((long)(contentH - barH) * scrollOffset / maxScroll);
            gfx.fill(panelX + panelW - 4, barY, panelX + panelW - 1, barY + barH, 0xFFAAAAAA);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, scrollOffset - (int) scrollY);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record ScreenLine(String text, int color) {}
}
