package de.rettichlp.teamspeakhud.integrations;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.rettichlp.teamspeakhud.gui.TeamSpeakHudOptionsScreen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return TeamSpeakHudOptionsScreen::new;
    }
}
