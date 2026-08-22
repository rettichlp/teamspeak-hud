<div align="center">

# TeamSpeak HUD

**See who's with you, right in the game.**

[![Fabric](https://img.shields.io/badge/Loader-Fabric-DBB69D?logo=fabric)](https://fabricmc.net/)
[![Modrinth](https://img.shields.io/badge/Verf%C3%BCgbar%20auf-Modrinth-1bd96a?logo=modrinth)](https://modrinth.com/mod/teamspeak-hud)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/teamspeak-hud?logo=modrinth&label=Downloads&color=00AF5C)](https://modrinth.com/mod/teamspeak-hud)
[![Modrinth Game Versions](https://img.shields.io/modrinth/game-versions/teamspeak-hud?logo=modrinth&label=Minecraft&color=00AF5C)](https://modrinth.com/mod/teamspeak-hud/versions)
[![GitHub Release](https://img.shields.io/github/v/release/rettichlp/teamspeak-hud?logo=github&label=GitHub%20Release)]([https://github.com/rettichlp/teamspeak-hud](https://github.com/rettichlp/teamspeak-hud))

</div>

TeamSpeak Integration is a lightweight Fabric mod that connects to your local TeamSpeak 3 client and shows a small HUD overlay listing
everyone currently in your TeamSpeak channel, including who's talking, muted, or away.

> ⚠️ **Disclaimer:** This is an independent, unofficial project. I am not affiliated with, endorsed by, or sponsored by TeamSpeak
> Systems GmbH in any way. "TeamSpeak" is a trademark of TeamSpeak Systems GmbH; it is used here solely to describe the software this
> mod interoperates with.

## Inhaltsverzeichnis

- [Installation](#installation)
- [Requirements](#requirements)
- [Usage](#usage)

## Installation

TeamSpeak HUD can be installed in almost any popular launcher that supports Modrinth integration (e.g. the official Modrinth Launcher,
LabyMod Launcher, Prism Launcher, MultiMC, or ATLauncher). Search for *TeamSpeak HUD* and install it. All required dependencies
([Fabric API](https://modrinth.com/mod/fabric-api) and [ModMenu](https://modrinth.com/mod/modmenu)) will be installed automatically.

> 🆘 If you need help or encounter any issues, feel free to open a ticket on my [Discord](https://discord.gg/mZGAAwhPHu).

## Requirements

The mod talks to TeamSpeak via its **ClientQuery** plugin, which ships with TeamSpeak 3 but is disabled by default. Enable it in
TeamSpeak under **Tools → Options → Addons**, then enable the "ClientQuery Plugin".

The mod tries to find the ClientQuery API key automatically (by searching `clientquery.ini` in the usual TeamSpeak directories). If
that fails, or the key doesn't match, you can enter it manually in the mod's settings
(via [ModMenu](https://modrinth.com/mod/modmenu)): open TeamSpeak's ClientQuery settings to copy the key, then paste it into the
"Manual API key" field.

## Usage

Once connected, a HUD box appears in the bottom-right corner listing the members of your current TeamSpeak channel. Use the
ModMenu settings screen to configure it.

<div align="center">

📥 [**Jetzt auf Modrinth herunterladen**](https://modrinth.com/mod/teamspeak-hud)

</div>
