# RoN Discord Presence 1.5.0 — compatible with Reign of Nether 1.5.0

- Targets the stable RoN 1.5.0 release, Minecraft 1.20.1 and Forge 47.4.x.
- Reads faction names through the new registry-based Faction.getName() API instead of casting factions to enums.
- Uses the renamed serverNormDayTime field for the optional dawn/night countdown.
- Preserves presence formatting, existing config, application ID, clock observation, and Windows IPC fix.
- Updates exact dependency metadata and compatibility checks against the official 1.5.0 JAR.

Upstream source: 63fb7688efa33021ae357d701225d8116d756cf9 (build version 1.5.0; upstream branch is still named 1.20.1-1.5.0-beta).
Official stable release: https://modrinth.com/mod/reign-of-nether-rts/version/rkigxbhw

Replace the old presence JAR. This build requires exactly RoN 1.5.0, not 1.4.4g or beta releases.
