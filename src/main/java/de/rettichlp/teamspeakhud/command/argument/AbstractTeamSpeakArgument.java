package de.rettichlp.teamspeakhud.command.argument;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Locale.ROOT;

public abstract class AbstractTeamSpeakArgument {

    protected static <T> @NonNull Optional<T> findBestMatch(@NonNull Collection<T> items,
                                                          @NonNull String name,
                                                          @NonNull Function<T, String> nameExtractor) {
        Optional<T> exact = items.stream().filter(item -> nameExtractor.apply(item).equalsIgnoreCase(name)).findFirst();
        if (exact.isPresent()) {
            return exact;
        }

        String needle = name.toLowerCase(ROOT);
        return items.stream().filter(item -> nameExtractor.apply(item).toLowerCase(ROOT).contains(needle)).findFirst();
    }

    protected static <T> @NonNull Suggestions buildSuggestions(@NonNull SuggestionsBuilder builder,
                                                             @NonNull Iterable<T> items,
                                                             @NonNull Function<T, String> nameExtractor) {
        String remaining = builder.getRemainingLowerCase();

        // starting with input
        for (T item : items) {
            if (nameExtractor.apply(item).toLowerCase(ROOT).startsWith(remaining)) {
                builder.suggest(nameExtractor.apply(item));
            }
        }

        // containing input
        for (T item : items) {
            String lowerName = nameExtractor.apply(item).toLowerCase(ROOT);
            if (!lowerName.startsWith(remaining) && lowerName.contains(remaining)) {
                builder.suggest(nameExtractor.apply(item));
            }
        }

        return builder.build();
    }
}
