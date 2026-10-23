# Color Clash — notes for working on this repo

Offline multiplayer shed-your-hand card game for Android (2–6 phones on a LAN or hotspot). Private
project, never published. See README.md for the player-facing overview.

## Modules and the rules they live by

- `:rules` — pure Kotlin. No Android, Ktor or coroutines imports, ever. `GameEngine.reduce(state, action)`
  is the single source of truth; `validate` decides legality and `legalActions` is just `validate` over
  candidates, so the UI and host can't disagree. `Transition` is a mutable scratch copy used inside one
  reduce call only. Randomness only through `Rng` (state stored in `GameState.rngState`).
- `:protocol` — serializable messages and per-player views. `GameState.viewFor` and `GameEvent.toView`
  are where hidden information is stripped: other players' cards and their card ids must never reach a
  client (ids map to fixed cards, so an id is as good as the card). `ClientMessage.Hello` must stay
  decodable across versions; bump `PROTOCOL_VERSION` on any message change.
- `:net` — `GameHost` is a single-coroutine actor: all room state is touched only from its command loop.
  Clients send intents with no seat; the seat comes from the connection. `HostServer`/`GameClient` are
  thin Ktor transports.
- `:app` — Compose UI. `SessionManager` (process-wide) owns the current room; `HostService` only holds
  the foreground slot and Wi-Fi/wake locks. `TableFx` plays host updates one at a time and the game
  screen renders `fx.displayed`, not the latest view. Logic worth testing goes in pure functions
  (`TableLogic.kt`, `Feedback.kt`, `AddressRanking.kt`) with JVM unit tests.

Every color, the title (`GAME_TITLE`) and fonts come from `GameTheme`; don't hardcode colors elsewhere.
Portrait only. Cards are drawn in code, not bitmaps.

## Commands

```bash
./gradlew test                           # rules + protocol + net + app unit tests
./gradlew :rules:test -PfuzzGames=20000  # heavier fuzzing (default 2000 games)
./gradlew :net:test --rerun              # re-run the socket integration tests
./gradlew :app:assembleDebug :app:lintDebug
python tools/sfx/generate_sounds.py      # regenerate res/raw sounds (stdlib only)
```

## Gotchas

- JUnit silently skips test methods that don't return `Unit`. Write `fun x(): Unit = runBlocking { ... }`.
- On Windows `--rerun-tasks` across modules can fail on locked build files; prefer `--rerun` on one task.
- AGP 9.4 needs Gradle 9.6+. Compile/target SDK is 37: Android 17 requires the runtime
  `ACCESS_LOCAL_NETWORK` permission before any LAN traffic (`Permissions.network()`).
- JVM modules compile with `-Xjdk-release=17` so JDK 21-only APIs (e.g. `List.removeLast`) can't sneak in
  and crash older Androids.
- Guests bind the process to Wi-Fi (`WifiBinder`); the host doesn't, because its own client uses loopback.
- New dependencies need a reason: the brief limits the stack to Kotlin, Compose/Material 3, Ktor,
  kotlinx.serialization and coroutines. ZXing core + CameraX were added for QR codes.

## Working agreements

- Work in phases; finish each with a green build and tests, a short commit, and a summary.
- Commit messages under 10 words, plain. No AI co-author lines. Don't push; the owner pushes.
- Short, human comments. Don't build features outside the brief; list ideas at the end of a phase instead.

## Network resilience

- `ChaosTest` (`:net`) puts a `ChaosProxy` in front of clients: one test plays a full game through ~100
  dropped connections, another freezes a link without closing it. Run them after touching
  `GameClient`/`HostServer`/`GameHost`.
- `GameClient` never waits on Ktor's close or handshake: each connection runs in its own job, and the
  reconnect loop moves on as soon as the read loop stops (a dead link otherwise hangs for ages).
- What can only be checked on phones lives in `docs/DEVICE_TESTING.md`.

## Status

All six phases are done. Remaining risk is real-device behaviour; work from `docs/DEVICE_TESTING.md` results.
