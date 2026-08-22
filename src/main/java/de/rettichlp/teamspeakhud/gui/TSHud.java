package de.rettichlp.teamspeakhud.gui;

import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.model.TeamSpeakUser;
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

        List<TeamSpeakUser> allTeamSpeakUsers = this.client.getTeamSpeakChannel().getMemberList();
        if (allTeamSpeakUsers.isEmpty()) {
            return;
        }

        // Cap how many rows are drawn, so a busy channel can't cover half the screen; anything beyond the cap is collapsed into a
        // single "+N more" row instead of being silently dropped.
        int maxDisplayed = max(1, configuration.getMaxDisplayedMembers());
        List<TeamSpeakUser> teamSpeakUsers = allTeamSpeakUsers.subList(0, min(allTeamSpeakUsers.size(), maxDisplayed));
        String moreText = allTeamSpeakUsers.size() > teamSpeakUsers.size()
                ? translatable("tsh.more_messages", allTeamSpeakUsers.size() - teamSpeakUsers.size()).getString()
                : null;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        int rowCount = teamSpeakUsers.size() + (moreText != null ? 1 : 0);
        int width = getWidth(font, teamSpeakUsers, moreText);
        int height = PADDING * 2 + ROW_HEIGHT * (rowCount + 1);

        int x = graphics.guiWidth() - 2 - width;
        int y = graphics.guiHeight() - 12 - PADDING - height; // 12 = chat input height

        int rowX = x + PADDING;
        int rowY = y + PADDING;

        int backgroundColor = black(minecraft.options.textBackgroundOpacity().get().floatValue());
        graphics.fill(x, y, x + width, y + height, backgroundColor);

        getChannelIcon().draw(graphics, rowX, rowY + ROW_HEIGHT / 2 - ICON_SIZE / 2, ICON_SIZE);
        graphics.text(font, getChannelName(), rowX + ICON_SIZE + GAP, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, GRAY.brighter().getRGB());

        for (TeamSpeakUser teamSpeakUser : teamSpeakUsers) {
            rowY += ROW_HEIGHT;
            getIcon(teamSpeakUser).draw(graphics, rowX, rowY + GAP, ICON_SIZE);
            graphics.text(font, teamSpeakUser.getNickname(), rowX + ICON_SIZE + GAP, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, WHITE.getRGB());
        }

        if (moreText != null) {
            rowY += ROW_HEIGHT;
            graphics.text(font, moreText, rowX, rowY + ROW_HEIGHT / 2 - font.lineHeight / 2, GRAY.brighter().getRGB());
        }
    }

    private int getWidth(@NonNull Font font, @NonNull Iterable<TeamSpeakUser> teamSpeakUsers, String moreText) {
        int contentWidth = ICON_SIZE + GAP + font.width(getChannelName());
        for (TeamSpeakUser member : teamSpeakUsers) {
            int rowWidth = ICON_SIZE + GAP + font.width(member.getNickname());
            contentWidth = max(contentWidth, rowWidth);
        }

        if (moreText != null) {
            contentWidth = max(contentWidth, font.width(moreText));
        }

        return contentWidth + PADDING * 2;
    }

    private Icon getChannelIcon() {
        boolean subscribed = this.client.getTeamSpeakChannel().isSubscribed();

        if (this.client.getTeamSpeakChannel().isFull()) {
            return subscribed ? CHANNEL_RED_SUBSCRIBED : CHANNEL_RED;
        }

        if (this.client.getTeamSpeakChannel().isPasswordProtected()) {
            return subscribed ? CHANNEL_YELLOW_SUBSCRIBED : CHANNEL_YELLOW;
        }

        return subscribed ? CHANNEL_GREEN_SUBSCRIBED : CHANNEL_GREEN;
    }

    private String getChannelName() {
        String channelName = this.client.getTeamSpeakChannel().getName();
        return channelName.isEmpty() ? "TeamSpeak" : channelName;
    }

    private Icon getIcon(@NonNull TeamSpeakUser member) {
        if (member.isLocallyMuted()) {
            return LOCALLY_MUTED;
        }

        if (member.isOutputHardwareDisabled()) {
            return HARDWARE_OUTPUT_MUTED;
        }

        if (member.isOutputMuted()) {
            return OUTPUT_MUTED;
        }

        if (member.isInputHardwareDisabled()) {
            return HARDWARE_INPUT_MUTED;
        }

        if (member.isInputMuted()) {
            return INPUT_MUTED;
        }

        if (member.isChannelCommander()) {
            return member.isTalking() ? PLAYER_COMMANDER_ON : PLAYER_COMMANDER_OFF;
        }

        return member.isTalking() ? PLAYER_ON : PLAYER_OFF;
    }
}
