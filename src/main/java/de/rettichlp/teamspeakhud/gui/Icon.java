package de.rettichlp.teamspeakhud.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.MOD_ID;
import static net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED;
import static net.minecraft.resources.Identifier.fromNamespaceAndPath;

public enum Icon {

    CHANNEL_GREEN_SUBSCRIBED(0, 0),
    CHANNEL_GREEN(1, 0),
    CHANNEL_RED_SUBSCRIBED(2, 0),
    CHANNEL_RED(3, 0),
    CHANNEL_YELLOW_SUBSCRIBED(4, 0),
    CHANNEL_YELLOW(5, 0),
    CHANNEL_SUBSCRIBED(6, 0),
    CHANNEL_UNSUBSCRIBED(7, 0),

    PLAYER_ON(3, 1),
    PLAYER_OFF(2, 1),
    PLAYER_COMMANDER_ON(1, 1),
    PLAYER_COMMANDER_OFF(0, 1),

    HARDWARE_INPUT_MUTED(4, 1),
    INPUT_MUTED(5, 1),
    HARDWARE_OUTPUT_MUTED(6, 1),
    OUTPUT_MUTED(7, 1);

    private static final Identifier TEXTURE = fromNamespaceAndPath(MOD_ID, "textures/gui/sprites/teamspeak/icons.png");
    private static final int SIZE = 32;
    private static final int TEXTURE_SIZE = 256;

    private final int u;
    private final int v;

    Icon(int column, int row) {
        this.u = column * SIZE;
        this.v = row * SIZE;
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int renderSize) {
        if (renderSize == SIZE) {
            graphics.blit(GUI_TEXTURED, TEXTURE, x, y, this.u, this.v, SIZE, SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
            return;
        }

        float scale = renderSize / (float) SIZE;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scaleAround(scale, scale, x, y);
        graphics.blit(GUI_TEXTURED, TEXTURE, x, y, this.u, this.v, SIZE, SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        pose.popMatrix();
    }
}
