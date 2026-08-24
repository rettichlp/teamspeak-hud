package de.rettichlp.teamspeakhud.gui;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_GREEN;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_GREEN_SUBSCRIBED;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_RED;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_RED_SUBSCRIBED;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_YELLOW;
import static de.rettichlp.teamspeakhud.gui.Icon.CHANNEL_YELLOW_SUBSCRIBED;
import static de.rettichlp.teamspeakhud.gui.Icon.HARDWARE_INPUT_MUTED;
import static de.rettichlp.teamspeakhud.gui.Icon.HARDWARE_OUTPUT_MUTED;
import static de.rettichlp.teamspeakhud.gui.Icon.INPUT_MUTED;
import static de.rettichlp.teamspeakhud.gui.Icon.LOCALLY_MUTED;
import static de.rettichlp.teamspeakhud.gui.Icon.OUTPUT_MUTED;
import static de.rettichlp.teamspeakhud.gui.Icon.PLAYER_COMMANDER_OFF;
import static de.rettichlp.teamspeakhud.gui.Icon.PLAYER_COMMANDER_ON;
import static de.rettichlp.teamspeakhud.gui.Icon.PLAYER_OFF;
import static de.rettichlp.teamspeakhud.gui.Icon.PLAYER_ON;
import static java.awt.Color.GRAY;
import static java.awt.Color.WHITE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.network.chat.Component.translatable;
import static net.minecraft.util.ARGB.black;

@RequiredArgsConstructor
public class TSHud implements HudElement {

    private static final int PADDING = 4;
    private static final int GAP = 2;
    private static final int ROW_HEIGHT = 9 + 2 * GAP;
    private static final int ICON_SIZE = 9;

    private final TeamSpeakClient client;

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull DeltaTracker deltaTracker) {
        if (!configuration.isEnabled() || !this.client.isConnected()) {
            return;
        }

        List<Client> allTeamSpeakUsers = this.client.getChannel().getClientList();
        if (allTeamSpeakUsers.isEmpty()) {
            return;
        }

        // cap how many rows are drawn, so a busy channel can't cover half the screen
        int maxDisplayed = max(1, configuration.getMaxDisplayedMembers());
        List<Client> clients = allTeamSpeakUsers.subList(0, min(allTeamSpeakUsers.size(), maxDisplayed));
        String moreText = allTeamSpeakUsers.size() > clients.size()
                ? translatable("tsh.more_messages", allTeamSpeakUsers.size() - clients.size()).getString()
                : null;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        int rowCount = clients.size() + (moreText != null ? 1 : 0);
        int width = getWidth(font, clients, moreText);
        int height = PADDING * 2 + ROW_HEIGHT * (rowCount + 1);

        int x = graphics.guiWidth() - 2 - width;
        int y = graphics.guiHeight() - 12 - PADDING - height; // 12 = chat input height

        int rowX = x + PADDING;
        int rowY = y + PADDING;

        int backgroundColor = black(minecraft.options.textBackgroundOpacity().get().floatValue());
        graphics.fill(x, y, x + width, y + height, backgroundColor);

        getChannelIcon().draw(graphics, rowX, rowY + ROW_HEIGHT / 2 - ICON_SIZE / 2, ICON_SIZE);
        graphics.text(font, getChannelName(), rowX + ICON_SIZE + GAP, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, GRAY.brighter().getRGB());

        for (Client client : clients) {
            rowY += ROW_HEIGHT;
            getIcon(client).draw(graphics, rowX, rowY + GAP, ICON_SIZE);
            graphics.text(font, client.getNickname(), rowX + ICON_SIZE + GAP, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, WHITE.getRGB());
        }

        if (moreText != null) {
            rowY += ROW_HEIGHT;
            graphics.text(font, moreText, rowX, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, GRAY.brighter().getRGB());
        }
    }

    private int getWidth(@NonNull Font font, @NonNull Iterable<Client> clients, String moreText) {
        int contentWidth = ICON_SIZE + GAP + font.width(getChannelName());
        for (Client client : clients) {
            int rowWidth = ICON_SIZE + GAP + font.width(client.getNickname());
            contentWidth = max(contentWidth, rowWidth);
        }

        if (moreText != null) {
            contentWidth = max(contentWidth, font.width(moreText));
        }

        return contentWidth + PADDING * 2;
    }

    private Icon getChannelIcon() {
        boolean subscribed = this.client.getChannel().isSubscribed();

        if (this.client.getChannel().isFull()) {
            return subscribed ? CHANNEL_RED_SUBSCRIBED : CHANNEL_RED;
        }

        if (this.client.getChannel().isPasswordProtected()) {
            return subscribed ? CHANNEL_YELLOW_SUBSCRIBED : CHANNEL_YELLOW;
        }

        return subscribed ? CHANNEL_GREEN_SUBSCRIBED : CHANNEL_GREEN;
    }

    private String getChannelName() {
        String channelName = this.client.getChannel().getName();
        return channelName.isEmpty() ? "TeamSpeak" : channelName;
    }

    private Icon getIcon(Client client) {
        if (client.isLocallyMuted()) {
            return LOCALLY_MUTED;
        }

        if (client.isOutputHardwareDisabled()) {
            return HARDWARE_OUTPUT_MUTED;
        }

        if (client.isOutputMuted()) {
            return OUTPUT_MUTED;
        }

        if (client.isInputHardwareDisabled()) {
            return HARDWARE_INPUT_MUTED;
        }

        if (client.isInputMuted()) {
            return INPUT_MUTED;
        }

        if (client.isChannelCommander()) {
            return client.isTalking() ? PLAYER_COMMANDER_ON : PLAYER_COMMANDER_OFF;
        }

        return client.isTalking() ? PLAYER_ON : PLAYER_OFF;
    }
}
