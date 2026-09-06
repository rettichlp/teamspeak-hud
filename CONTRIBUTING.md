# Contributing to TeamSpeak HUD

_🤖 This file was generated completely by AI._

Thanks for considering a contribution! This document covers everything you need to get set up and send a pull
request that's easy to review.

By participating in this project you agree to abide by the [Code of Conduct](.github/CODE_OF_CONDUCT.md).

## Getting set up

- JDK 25 (Temurin recommended). Gradle's toolchain resolver picks the right JDK per Minecraft version automatically (Java 17/21/25
  depending on the version), so having 25 JDK available covers all of them.
- Build every supported Minecraft version: `./gradlew build`
- Build/run just one version, e.g. `./gradlew :26.2:build` or `./gradlew :26.2:runClient`

This project targets multiple Minecraft versions at once via [Stonecutter](https://stonecutter.kikugie.dev/). See 
[Multi-version support](#multi-version-support) below before editing anything.

## Project structure

Package root: `de.rettichlp.teamspeakhud`

- `command/` — the `/ts` client command (join/move) built with Brigadier; argument types (channel/client name
  resolution and tab completion) live in `command/argument/`.
- `configuration/` — persisted mod configuration.
- `gui/` — the HUD overlay (`TSHud`) and the ModMenu options screen.
- `integrations/` — integration with other mods (currently just ModMenu).
- `teamspeak/` — the actual TeamSpeak ClientQuery client:
  - `TeamSpeakConnection`, `Reconnector`, `Heartbeat`, `ApiKeyResolver` — connection lifecycle.
  - `RequestQueue` — serializes ClientQuery requests/responses.
  - `command/` — one class per ClientQuery command (e.g. `ClientListQuery`, `ClientMoveQuery`), all implementing `TeamSpeakCommand<T>`.
  - `notify/` — handlers for ClientQuery's asynchronous notify events (poke, text message, membership changes, ...).
  - `model/` — plain data classes (`Channel`, `Client`).

This project has no mixins — HUD rendering and command registration hook in via Fabric API callbacks, not ASM injection.

Translation strings live in `src/main/resources/assets/teamspeak-hud/lang/en_us.json`.
Minecraft/Loader/Fabric API/dependency versions live in `stonecutter.properties.toml` — don't hardcode them elsewhere.

## Multi-version support

`src/main/java`/`src/main/resources` hold a single source of truth, written against the *active* version declared in
`stonecutter.gradle.kts` (currently `26.2`). Stonecutter generates the per-version copies under
`versions/<version>/build/generated/stonecutter/` for every version listed in `settings.gradle.kts` — **never edit those generated
copies**, edit the root sources instead.

If a change needs to differ between Minecraft versions (a renamed class, a moved method, ...), don't duplicate files — add a
`replacements` rule in `stonecutter.gradle.kts` instead, following the existing examples there (e.g. the `ResourceLocation` →
`Identifier` rename for 1.21.11+).

To develop against a different Minecraft version, change the `stonecutter active "..."` line in `stonecutter.gradle.kts` (or use the
Stonecutter IntelliJ plugin's version switcher) and re-sync Gradle.

## Branching & pull requests

Branches must be named `feature/*`, `bugfix/*`, or `hotfix/*`:

- `feature/*` — anything that isn't a fix.
- `bugfix/*` — fixes for bugs that are not yet on `main`.
- `hotfix/*` — emergency-only: fixing a bug that is already live on `main`.

Merge targets (all merges go through a GitHub pull request):

- `feature/*` → `develop`.
- `bugfix/*` → `develop` or a `hotfix/*` branch.
- Only `develop` or `hotfix/*` may be merged into `main`.

Your pull request title must match the name of the branch you're merging, except for merges into `main`, whose
title must be `Release x.y.z`.

Every push/PR against `develop` or `hotfix/**` runs the Build workflow (compiles and packages every Minecraft version). Releases are
cut from `main` via the manually triggered Release workflow — you don't need to do anything for that as a contributor.

## Code style

The project has a fixed IntelliJ code style and inspection profile; please stick to it rather than your editor's
defaults.

- [IntelliJ code style](https://gist.github.com/rettichlp/19e2a02631ba1a65cff3e0d53324af9d#file-intellij_code_style-xml)
- [IntelliJ inspection profile](https://gist.github.com/rettichlp/19e2a02631ba1a65cff3e0d53324af9d#file-intellij_inspections-xml)

If you use IntelliJ, importing the project's code style scheme (`intellij_code_style`) and inspection profile (`intellij_inspections`)
will apply all of this automatically.

A few conventions are already used throughout the codebase:

- Lombok is available (`compileOnly`/`annotationProcessor`) — use it for boilerplate (`@Data`, `@Getter`, `@RequiredArgsConstructor`,
  etc.) where it makes sense.
- Nullability is annotated with [JSpecify](https://jspecify.dev/)'s `@NonNull`/`@Nullable` — annotate new public APIs the same way.
- Prefer records for small immutable data types (see `Response<T>` and the `teamspeak/command/*Query` classes).

## Testing your changes

There's no automated test suite; `./gradlew build` verifies every supported Minecraft version still compiles and packages correctly.
Since this mod's entire purpose is talking to a live TeamSpeak 3 client over ClientQuery, please also test manually: run
`./gradlew :<version>:runClient` (or the matching Fabric Loom run configuration in IntelliJ, picked up after a Gradle sync) against a
TeamSpeak client with the ClientQuery plugin enabled, and check the change in-game before opening a PR.

## Reporting bugs / requesting features

Open a GitHub issue with as much detail as you can: Minecraft/mod version, steps to reproduce, and what you expected vs. what happened.
For feature requests, describe the use case, not just the desired implementation.
