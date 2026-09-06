package de.rettichlp.teamspeakhud.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import de.rettichlp.teamspeakhud.teamspeak.command.ClientMoveQuery;
import de.rettichlp.teamspeakhud.teamspeak.model.Channel;
import de.rettichlp.teamspeakhud.teamspeak.model.Client;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static de.rettichlp.teamspeakhud.TeamSpeakHud.teamSpeakClient;
import static de.rettichlp.teamspeakhud.command.argument.ChannelArgument.channel;
import static de.rettichlp.teamspeakhud.command.argument.ClientArgument.client;
import static java.lang.String.valueOf;
import static java.util.Arrays.copyOf;
import static net.minecraft.ChatFormatting.AQUA;
import static net.minecraft.ChatFormatting.DARK_AQUA;
import static net.minecraft.ChatFormatting.DARK_RED;
import static net.minecraft.ChatFormatting.RED;
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
                                        source.sendError(errorMessage("tsh.command.not_connected"));
                                        return SINGLE_SUCCESS;
                                    }

                                    futureChannel.thenAccept(channel -> {
                                        if (channel == null) {
                                            source.sendError(errorMessage("tsh.command.channel_not_found"));
                                            return;
                                        }

                                        move(teamSpeakClient.getOwnClientId(), channel.getId(), source, "tsh.command.join", channel.getName());
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
                                                source.sendError(errorMessage("tsh.command.not_connected"));
                                                return SINGLE_SUCCESS;
                                            }

                                            futureChannel.thenAccept(channel -> {
                                                if (channel == null) {
                                                    source.sendError(errorMessage("tsh.command.channel_not_found"));
                                                    return;
                                                }

                                                futureClient.thenAccept(client -> {
                                                    if (client == null) {
                                                        source.sendError(errorMessage("tsh.command.client_not_found"));
                                                        return;
                                                    }

                                                    move(client.getClientId(), channel.getId(), source, "tsh.command.move", client.getNickname(), channel.getName());
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
                             int channelId,
                             @NonNull FabricClientCommandSource source,
                             @NonNull String feedbackKey,
                             Object @NonNull ... messageArgs) {
        new ClientMoveQuery(clientId, channelId).send(teamSpeakClient).thenAccept(response -> {
            if (response.success()) {
                source.sendFeedback(feedbackMessage(feedbackKey + ".success", messageArgs));
            } else {
                String reason = response.msg();
                String baseKey = feedbackKey + ".failed";
                if (reason.isBlank()) {
                    source.sendError(errorMessage(baseKey, messageArgs));
                    return;
                }

                Object[] argsWithReason = copyOf(messageArgs, messageArgs.length + 1);
                argsWithReason[messageArgs.length] = reason;
                source.sendError(errorMessage(baseKey + "_reason", argsWithReason));
            }
        });
    }

    private static @NonNull MutableComponent errorMessage(@NonNull String key, Object @NonNull ... args) {
        return translatable(key, colorArgs(DARK_RED, args)).withStyle(RED);
    }

    private static @NonNull MutableComponent feedbackMessage(@NonNull String key, Object @NonNull ... args) {
        return translatable(key, colorArgs(AQUA, args)).withStyle(DARK_AQUA);
    }

    private static Object @NonNull [] colorArgs(@NonNull ChatFormatting color, Object @NonNull ... args) {
        Object[] colored = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            colored[i] = Component.literal(valueOf(args[i])).withStyle(color);
        }

        return colored;
    }
}
