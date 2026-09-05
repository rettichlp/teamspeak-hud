package de.rettichlp.teamspeakhud.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientMoveQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;
import static de.rettichlp.teamspeakhud.command.argument.ChannelArgument.channel;
import static de.rettichlp.teamspeakhud.command.argument.ClientArgument.client;
import static java.util.Arrays.copyOf;
import static net.minecraft.network.chat.Component.translatable;

public class TsCommand {

    @SuppressWarnings("unchecked")
    public static void register(@NonNull CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ts")
                .then(literal("join")
                        .then(argument("channel", channel())
                                .executes(context -> {
                                    CompletionStage<Channel> futureChannel = (CompletableFuture<Channel>) context.getArgument("channel", CompletableFuture.class);

                                    FabricClientCommandSource source = context.getSource();

                                    if (!teamSpeakClient.isConnected()) {
                                        source.sendError(translatable("tsh.command.not_connected"));
                                        return SINGLE_SUCCESS;
                                    }

                                    futureChannel.thenAccept(channel -> {
                                        if (channel == null) {
                                            source.sendError(translatable("tsh.command.channel_not_found"));
                                            return;
                                        }

                                        move(teamSpeakClient.getOwnClientId(), channel, source, "tsh.command.join", channel.getName());
                                    });

                                    return SINGLE_SUCCESS;
                                })))
                .then(literal("move")
                        .then(argument("client", client())
                                .then(argument("channel", channel())
                                        .executes(context -> {
                                            CompletionStage<Client> futureClient = (CompletableFuture<Client>) context.getArgument("client", CompletableFuture.class);
                                            CompletionStage<Channel> futureChannel = (CompletableFuture<Channel>) context.getArgument("channel", CompletableFuture.class);

                                            FabricClientCommandSource source = context.getSource();

                                            if (!teamSpeakClient.isConnected()) {
                                                source.sendError(translatable("tsh.command.not_connected"));
                                                return SINGLE_SUCCESS;
                                            }

                                            futureChannel.thenAccept(channel -> {
                                                if (channel == null) {
                                                    source.sendError(translatable("tsh.command.channel_not_found"));
                                                    return;
                                                }

                                                futureClient.thenAccept(client -> {
                                                    if (client == null) {
                                                        source.sendError(translatable("tsh.command.client_not_found"));
                                                        return;
                                                    }

                                                    move(client.getClientId(), channel, source, "tsh.command.move", client.getNickname(), channel.getName());
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

    private static void move(int clientId,
                             @NonNull Channel channel,
                             @NonNull FabricClientCommandSource source,
                             @NonNull String feedbackKey,
                             Object @NonNull ... messageArgs) {
        new ClientMoveQuery(clientId, channel.getId()).send(teamSpeakClient).thenAccept(response -> {
            if (response.success()) {
                source.sendFeedback(translatable(feedbackKey + ".success", messageArgs));
            } else {
                String reason = response.msg();
                String baseKey = feedbackKey + ".failed";
                if (reason.isBlank()) {
                    source.sendError(translatable(baseKey, messageArgs));
                    return;
                }

                Object[] argsWithReason = copyOf(messageArgs, messageArgs.length + 1);
                argsWithReason[messageArgs.length] = reason;
                source.sendError(translatable(baseKey + "_reason", argsWithReason));
            }
        });
    }
}
