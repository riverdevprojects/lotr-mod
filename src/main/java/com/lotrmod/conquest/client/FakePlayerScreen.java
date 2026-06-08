package com.lotrmod.conquest.client;

import com.lotrmod.conquest.network.C2SFakePlayerActionPacket;
import com.lotrmod.conquest.network.S2CFakePlayerScreenPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * Debug screen for controlling a fake player NPC.
 * Opened by right-clicking a FakePlayerEntity.
 * All actions are dispatched server-side via C2SFakePlayerActionPacket.
 */
public class FakePlayerScreen extends Screen {

    private S2CFakePlayerScreenPacket data;

    // Create guild
    private EditBox guildNameBox;
    private EditBox guildTagBox;

    // Join / war
    private EditBox joinGuildBox;
    private EditBox declareWarBox;

    // Deposit / withdraw
    private EditBox depositResBox;
    private EditBox depositAmtBox;
    private EditBox withdrawResBox;
    private EditBox withdrawAmtBox;

    private static final int BG      = 0xCC1A1A2E;
    private static final int BORDER  = 0xFFAA8800;
    private static final int HEADER  = 0xFFFFDD44;
    private static final int TEXT    = 0xFFE0E0E0;
    private static final int MUTED   = 0xFF999999;
    private static final int WAR     = 0xFFFF4444;
    private static final int GREEN   = 0xFF44FF88;

    public FakePlayerScreen(S2CFakePlayerScreenPacket data) {
        super(Component.literal("Fake Player: " + data.fakeName()));
        this.data = data;
    }

    /** Called by the server to refresh screen data without reopening. */
    public void refresh(S2CFakePlayerScreenPacket newData) {
        this.data = newData;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        clearWidgets();

        int panelW = Math.min(400, width - 40);
        int cx = (width - panelW) / 2;
        int y = 30;
        int fw = (panelW - 12) / 2 - 4; // field width for paired fields
        int bw = 90;

        // ── Guild Actions ─────────────────────────────────────────────────
        y += 18; // header row
        // Create guild row
        guildNameBox = makeBox(cx + 2, y, fw, "Guild Name");
        guildTagBox  = makeBox(cx + fw + 8, y, 50, "Tag");
        addRenderableWidget(Button.builder(Component.literal("Create Guild"),
            b -> send("CREATE_GUILD", guildNameBox.getValue(), guildTagBox.getValue(), 0))
            .pos(cx + fw + 64, y).size(bw, 16).build());
        y += 20;

        // Join guild row
        joinGuildBox = makeBox(cx + 2, y, panelW - bw - 16, "Guild to join");
        addRenderableWidget(Button.builder(Component.literal("Join Guild"),
            b -> send("JOIN_GUILD", joinGuildBox.getValue(), "", 0))
            .pos(cx + panelW - bw - 4, y).size(bw, 16).build());
        y += 20;

        // Leave / make master row
        addRenderableWidget(Button.builder(Component.literal("Leave Guild"),
            b -> send("LEAVE_GUILD", "", "", 0))
            .pos(cx + 2, y).size(bw, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Make Master"),
            b -> send("MAKE_MASTER", "", "", 0))
            .pos(cx + bw + 8, y).size(bw, 16).build());
        y += 22;

        // ── War ───────────────────────────────────────────────────────────
        y += 6; // section gap
        declareWarBox = makeBox(cx + 2, y, panelW - bw - 16, "Enemy guild name");
        addRenderableWidget(Button.builder(Component.literal("Declare War"),
            b -> send("DECLARE_WAR", declareWarBox.getValue(), "", 0))
            .pos(cx + panelW - bw - 4, y).size(bw, 16).build());
        y += 22;

        // ── Treasury ──────────────────────────────────────────────────────
        y += 6;
        depositResBox = makeBox(cx + 2, y, fw - 10, "Resource");
        depositAmtBox = makeBox(cx + fw - 6, y, 50, "Amt");
        addRenderableWidget(Button.builder(Component.literal("Deposit"),
            b -> sendAmount("DEPOSIT", depositResBox.getValue(), depositAmtBox.getValue()))
            .pos(cx + fw + 48, y).size(70, 16).build());
        y += 20;

        withdrawResBox = makeBox(cx + 2, y, fw - 10, "Resource");
        withdrawAmtBox = makeBox(cx + fw - 6, y, 50, "Amt");
        addRenderableWidget(Button.builder(Component.literal("Withdraw"),
            b -> sendAmount("WITHDRAW", withdrawResBox.getValue(), withdrawAmtBox.getValue()))
            .pos(cx + fw + 48, y).size(70, 16).build());
        y += 26;

        // ── Close ─────────────────────────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
            .pos(width / 2 - 40, height - 28).size(80, 20).build());
    }

    private EditBox makeBox(int x, int y, int w, String hint) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.literal(hint));
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private void send(String action, String arg1, String arg2, long amount) {
        PacketDistributor.sendToServer(new C2SFakePlayerActionPacket(
            data.fakeUUID(), data.fakeName(), action, arg1.trim(), arg2.trim(), amount));
    }

    private void sendAmount(String action, String resource, String amountStr) {
        long amt = 0;
        try { amt = Long.parseLong(amountStr.trim()); } catch (NumberFormatException ignored) {}
        send(action, resource, "", amt);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);

        int panelW = Math.min(400, width - 40);
        int panelH = height - 46;
        int px = (width - panelW) / 2;
        int py = 18;

        // Panel
        gfx.fill(px, py, px + panelW, py + panelH, BG);
        gfx.fill(px, py, px + panelW, py + 1, BORDER);
        gfx.fill(px, py + panelH - 1, px + panelW, py + panelH, BORDER);
        gfx.fill(px, py, px + 1, py + panelH, BORDER);
        gfx.fill(px + panelW - 1, py, px + panelW, py + panelH, BORDER);

        // Header
        gfx.fill(px, py, px + panelW, py + 14, 0xCC2A2A0E);
        gfx.drawString(font, "§e[FP] §f" + data.fakeName() + "  §7" + data.fakeUUID().toString().substring(0, 8) + "...", px + 4, py + 3, HEADER, false);

        int cx = px;
        int y = py + 18;

        // Guild status
        if (data.inGuild()) {
            gfx.drawString(font, "§aGuild: §f" + data.guildName() + " §7[" + data.guildTag() + "]", cx + 4, y, GREEN, false);
            if (!data.wars().isEmpty()) {
                gfx.drawString(font, "§cAt war: §f" + String.join(", ", data.wars()), cx + 4, y + 10, WAR, false);
            }
        } else {
            gfx.drawString(font, "§7No guild", cx + 4, y, MUTED, false);
        }
        y += data.inGuild() && !data.wars().isEmpty() ? 24 : 14;

        // Section labels
        gfx.drawString(font, "§eGuild Actions", cx + 4, y, HEADER, false);
        y += 16 + 60 + 4; // skip past buttons region
        gfx.drawString(font, "§eWar", cx + 4, y, HEADER, false);
        y += 16 + 28 + 4;
        gfx.drawString(font, "§eTreasury", cx + 4, y, HEADER, false);

        // Resource hint
        int tipY = py + panelH - 56;
        gfx.drawString(font, "§7Resources: bread cobblestone logs gold iron silver", cx + 4, tipY, MUTED, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
