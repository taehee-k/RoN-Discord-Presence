# RoN Discord Presence 1.5.0b — compatible with Reign of Nether 1.5.0b

- Updates the exact dependency requirement to stable RoN 1.5.0b.
- Preserves faction lookup, match timer, display formatting, existing settings and Windows IPC implementation.
- Verifies the used public API members and both clock Mixin targets against the official 1.5.0b JAR.
- Requires Minecraft 1.20.1 and Forge 47.4.0+ within 47.x.

Source compared: 1110f4e37530763274ba762a3a1d5daa60bcb84b (1.5.0a) to 67db0a71d03534b03276a168c90efab2f69fa8e0 (1.5.0b).
The player-construction changes do not affect this add-on's read-only API access or clock hooks.
Official release: https://modrinth.com/mod/reign-of-nether-rts/version/BpeAy6l2

Replace the previous presence JAR. This build requires exactly RoN 1.5.0b. Existing config/ron-discord-presence-client.toml settings are reused.
