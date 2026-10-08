# RoN Discord Presence 1.0.0

Standalone client-side Discord presence for **Reign of Nether 1.4.4f**, Minecraft **1.20.1**, Java **17**, Forge **47.4.0+ within 47.x**. This release does not support the beta.

## Install

1. Close Minecraft and remove your previous RoN presence add-on JAR from the instance's `mods` folder.
2. Put `ron-discord-presence-1.0.0-mc1.20.1-ron1.4.4f.jar` there alongside Reign of Nether 1.4.4f.
3. Open Discord desktop, enable activity sharing, and launch Minecraft.

Install only one presence add-on version. The mod ID is unchanged: `ron_discord_presence`.
The version is deliberately reset to 1.0.0 for the named release; it supersedes the earlier 1.0.3 prototype.

Application ID `1557642898630123560` is built in. No bot, token, GitHub repository, or additional Discord application setup is needed for this installation.
The Discord activity's application title comes from that application, independently of the mod's display name.

## Settings

This release uses **`config/ron-discord-presence-client.toml`** (hyphens), leaving the prototype's underscore-named config untouched. The new file starts with the approved display defaults. If you customized the application ID or images, copy those settings into the new file. Restart Minecraft after editing.

| Setting | Default | Effect |
| --- | --- | --- |
| enabled | true | Enable presence |
| applicationId | 1557642898630123560 | Public Discord application ID |
| showMenus | true | Show main menu and connecting state |
| showMap | true | Show RTS map names; singleplayer world name outside RTS |
| showFaction | true | Show faction |
| showGameType | true | Show Classic matchup / mode |
| showPlayerNames | true | 1v1 names on card; other participants on hover |
| showTimer | true | Show synchronized RoN match duration |
| showDaylight | false | Add time until dawn/night |
| largeImage | Public RoN icon URL | Image with participant hover text; blank disables both |

Map and player names are shared by default. Their settings hide them from both visible text and hover text. No server address, world path, inventory, resources, account token, party secret, or join button is sent.

The default image is the public Reign of Nether project icon hosted on Modrinth. You may replace its URL with an uploaded Discord application asset key. Loading external images depends on Discord's image proxy; if it fails, text still works, but participant hover needs a working image.

## Display examples

Each row represents the two text lines beneath Discord's application title.

| Situation | First line | Second line |
| --- | --- | --- |
| Main menu | In the main menu | Reign of Nether |
| Connecting | Connecting to a world | Reign of Nether |
| Normal singleplayer world | Exploring Emerald Valley | Singleplayer |
| Normal multiplayer world, no world name | Exploring a world | Multiplayer |
| RTS lobby | Emerald Valley · Preparing for battle | Choosing a faction |
| Faction selected | Emerald Valley · Preparing for battle | Piglins |
| Ready | Emerald Valley · Ready | Piglins |
| Countdown | Emerald Valley · Starting soon | Piglins |
| 1v1 | Emerald Valley · 1v1 vs Alex | Piglins · 12:34 |
| 2v2 | Emerald Valley · 2v2 | Piglins · 12:34 |
| Uneven teams | Emerald Valley · 1v2 | Piglins · 12:34 |
| FFA | Emerald Valley · FFA | Piglins · 12:34 |
| Solo Classic | Emerald Valley · Solo Classic | Piglins · 12:34 |
| Uncertain teams | Emerald Valley · Classic | Piglins · 12:34 |
| Survival | Emerald Valley · Survival | Piglins · 12:34 |
| Co-op Survival | Emerald Valley · Co-op Survival | Piglins · 12:34 |
| Scenario | Emerald Valley · Scenario | Piglins · 12:34 |
| Sandbox | Emerald Valley · Sandbox | Editing map |
| Tutorial | Playing the tutorial | Build Town Centre |
| Spectating 1v1 | Emerald Valley · Spectating 1v1 | Alex vs Steve · 12:34 |
| Spectating teams | Emerald Valley · Spectating 2v2 | 12:34 |
| Spectating FFA | Emerald Valley · Spectating FFA | 12:34 |
| Unknown map | 1v1 vs Alex | Piglins · 12:34 |
| Daylight enabled | Emerald Valley · 1v1 vs Alex | Piglins · 12:34 · Night in 2:10 |

Team-game hover: `With: Steve` / `Against: Alex, Sam`. Spectator hover lists teams. FFA hover lists opponents. Long lists shorten with `+N more`. Discord controls tooltip wrapping. Disabling player names removes them everywhere; disabling game type hides matchup labels but not independently enabled names.

The timer has no “Match” prefix and no day count. It uses RoN's server-synchronized `rtsGameTicks`, the same counter as the HUD, formatted as `m:ss` or `h:mm:ss`. It is omitted until the first server sync (normally within ten seconds after joining an ongoing match), then refreshed through Discord about every five seconds. There is no Discord elapsed timestamp. A small client-only Mixin observes timer synchronization and RTS reset without changing gameplay.

## Limits

Teams are inferred from currently known RTS participants and reciprocal alliances, not an authoritative original match-size field. Participants observed during this connection are retained when defeated; joining after an elimination cannot recover missing original players. Dynamic alliances can change the inferred format. Non-transitive alliances fall back to Classic. Bots are included when RoN registers them as RTS players. Scenario NPCs may appear in participant hover.

Defeated local players become spectators while an observed match remains active. An explicit RTS reset clears match state; no victory/defeat announcement is invented. A surviving RTS player can remain in playing state until the game resets, matching the upstream state. Disconnect clears the connection and roster.

Dawn/night is optional and follows RoN's world-time thresholds. It is omitted when a blood moon is active or daylight cycling is disabled. Server time changes can move that countdown.

The tested Windows connection fix and diagnostic logging are retained. Linux/macOS transports are implemented but have not been exercised here. This build was not visually tested in a live Minecraft/Discord session; see VALIDATION.md.

## Build and source

With JDK 17:

```powershell
.\gradlew.bat test build -PronReleaseJar=C:/path/to/reignofnether-1.4.4f.jar
```

On Linux/macOS use `sh gradlew`. Output is `build/libs/ron-discord-presence-1.0.0-mc1.20.1-ron1.4.4f.jar`. The optional release-JAR property enables actual-release compatibility checks; without it those two tests skip. First builds require internet access for Gradle and Forge dependencies. Reign of Nether itself is not redistributed.

Source is provided under GPL-3.0-only; see LICENSE.txt. This is an unofficial add-on. Reign of Nether and its project icon belong to their respective contributors. A GitHub repository is optional for operation and can be added later for public releases, issue tracking, and contributions.
