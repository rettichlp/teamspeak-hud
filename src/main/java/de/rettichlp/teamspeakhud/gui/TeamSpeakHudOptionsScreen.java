package de.rettichlp.teamspeakhud.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.configuration;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;
import static java.awt.Color.GRAY;
import static java.awt.Color.WHITE;
import static java.lang.Math.clamp;
import static java.lang.Math.max;
import static net.minecraft.client.gui.components.Checkbox.getBoxSize;
import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
import static net.minecraft.network.chat.CommonComponents.EMPTY;
import static net.minecraft.network.chat.CommonComponents.GUI_DONE;
import static net.minecraft.network.chat.Component.translatable;
import static net.minecraft.resources.Identifier.withDefaultNamespace;

public class TeamSpeakHudOptionsScreen extends Screen {

    private static final Identifier POPUP_BACKGROUND_SPRITE = withDefaultNamespace("popup/background");

    private static final int PADDING = 16;
    private static final int CONTENT_WIDTH = 252;
    private static final int PANEL_WIDTH = CONTENT_WIDTH + PADDING * 2;
    private static final int GAP = 4;
    private static final int GAP_SECTION = 14;
    private static final int INPUT_HEIGHT = 20;
    private static final int MIN_DISPLAYED_MEMBERS = 1;
    private static final int MAX_DISPLAYED_MEMBERS = 100;

    private final Screen parent;
    private final Collection<TextLine> textLines = new ArrayList<>();

    private Checkbox enabledCheckbox;
    private Checkbox pokeNotificationsCheckbox;
    private Checkbox privateMessageNotificationsCheckbox;
    private Checkbox channelMessageNotificationsCheckbox;
    private EditBox manualApiKeyBox;
    private int maxDisplayedMembers;

    private boolean measuring;
    private int cursorY;
    private int dividerY;
    private int panelX;
    private int panelY;
    private int panelHeight;

    public TeamSpeakHudOptionsScreen(Screen parent) {
        super(translatable("tsh.options.title"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int contentX = this.panelX + PADDING;
        graphics.fill(contentX, this.dividerY, contentX + CONTENT_WIDTH, this.dividerY + 1, GRAY.getRGB());

        for (TextLine line : this.textLines) {
            graphics.text(this.font, line.text(), line.x(), line.y(), line.color());
        }
    }

    @Override
    public void onClose() {
        boolean wasEnabled = configuration.isEnabled();
        boolean enabled = this.enabledCheckbox.selected();
        String previousApiKey = configuration.getManualApiKey();
        String manualApiKey = this.manualApiKeyBox.getValue().strip();

        configuration.setEnabled(enabled);
        configuration.setPokeNotificationsEnabled(this.pokeNotificationsCheckbox.selected());
        configuration.setPrivateMessageNotificationsEnabled(this.privateMessageNotificationsCheckbox.selected());
        configuration.setChannelMessageNotificationsEnabled(this.channelMessageNotificationsCheckbox.selected());
        configuration.setMaxDisplayedMembers(this.maxDisplayedMembers);
        configuration.setManualApiKey(manualApiKey);
        configuration.saveToFile();

        // Only touch the connection if enablement changed, or a still-enabled mod got a different API key.
        if (enabled && (!wasEnabled || !manualApiKey.equals(previousApiKey))) {
            teamSpeakClient.stop();
            teamSpeakClient.start();
        } else if (!enabled && wasEnabled) {
            teamSpeakClient.stop();
        }

        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    protected void init() {
        this.textLines.clear();

        // First pass only measures the total content height (no widgets/text are created), so the panel can be centered on screen.
        this.measuring = true;
        this.cursorY = 0;
        buildLayout();
        this.panelHeight = this.cursorY + PADDING * 2;
        this.panelX = this.width / 2 - PANEL_WIDTH / 2;
        this.panelY = max(10, this.height / 2 - this.panelHeight / 2);

        // Second pass repeats the exact same steps, now actually placing widgets and text at their final coordinates.
        this.measuring = false;
        this.cursorY = this.panelY + PADDING;
        buildLayout();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(GUI_TEXTURED, POPUP_BACKGROUND_SPRITE, this.panelX, this.panelY, PANEL_WIDTH, this.panelHeight);
    }

    private void buildLayout() {
        int contentX = this.panelX + PADDING;

        advanceTitle(this.title);
        advanceGap(GAP_SECTION);

        this.enabledCheckbox = advanceCheckbox(translatable("tsh.options.enabled"), contentX, configuration.isEnabled());
        advanceGap(GAP);
        advanceDescription(translatable("tsh.options.enabled.description"), contentX);
        advanceGap(GAP_SECTION);

        this.pokeNotificationsCheckbox = advanceCheckbox(translatable("tsh.options.notifications.poke"), contentX, configuration.isPokeNotificationsEnabled());
        advanceGap(GAP);
        this.privateMessageNotificationsCheckbox = advanceCheckbox(translatable("tsh.options.notifications.private_message"), contentX, configuration.isPrivateMessageNotificationsEnabled());
        advanceGap(GAP);
        this.channelMessageNotificationsCheckbox = advanceCheckbox(translatable("tsh.options.notifications.channel_message"), contentX, configuration.isChannelMessageNotificationsEnabled());
        advanceGap(GAP);
        advanceDescription(translatable("tsh.options.notifications.description"), contentX);
        advanceGap(GAP_SECTION);

        advanceMaxDisplayedMembersSlider(contentX);
        advanceGap(GAP);
        advanceDescription(translatable("tsh.options.max_displayed_members.description"), contentX);
        advanceGap(GAP_SECTION);

        advanceLabel(translatable("tsh.options.manual_api_key"), contentX);
        advanceGap(2);
        this.manualApiKeyBox = advanceEditBox(contentX, translatable("tsh.options.manual_api_key"));
        if (this.manualApiKeyBox != null) {
            this.manualApiKeyBox.setMaxLength(64);
            this.manualApiKeyBox.setValue(configuration.getManualApiKey());
        }
        advanceGap(GAP);
        advanceDescription(translatable("tsh.options.manual_api_key.description"), contentX);
        advanceGap(GAP_SECTION + 2);

        advanceDoneButton();
    }

    private void advanceGap(int amount) {
        this.cursorY += amount;
    }

    private void advanceTitle(Component text) {
        if (!this.measuring) {
            FormattedCharSequence visual = text.getVisualOrderText();
            int x = this.width / 2 - this.font.width(visual) / 2;
            this.textLines.add(new TextLine(visual, x, this.cursorY, WHITE.getRGB()));
        }
        this.cursorY += this.font.lineHeight;
    }

    private void advanceLabel(Component text, int x) {
        if (!this.measuring) {
            this.textLines.add(new TextLine(text.getVisualOrderText(), x, this.cursorY, WHITE.getRGB()));
        }
        this.cursorY += this.font.lineHeight;
    }

    private void advanceDescription(FormattedText text, int x) {
        for (FormattedCharSequence line : this.font.split(text, CONTENT_WIDTH)) {
            if (!this.measuring) {
                this.textLines.add(new TextLine(line, x, this.cursorY, GRAY.brighter().getRGB()));
            }
            this.cursorY += this.font.lineHeight;
        }
    }

    private Checkbox advanceCheckbox(Component message, int x, boolean selected) {
        int height = getBoxSize(this.font);
        Checkbox checkbox = this.measuring ? null : addRenderableWidget(Checkbox.builder(message, this.font)
                .pos(x, this.cursorY)
                .selected(selected)
                .build());
        this.cursorY += height;
        return checkbox;
    }

    private EditBox advanceEditBox(int x, Component narrationMessage) {
        EditBox editBox = this.measuring ? null : addRenderableWidget(new EditBox(this.font, x, this.cursorY, CONTENT_WIDTH, INPUT_HEIGHT, narrationMessage));
        this.cursorY += INPUT_HEIGHT;
        return editBox;
    }

    /**
     * The slider's own label shows the current value (e.g. "Max shown members: 15"), so it needs no separate label line above it.
     */
    private void advanceMaxDisplayedMembersSlider(int x) {
        if (!this.measuring) {
            int initialValue = clamp(configuration.getMaxDisplayedMembers(), MIN_DISPLAYED_MEMBERS, MAX_DISPLAYED_MEMBERS);
            double normalized = (double) (initialValue - MIN_DISPLAYED_MEMBERS) / (MAX_DISPLAYED_MEMBERS - MIN_DISPLAYED_MEMBERS);

            addRenderableWidget(new AbstractSliderButton(x, this.cursorY, CONTENT_WIDTH, INPUT_HEIGHT, EMPTY, normalized) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    TeamSpeakHudOptionsScreen.this.maxDisplayedMembers = MIN_DISPLAYED_MEMBERS + (int) Math.round(this.value * (MAX_DISPLAYED_MEMBERS - MIN_DISPLAYED_MEMBERS));
                    this.setMessage(translatable("tsh.options.max_displayed_members", TeamSpeakHudOptionsScreen.this.maxDisplayedMembers));
                }

                @Override
                protected void applyValue() {
                }
            });
        }
        this.cursorY += INPUT_HEIGHT;
    }

    private void advanceDoneButton() {
        if (!this.measuring) {
            addRenderableWidget(Button.builder(GUI_DONE, _ -> onClose())
                    .pos(this.width / 2 - 75, this.cursorY)
                    .width(150)
                    .build());
        }
        this.cursorY += INPUT_HEIGHT;
    }

    private record TextLine(FormattedCharSequence text, int x, int y, int color) {
    }
}
