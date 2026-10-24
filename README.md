# Color Clash

A colorful, offline, 2–6 player shed-your-hand card game for Android phones. One phone hosts and
everyone else joins over the same Wi-Fi or the host's hotspot. No internet, no accounts, no ads.

A private project for playing with friends, not published anywhere.

## Features

- 2–6 players, any mix of friends and bots, portrait only
- Standard 108-card deck: Skip, Reverse, Draw 2, Wild, Wild Draw 4
- "UNO!" calls with catching, the +4 challenge, scoring over rounds to a target
- House rules you can toggle in the lobby: stacking +2/+4, jump-in, 7-0 swaps, draw until playable, turn timer
- Join by QR code (the in-app scanner or the stock camera app), room code, nearby-room list, or IP address
- Dropped players get 60 seconds to come back; after that a bot keeps their seat warm until they rejoin
- Card animations, sound effects, music and haptics, all switchable in Settings
- Practice mode against bots on a single phone

## Install

Every push to `main` builds an APK on GitHub Actions; tagged versions are published as GitHub Releases.

1. Download `color-clash-<version>.apk` from the latest release (or the build artifacts of a workflow run).
2. Open it on the phone and allow installing from your browser or file manager when asked.
3. Everyone at the table should install **the same version**. A phone on a different version is refused
   with a message saying so.

Requires Android 8.0 (API 26) or newer.

## Play

1. Everyone opens the app and picks a name and avatar.
2. **One phone taps Host a game.** The lobby shows a room code, a QR code and the host's IP address.
   - **Same Wi-Fi:** everyone joins the same Wi-Fi network as the host.
   - **No Wi-Fi around:** the host taps *Turn on your hotspot*, switches the hotspot on, and the others
     connect to it. The QR code updates by itself once the hotspot is up.
3. **Everyone else taps Join a game** and either picks the room from *Rooms nearby*, scans the QR code,
   types the room code, or types the IP shown under the QR code.
4. Players tap **I'm ready**; the host sets house rules, adds bots for empty seats if wanted, and taps **Start game**.

On Android 17 and newer the app asks for permission to talk to devices on your local network the first
time you host or join. The game can't work without it.

During play: tap a highlighted card (or drag it up) to play it, tap the draw pile to draw, and hit
**UNO!** before you're down to one. If someone forgets, a **CATCH!** button appears on their seat.

## Troubleshooting

| Problem | Try |
| --- | --- |
| Room doesn't show under *Rooms nearby* | Normal on many routers, which block discovery. Scan the QR or type the room code instead. |
| "Couldn't reach the host" on a shared Wi-Fi | Guest or office networks often stop phones talking to each other. Use the host's hotspot instead. |
| Joined the hotspot but can't connect | Make sure the phone actually stayed on the hotspot Wi-Fi (some phones hop to mobile data when Wi-Fi has no internet), then type the room code. |
| Host's game stops when its screen is off | Some makers (Samsung, Xiaomi, OnePlus, Huawei…) kill background apps hard. On the host phone set the app's battery usage to **Unrestricted**: Settings › Apps › Color Clash › Battery. See [dontkillmyapp.com](https://dontkillmyapp.com). |
| Someone's app was closed mid-game | Open it again and tap **Rejoin game** on the home screen. Within 60 s nobody notices; after that a bot was keeping the seat warm. |
| "Install the same build on every phone" | Everyone needs the exact same APK version. |
| The host's app was closed | The game can't move to another phone; start a new room. |

Before a big game night, run through the [device test checklist](docs/DEVICE_TESTING.md) once with your phones.

## Build

Requirements: JDK 17 or newer (21 recommended) and the Android SDK. Gradle is downloaded by the wrapper.

```bash
./gradlew test                    # all unit, fuzz and integration tests
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # see Releases for signing
```

Install on a phone connected over USB with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Run the rules fuzz test harder with `./gradlew :rules:test -PfuzzGames=20000`.

## Releases

The [`Build APK`](.github/workflows/release.yml) workflow runs the tests and lint, builds a release APK,
and uploads it as an artifact. Pushing a tag like `v1.0.0` also publishes a GitHub Release with the APK.

Android only installs an update over an existing copy when both are signed with the same key, so CI
needs a permanent release key. Do this once:

```bash
keytool -genkeypair -v -keystore color-clash-release.jks -alias colorclash \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 color-clash-release.jks > color-clash-release.b64
gh secret set KEYSTORE_BASE64 < color-clash-release.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body colorclash
gh secret set KEY_PASSWORD
```

Keep the `.jks` file and its passwords somewhere safe outside the repo; losing them means friends have
to uninstall before they can install new versions. Without these secrets, CI still builds an APK signed
with a throwaway key, but it won't publish a release.

To sign a release build locally, export `CC_KEYSTORE_PATH`, `CC_KEYSTORE_PASSWORD`, `CC_KEY_ALIAS` and
`CC_KEY_PASSWORD` before running `assembleRelease`. Without them the release build uses the debug key.

## How it works

The host phone runs the whole game; the other phones only send what their player wants to do
("play this card", "draw") and show what the host sends back.

| Module | What's in it |
| --- | --- |
| `:rules` | The game itself: a pure `(state, action) -> (new state, events)` reducer, the bot, a seedable RNG. Plain Kotlin, no Android. |
| `:protocol` | The JSON messages between phones, each player's redacted view of the game, the join link format. |
| `:net` | The Ktor WebSocket host (seats, reconnects, bots, turn timer) and client (heartbeat, auto-reconnect). Plain Kotlin, tested headless. |
| `:app` | The Android app: Compose UI, animations, sound, the foreground service that keeps the host alive, Wi-Fi and mDNS glue. |

The host never sends anyone another player's cards, and checks every move against the rules before
applying it. See [ASSETS.md](ASSETS.md) for where sounds and graphics come from.
