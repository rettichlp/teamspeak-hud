package de.rettichlp.teamspeakhud.teamspeak;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.rettichlp.teamspeakhud.TeamSpeakHud.LOGGER;
import static java.lang.ProcessHandle.allProcesses;
import static java.lang.System.getProperty;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.Files.isRegularFile;
import static java.nio.file.Files.readAllLines;
import static java.nio.file.Files.walkFileTree;
import static java.nio.file.Paths.get;
import static java.util.Locale.ROOT;

/**
 * Locates the TeamSpeak ClientQuery API key by searching {@code clientquery.ini} in the well-known TeamSpeak 3 client directories for
 * the current OS, plus the installation directory of any currently running TeamSpeak process.
 */
public class ApiKeyResolver {

    private static final String CLIENT_QUERY_FILE = "clientquery.ini";
    private static final String API_KEY_PREFIX = "api_key=";
    private static final int MAX_SEARCH_DEPTH = 3;

    public Optional<String> resolve() {
        Collection<Path> roots = new LinkedHashSet<>(knownDirectories());
        addRunningClientDirectories(roots);

        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }

            for (Path clientQueryFile : findClientQueryFiles(root)) {
                String apiKey = readApiKey(clientQueryFile);
                if (apiKey != null && !apiKey.isEmpty()) {
                    LOGGER.info("Found TeamSpeak ClientQuery API key in {}", clientQueryFile);
                    return Optional.of(apiKey);
                }
            }
        }

        return Optional.empty();
    }

    private @NonNull List<Path> findClientQueryFiles(@NonNull Path root) {
        List<Path> found = new ArrayList<>();

        addIfPresent(found, root.resolve(CLIENT_QUERY_FILE));
        addIfPresent(found, root.resolve("config").resolve(CLIENT_QUERY_FILE));
        if (!found.isEmpty()) {
            return found;
        }

        try {
            walkFileTree(root, Set.of(), MAX_SEARCH_DEPTH, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attributes) {
                    if (file.getFileName().toString().equalsIgnoreCase(CLIENT_QUERY_FILE)) {
                        found.add(file);
                    }

                    return CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException e) {
                    return CONTINUE;
                }
            });
        } catch (IOException e) {
            // directory inaccessible; nothing to resolve here
        }

        return found;
    }

    private void addIfPresent(Collection<Path> found, Path file) {
        if (isRegularFile(file)) {
            found.add(file);
        }
    }

    private @Nullable String readApiKey(Path clientQueryFile) {
        try {
            for (String rawLine : readAllLines(clientQueryFile, UTF_8)) {
                String line = rawLine.strip();
                if (line.startsWith(API_KEY_PREFIX)) {
                    return line.substring(API_KEY_PREFIX.length()).strip();
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read {}", clientQueryFile, e);
        }

        return null;
    }

    private void addRunningClientDirectories(Collection<Path> roots) {
        try {
            allProcesses().forEach(process -> process.info().command().ifPresent(executable -> {
                String lowerCase = executable.toLowerCase(ROOT);
                if (!lowerCase.contains("ts3client") && !lowerCase.contains("teamspeak")) {
                    return;
                }

                Path installDirectory = get(executable).getParent();
                if (installDirectory != null) {
                    roots.add(installDirectory);
                }
            }));
        } catch (Exception e) {
            // process enumeration may be restricted; fall back to the known directories
        }
    }

    private @NonNull List<Path> knownDirectories() {
        String osName = getProperty("os.name", "").toLowerCase(ROOT);
        Path userHome = get(getProperty("user.home"));
        List<Path> roots = new ArrayList<>();

        if (osName.contains("win")) {
            addRootFromEnvironment(roots, "APPDATA");
            addRootFromEnvironment(roots, "LOCALAPPDATA");
            roots.add(userHome.resolve("TS3Client"));
        } else if (osName.contains("mac")) {
            Path applicationSupport = userHome.resolve("Library").resolve("Application Support");
            roots.add(applicationSupport.resolve("TeamSpeak 3"));
            roots.add(applicationSupport.resolve("TeamSpeak"));
        } else {
            roots.add(userHome.resolve(".ts3client"));
            roots.add(userHome.resolve(".local").resolve("share").resolve("TeamSpeak 3"));
            roots.add(userHome.resolve(".config").resolve("TeamSpeak"));
        }

        return roots;
    }

    private void addRootFromEnvironment(Collection<Path> roots, String environmentVariable) {
        String value = System.getenv(environmentVariable);
        if (value != null && !value.isEmpty()) {
            roots.add(get(value).resolve("TS3Client"));
        }
    }
}
