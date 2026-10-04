# Cloud feasibility check (2026-10-04)

Can the build and the GameTest suite run in a Claude Code cloud session? **Not with this environment's network
policy.** The machine is big enough; the downloads are blocked. No code was changed for this check.

## The machine

| | |
|---|---|
| CPU | 4 cores (Intel Xeon @ 2.10 GHz) |
| Memory | 15 GiB, about 15 GiB free, no swap |
| Free disk | about 30 GB (session allowance, not the 252 GB `df` shows for the whole disk) |
| Java | OpenJDK 21.0.11 (Ubuntu), `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` - the right version |

Outbound HTTPS goes through a proxy that enforces an allowlist. Java is already set up to trust it.

## What happened

| Command | Ran? | Time | Result |
|---|---|---|---|
| `./gradlew :neoforge:build` | Started, failed while reading the build files | 37 s (most of it downloading Gradle 8.10 itself, which worked) | `Plugin 'fabric-loom' version '1.8-SNAPSHOT' was not found` |
| `./gradlew :neoforge:runGameTestServer` | Same failure, same place | 6 s (Gradle already downloaded) | Same error; no test ran |

Both stop at the very first step: Gradle reads `build.gradle`, which declares the Fabric Loom plugin, and that plugin
only comes from `maven.fabricmc.net`, which is blocked. Even with that fixed, the next steps (NeoForge's ModDevGradle
setup, Parchment mappings, the Minecraft jar and libraries, the mod dependencies) all come from blocked hosts too, so
nothing compiles and no GameTest runs.

## Hosts tested (HTTPS, through the proxy)

Reachable: `services.gradle.org`, `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `repo.maven.apache.org`
(Maven Central), `raw.githubusercontent.com`, `github.com`, `objects.githubusercontent.com`.
(`repo1.maven.org` answered 429 "too many requests"; Gradle uses `repo.maven.apache.org`, which worked.)

Blocked (proxy answered 403 to CONNECT): everything below.

## Domains the allowlist would need

Required for `:neoforge:build` and `:neoforge:runGameTestServer` (repositories in `settings.gradle`,
`buildSrc/.../multiloader-common.gradle`, `neoforge/build.gradle`, plus what NeoForge downloads itself):

| Domain | Why |
|---|---|
| `maven.fabricmc.net` | Fabric Loom plugin (declared in the root `build.gradle`, so needed even for NeoForge-only builds) |
| `maven.neoforged.net` | NeoForge, ModDevGradle plugin, NeoForm |
| `maven.parchmentmc.org` | Parchment mappings |
| `repo.spongepowered.org` | Mixin |
| `maven.minecraftforge.net` | Forge-group tooling |
| `piston-meta.mojang.com`, `launchermeta.mojang.com` | Minecraft version manifest |
| `piston-data.mojang.com` | Minecraft client/server jars |
| `libraries.minecraft.net` | Minecraft's libraries |
| `resources.download.minecraft.net` | Game assets (needed by `runClient`/`runData`; possibly not by the test server, but NeoForge setup may fetch them) |
| `maven.blamejared.com`, `modmaven.dev` | JEI |
| `www.cursemaven.com`, `edge.forgecdn.net`, `mediafilez.forgecdn.net` | Jade, JER (CurseMaven redirects to the CurseForge CDN) |
| `dl.cloudsmith.io` | SmartBrainLib |
| `maven.theillusivec4.top` | Curios |
| `maven.wispforest.io` | Accessories / owo |
| `maven.su5ed.dev` | Forgified Fabric API |
| `maven.shedaniel.me` | Cloth / Architectury-related deps |
| `maven.createmod.net` | Create, Ponder, Flywheel |
| `mvn.devos.one` | Registrate |

Already allowed and needed: `services.gradle.org`, `plugins.gradle.org`, `plugins-artifacts.gradle.org`,
`repo.maven.apache.org`, `raw.githubusercontent.com` (ForgeConfigAPIPort).

Not certain until a run gets past the first error: there may be more hosts behind redirects (e.g. Modrinth's
`api.modrinth.com` / `cdn.modrinth.com`, also blocked now). Simplest route: a custom environment with **full** network
access, or the list above plus re-running and reading `curl -sS "$HTTPS_PROXY/__agentproxy/status"`
(`recentRelayFailures` names every host the proxy refused).

## Parallel GameTests and time

**Not measured: no test ran.** What can be said in advance:
- GameTests don't use extra CPU cores. Vanilla runs the tests of a batch side by side in the same world, all on the
  one server thread; batches run one after the other. 4 cores help Gradle and Java's garbage collector, not the tests.
- So the suite's time is mostly game ticks (20 per second), much like on a laptop. The cloud CPU (2.1 GHz Xeon) is
  likely a bit slower per core than a recent laptop, which only matters where ticks run slower than 20/s.
- The "about 10 seconds" figure is out of date: `CLAUDE.md` now says the 163-test suite takes a few minutes (the
  scaffold tests build whole towers).
- Every fresh cloud session also has to download Minecraft, NeoForge and all dependencies (likely several minutes,
  a few hundred MB to over 1 GB) before the first test, unless a setup script pre-fetches them.

## To make it work

1. Create a custom cloud environment with full network access, or add the domains above to its allowlist.
2. Optional: a setup script that runs `./gradlew :neoforge:build` once so the Gradle cache is warm.
3. Re-run this check: `./gradlew :neoforge:build`, then `tools/suite.sh`, and fill in the real times here.
