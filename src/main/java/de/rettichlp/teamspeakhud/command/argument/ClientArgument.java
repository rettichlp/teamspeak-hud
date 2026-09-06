package de.rettichlp.teamspeakhud.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientDescriptionQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientListQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import org.jspecify.annotations.NonNull;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;
import static java.util.Locale.ROOT;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class ClientArgument extends AbstractTeamSpeakArgument implements ArgumentType<CompletableFuture<Client>> {

    @Override
    public CompletableFuture<Client> parse(@NonNull StringReader reader) throws CommandSyntaxException {
        String input = reader.readString();
        return new ClientListQuery().send(teamSpeakClient).thenCompose(response -> {
            List<Client> clients = response.data();
            if (clients == null || clients.isEmpty()) {
                return completedFuture(null);
            }

            Optional<Client> byNickname = findBestMatch(clients, input, Client::getNickname);
            return byNickname
                    .<CompletionStage<Client>>map(CompletableFuture::completedFuture)
                    .orElseGet(() -> resolveClientByDescription(clients.iterator(), input));
        });
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (!teamSpeakClient.isConnected()) {
            return builder.buildFuture();
        }

        return new ClientListQuery().send(teamSpeakClient)
                .thenApply(response -> buildSuggestions(builder, response.data() == null
                        ? List.of()
                        : response.data(), client -> "\"" + client.getNickname() + "\""));
    }

    public static ClientArgument client() {
        return new ClientArgument();
    }

    private static @NonNull CompletableFuture<Client> resolveClientByDescription(@NonNull Iterator<Client> remaining,
                                                                                 @NonNull String target) {
        if (!remaining.hasNext()) {
            return completedFuture(null);
        }

        Client candidate = remaining.next();
        return new ClientDescriptionQuery(candidate.getClientId()).send(teamSpeakClient).thenCompose(response -> {
            String description = response.data();
            if (description != null && !description.isBlank() && description.toLowerCase(ROOT).contains(target.toLowerCase(ROOT))) {
                return completedFuture(candidate);
            }

            return resolveClientByDescription(remaining, target);
        });
    }
}
