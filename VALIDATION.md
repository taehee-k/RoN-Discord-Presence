# Validation: 1.5.0

Built with JDK 17, Gradle 8.8, ForgeGradle 6.0.54, Minecraft 1.20.1 / Forge 47.4.0.
Compilation, resource processing, production remapping and build succeeded.

All **15 tests passed, zero skipped**:
- 7 formatting/model tests: phases, 1v1/team/spectator output, privacy toggles, non-transitive alliances, timer/reset gating, hour formatting, UTF-8 bounds and shortened player lists.
- 5 Discord protocol tests: handshake, READY/ack flow, framing/reconnection/error handling.
- 1 real Windows named-pipe regression: reader waiting while writing, plus idle-reader shutdown.
- 1 compatibility check against the official Reign of Nether 1.5.0 JAR: public field/method descriptors, both Mixin targets, and the new faction registry API.
- 1 faction-name test: built-in names, custom names, missing names, and None.

The shipped archive manifest is version 1.5.0 and registers the client-only clock Mixin. Dependency metadata requires exactly RoN 1.5.0.

Not verified in a running Minecraft client: runtime Mixin injection, every game-mode transition, Discord image fetching/hover rendering, and full live activity appearance. Descriptor checks reduce compatibility risk but do not replace a live Forge launch. The previous Windows connection implementation was user-confirmed working and is preserved here. Linux/macOS transports remain untested.

Suggested live checks: main menu; lobby ready/countdown; 1v1; team hover; spectating; compare the timer to the HUD; disconnect and join another world. Check each visibility setting after restart. A late join may briefly omit the timer while awaiting server synchronization.
