# minecraft-metaconcord

[![Build](https://github.com/Metastruct/minecraft-metaconcord/actions/workflows/build.yml/badge.svg)](https://github.com/Metastruct/minecraft-metaconcord/actions/workflows/build.yml)

Server-side mod (Minecraft 1.21.1, NeoForge and Fabric) that relays chat and player events to the [metaconcord](https://github.com/metastruct/metaconcord) bridge over WebSocket. Counterpart of [gmod-metaconcord](https://github.com/Earu/gmod-metaconcord).

## What it does

- Player chat, `/me` emotes, joins, leaves, deaths and earned advancements are sent to the bridge (`/minecraft/ws` endpoint), which posts them to Discord.
- Deaths and advancements follow the vanilla rules: they are only relayed when `showDeathMessages` / `announceAdvancements` is on and the advancement would be announced in chat.
- Messages from the dedicated Discord channel are broadcast in-game as `[Discord] Name: message`.

## Install

1. Build with `./gradlew build`. Jars: `neoforge/build/libs/metaconcord-neoforge-<version>.jar` and `fabric/build/libs/metaconcord-fabric-<version>.jar`. Prebuilt jars for every commit are attached to the [build workflow runs](https://github.com/Metastruct/minecraft-metaconcord/actions/workflows/build.yml).
2. Drop the jar for your loader in the server's `mods/` folder. The Fabric build requires [Fabric API](https://modrinth.com/mod/fabric-api).
3. Start the server, then edit `config/metaconcord-common.toml`:
   - `endpoint`: full WebSocket URI, e.g. `wss://your-host/minecraft/ws`
   - `token`: the shared token from the bridge's `config/minecraft.json`
4. Restart the server.

On NeoForge `/me` is captured from the parsed command before it runs. On Fabric it is captured when the emote is broadcast. Same result on vanilla.

## Wire protocol

Same as gmod-metaconcord: outbound frames `{"name": "...", "data": {...}}`, inbound `{"payload": {"name": "...", "data": {...}}}`, empty-string heartbeat every 10s, exponential reconnect backoff capped at 5 minutes. Auth is the `X-Auth-Token` header plus an IP allowlist on the bridge side.

## Dev

`./gradlew :neoforge:runServer` or `./gradlew :fabric:runServer` starts a dedicated dev server (accept the EULA in `<loader>/run/eula.txt`). Point `endpoint` at a local bridge with `ws://localhost:3000/minecraft/ws`.

Layout: `common/` holds the loader-independent code (socket, payloads, stats, console relay, config) and is compiled into each loader jar; `neoforge/` and `fabric/` hold the entrypoints and event wiring.
