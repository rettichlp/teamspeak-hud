package de.rettichlp.teamspeakhud;

import de.rettichlp.teamspeakhud.configuration.Configuration;
import de.rettichlp.teamspeakhud.gui.TSHud;
import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

import static net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED;
import static net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING;
import static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast;
import static net.minecraft.resources.Identifier.fromNamespaceAndPath;
import static org.slf4j.LoggerFactory.getLogger;

public class TeamSpeakHud implements ModInitializer {

    public static final String MOD_ID = "teamspeak-hud";
    public static final String MOD_NAME = "TeamSpeak HUD";

    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = getLogger(MOD_ID);

    public static final Configuration configuration = new Configuration().loadFromFile();

    public static final TeamSpeakClient teamSpeakClient = new TeamSpeakClient();

    @Override
    public void onInitialize() {
        addLast(fromNamespaceAndPath(MOD_ID, "channel_members"), new TSHud(teamSpeakClient));

        CLIENT_STARTED.register(_ -> {
            if (configuration.isEnabled()) {
                teamSpeakClient.start();
            }
        });

        CLIENT_STOPPING.register(_ -> teamSpeakClient.shutdown());
    }
}
