# minecraft-metaconcord

Server-side NeoForge mod (Minecraft 1.21.1) that relays chat and player events to the [metaconcord](https://github.com/metastruct/metaconcord) bridge over WebSocket. Counterpart of [gmod-metaconcord](https://github.com/Earu/gmod-metaconcord).

## What it does

- Player chat, `/me` emotes, joins, leaves, deaths and earned advancements are sent to the bridge (`/minecraft/ws` endpoint), which posts them to Discord.
- Deaths and advancements follow the vanilla rules: they are only relayed when `showDeathMessages` / `announceAdvancements` is on and the advancement would be announced in chat.
- Messages from the dedicated Discord channel are broadcast in-game as `[Discord] Name: message`.

## Install

1. Build with `./gradlew build`, the jar is in `build/libs/`.
2. Drop the jar in the server's `mods/` folder. Clients do not need it.
3. Start the server once, then edit `config/metaconcord-common.toml`:
   - `endpoint`: full WebSocket URI, e.g. `wss://your-host/minecraft/ws`
   - `token`: the shared token from the bridge's `config/minecraft.json` (leave empty if the bridge runs with an empty token, the IP allowlist still applies)
4. Restart the server.

## Wire protocol

Same as gmod-metaconcord: outbound frames `{"name": "...", "data": {...}}`, inbound `{"payload": {"name": "...", "data": {...}}}`, empty-string heartbeat every 10s, exponential reconnect backoff capped at 5 minutes. Auth is the `X-Auth-Token` header plus an IP allowlist on the bridge side.

## Dev

`./gradlew runServer` starts a dedicated dev server (accept the EULA in `runs/server/eula.txt`). Point `endpoint` at a local bridge with `ws://localhost:3000/minecraft/ws`.
