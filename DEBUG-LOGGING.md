# Connection diagnostics

INFO logs in `logs/latest.log` and the Prism console include startup, activity queued, IPC opened, HANDSHAKE written, READY, SET_ACTIVITY written, and Discord's acknowledgement. Expected final checkpoint: `Discord accepted the Rich Presence activity (SET_ACTIVITY)`.

The Windows transport retains the working JNA PeekNamedPipe/ReadFile/WriteFile implementation. It does not hold the pipe lock during an idle read wait. No player names or map names are deliberately included in diagnostic messages.

If only generic world presence appears, look for `RoN client API changed; using generic presence`. If startup fails, include the Mixin error in your report. Check `config/ron-discord-presence-client.toml` for the application ID and enabled setting. Only install one presence JAR.
