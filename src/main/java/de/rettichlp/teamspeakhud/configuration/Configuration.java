package de.rettichlp.teamspeakhud.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Data;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.MOD_ID;
import static java.nio.file.Files.newBufferedReader;
import static java.nio.file.Files.newBufferedWriter;

@Data
public class Configuration {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");

    /**
     * Whether the mod is enabled at all. Also controls whether the HUD is rendered, since there is nothing else the connection to
     * TeamSpeak is used for.
     */
    private boolean enabled = true;

    /**
     * Whether a toast notification is shown when the local user is poked or receives a TeamSpeak chat message.
     */
    private boolean notificationsEnabled = true;

    /**
     * Manually configured ClientQuery API key. Empty means the key should be auto-resolved from clientquery.ini.
     */
    private String manualApiKey = "";

    /**
     * Caps how many channel members the HUD list shows at once, so a busy channel doesn't cover half the screen. Any members beyond
     * this are collapsed into a single "+N more" row instead of being drawn individually.
     */
    private int maxDisplayedMembers = 15;

    public Configuration loadFromFile() {
        File file = CONFIG_PATH.toFile();

        // create a new config if the file does not exist or is empty
        if (!file.exists() || file.length() == 0) {
            LOGGER.info("Config file does not exist or is empty, creating new one at {}", CONFIG_PATH);
            saveToFile();
            return this;
        }

        // load existing config
        try {
            Reader reader = newBufferedReader(CONFIG_PATH);
            Configuration configuration = GSON.fromJson(reader, Configuration.class);
            LOGGER.info("Loaded configuration: {}", configuration);
            return configuration;
        } catch (Exception e) {
            LOGGER.error("Failed to load config from {}", CONFIG_PATH, e);
        }

        // fallback
        LOGGER.warn("Failed to load config, using default values");
        saveToFile();

        return this;
    }

    public void saveToFile() {
        try (Writer writer = newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
            LOGGER.info("Saved config to {}", CONFIG_PATH);
        } catch (IOException e) {
            LOGGER.error("Failed to save config to {}", CONFIG_PATH, e);
        }
    }
}
