package de.rettichlp.teamspeakhud.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelListQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;

public class ChannelArgument extends AbstractTeamSpeakArgument implements ArgumentType<CompletableFuture<Channel>> {

    @Override
    public CompletableFuture<Channel> parse(@NonNull StringReader reader) throws CommandSyntaxException {
        String input = reader.readString();
        return new ChannelListQuery().send(teamSpeakClient)
                .thenApply(response -> response.data() == null ? null : findBestMatch(response.data(), input, Channel::getName)
                        .orElse(null));
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!teamSpeakClient.isConnected()) {
            return builder.buildFuture();
        }

        return new ChannelListQuery().send(teamSpeakClient)
                .thenApply(response -> buildSuggestions(builder, response.data() == null
                        ? List.of()
                        : response.data(), channel -> "\"" + channel.getName() + "\""));
    }

    public static ChannelArgument channel() {
        return new ChannelArgument();
    }
}
