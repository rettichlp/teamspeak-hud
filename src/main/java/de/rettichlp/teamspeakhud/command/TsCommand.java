package de.rettichlp.teamspeakhud.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import de.rettichlp.teamspeakhud.teamspeak.TeamSpeakClient;
import de.rettichlp.teamspeakhud.teamspeak.command.ChannelListQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientDescriptionQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientListQuery;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientMoveQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;
import static java.util.Locale.ROOT;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static net.minecraft.network.chat.Component.translatable;

public class TsCommand {

    public static void register(@NonNull CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ts")
                .then(literal("join")
                        .then(argument("channel", greedyString())
                                .suggests(TsCommand::suggestChannels)
                                .executes(context -> {
                                    String channelName = getString(context, "channel");
                                    FabricClientCommandSource source = context.getSource();
                                    TeamSpeakClient client = teamSpeakClient;

                                    if (!client.isConnected()) {
                                        source.sendError(translatable("tsh.command.not_connected"));
                                        return SINGLE_SUCCESS;
                                    }

                                    resolveChannel(client, channelName).thenAccept(channel -> {
                                        if (channel == null) {
                                            source.sendError(translatable("tsh.command.channel_not_found", channelName));
                                            return;
                                        }

                                        move(client, client.getOwnClientId(), channel.getId(), source, "tsh.command.join", channelName);
                                    });

                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("move")
                        .then(argument("target", string())
                                .suggests(TsCommand::suggestClients)
                                .then(argument("channel", greedyString())
                                        .suggests(TsCommand::suggestChannels)
                                        .executes(context -> {
                                            String target = getString(context, "target");
                                            String channelName = getString(context, "channel");
                                            FabricClientCommandSource source = context.getSource();
                                            TeamSpeakClient client = teamSpeakClient;

                                            if (!client.isConnected()) {
                                                source.sendError(translatable("tsh.command.not_connected"));
                                                return SINGLE_SUCCESS;
                                            }

                                            resolveChannel(client, channelName).thenAccept(channel -> {
                                                if (channel == null) {
                                                    source.sendError(translatable("tsh.command.channel_not_found", channelName));
                                                    return;
                                                }

                                                resolveClient(client, target).thenAccept(resolvedClient -> {
                                                    if (resolvedClient == null) {
                                                        source.sendError(translatable("tsh.command.client_not_found", target));
                                                        return;
                                                    }

                                                    move(client, resolvedClient.getClientId(), channel.getId(), source, "tsh.command.move", target, channelName);
                                                });
                                            });

                                            return SINGLE_SUCCESS;
                                        })))));
    }

    // necessary for multi-version support
    private static @NonNull LiteralArgumentBuilder<FabricClientCommandSource> literal(@NonNull String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    // necessary for multi-version support
    private static <T> @NonNull RequiredArgumentBuilder<FabricClientCommandSource, T> argument(@NonNull String name,
                                                                                               @NonNull ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static @NonNull CompletableFuture<Suggestions> suggestChannels(@NonNull CommandContext<FabricClientCommandSource> context,
                                                                           @NonNull SuggestionsBuilder builder) {
        TeamSpeakClient client = teamSpeakClient;
        if (!client.isConnected()) {
            return builder.buildFuture();
        }

        return new ChannelListQuery().send(client)
                .thenApply(response -> buildSuggestions(builder, response.data() == null
                        ? List.of()
                        : response.data(), Channel::getName));
    }

    private static @NonNull CompletableFuture<Suggestions> suggestClients(@NonNull CommandContext<FabricClientCommandSource> context,
                                                                          @NonNull SuggestionsBuilder builder) {
        TeamSpeakClient client = teamSpeakClient;
        if (!client.isConnected()) {
            return builder.buildFuture();
        }

        return new ClientListQuery().send(client)
                .thenApply(response -> buildSuggestions(builder, response.data() == null
                        ? List.of()
                        : response.data(), cl -> "\"" + cl.getNickname() + "\""));
    }

    private static <T> @NonNull Suggestions buildSuggestions(@NonNull SuggestionsBuilder builder,
                                                             @NonNull Iterable<T> items,
                                                             @NonNull Function<T, String> nameExtractor) {
        String remaining = builder.getRemainingLowerCase();

        for (T item : items) {
            if (nameExtractor.apply(item).toLowerCase(ROOT).startsWith(remaining)) {
                builder.suggest(nameExtractor.apply(item));
            }
        }

        for (T item : items) {
            String lowerName = nameExtractor.apply(item).toLowerCase(ROOT);
            if (!lowerName.startsWith(remaining) && lowerName.contains(remaining)) {
                builder.suggest(nameExtractor.apply(item));
            }
        }

        return builder.build();
    }

    private static @NonNull CompletableFuture<Channel> resolveChannel(@NonNull TeamSpeakClient client, @NonNull String channelName) {
        return new ChannelListQuery().send(client)
                .thenApply(response -> response.data() == null ? null : findBestMatch(response.data(), channelName, Channel::getName).orElse(null));
    }

    private static @NonNull CompletableFuture<Client> resolveClient(@NonNull TeamSpeakClient client, @NonNull String target) {
        return new ClientListQuery().send(client).thenCompose(response -> {
            List<Client> clients = response.data();
            if (clients == null || clients.isEmpty()) {
                return completedFuture(null);
            }

            Optional<Client> byNickname = findBestMatch(clients, target, Client::getNickname);
            return byNickname
                    .<CompletionStage<Client>>map(CompletableFuture::completedFuture)
                    .orElseGet(() -> resolveClientByDescription(client, clients.iterator(), target));
        });
    }

    private static @NonNull CompletableFuture<Client> resolveClientByDescription(@NonNull TeamSpeakClient client,
                                                                                 @NonNull Iterator<Client> remaining,
                                                                                 @NonNull String target) {
        if (!remaining.hasNext()) {
            return completedFuture(null);
        }

        Client candidate = remaining.next();
        return new ClientDescriptionQuery(candidate.getClientId()).send(client).thenCompose(response -> {
            String description = response.data();
            if (description != null && !description.isBlank() && description.toLowerCase(ROOT).contains(target.toLowerCase(ROOT))) {
                return completedFuture(candidate);
            }

            return resolveClientByDescription(client, remaining, target);
        });
    }

    private static <T> @NonNull Optional<T> findBestMatch(@NonNull Collection<T> items,
                                                          @NonNull String name,
                                                          @NonNull Function<T, String> nameExtractor) {
        Optional<T> exact = items.stream().filter(item -> nameExtractor.apply(item).equalsIgnoreCase(name)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        String needle = name.toLowerCase(ROOT);
        return items.stream().filter(item -> nameExtractor.apply(item).toLowerCase(ROOT).contains(needle)).findFirst();
    }

    private static void move(@NonNull TeamSpeakClient client,
                             int clientId,
                             int channelId,
                             @NonNull FabricClientCommandSource source,
                             @NonNull String feedbackKey,
                             Object @NonNull ... messageArgs) {
        new ClientMoveQuery(clientId, channelId).send(client).thenAccept(response -> {
            if (response.success()) {
                source.sendFeedback(translatable(feedbackKey + ".success", messageArgs));
            } else {
                String reason = response.msg();
                String baseKey = feedbackKey + ".failed";
                if (reason.isBlank()) {
                    source.sendError(translatable(baseKey, messageArgs));
                    return;
                }

                Object[] argsWithReason = Arrays.copyOf(messageArgs, messageArgs.length + 1);
                argsWithReason[messageArgs.length] = reason;
                source.sendError(translatable(baseKey + "_reason", argsWithReason));
            }
        });
    }
}
