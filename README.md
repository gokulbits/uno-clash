# Color Clash

A colorful, offline card game for 2–6 Android phones: shed your hand, call UNO, don't get caught. One
phone hosts and everyone else joins over the same Wi-Fi or the host's hotspot. No internet, no
accounts, no ads.

A private project for playing with friends, not published anywhere.

## Features

**The game**
- 2–6 players, any mix of friends and bots (Easy or Normal), portrait only
- Standard 108-card deck: Skip, Reverse, Draw 2, Wild, Wild Draw 4, with a fair shuffle and a real
  one-card-at-a-time deal every round
- UNO buzzer that's always on screen and never hints when to press it; forget and anyone can **CATCH!** you
- Challenge a Wild Draw 4, score every round, play to 100, 250 or 500 points, or a single round
- House rules: stack +2/+4, jump-in, 7-0 swaps, draw until playable, and a penalty for going out on a
  power card. The host's phone remembers them for the next room
- 15-second turn timer (or 30 s, 60 s, off). Run out three times in a row and a bot plays for you until
  you tap **I'm back**
- Next round starts by itself after a 15-second look at the scores; **Play again** for a rematch

**At the table**
- Cards fly, flip and land; skips, draws and challenges slam a stamp onto the player they hit
- Action cards and picked colors are called out on screen and out loud ("Skip!", "Blue!")
- Emoji reactions and quick-chat phrases that pop out of your seat
- Fanned card backs show how many cards everyone holds; tap a seat for the exact count, tap the pile to
  see the last few plays, and a NEXT tag shows who's up
- Sort your hand by color or number; optional tap-twice-to-play; optional colorblind shapes on every card
- Sound effects, music, voice and vibration, each switchable in Settings

**Make it yours**
- Use any photo from your phone as your avatar, cropped to a circle, or pick one of 50 built-in ones:
  animals plus characters from Malayalam, Tamil and English films and series
- Give it a color and a frame (gold, silver, rainbow, neon, sparkle, flames); everyone at the table sees it
- Pick your table color and card back design (just for your phone)
- Your games, wins and best round are kept in Settings

**Getting together**
- Rooms show up as "Sam's Clash". Join by QR code (the in-app scanner or any camera app), room code,
  the nearby-rooms list, or the host's IP. Every way in needs the room code
- A compact lobby: players, house rules and the Start / I'm ready button on about one screen
- Dropped players get 60 seconds to come back; after that a bot keeps their seat warm until they rejoin
- Practice against bots on a single phone

## Install

1. Download `color-clash-<version>.apk` from the [latest release](../../releases/latest).
2. Open it on the phone and allow installing from your browser or file manager when asked.
3. Everyone at the table needs **the same version**. A phone on a different one is told so when it joins.

Requires Android 8.0 or newer. **Coming from a build before 1.0.0?** Uninstall it first: those were
signed with a temporary key, so Android won't update them. From 1.0.0 on, new versions install over the top.

## Play

1. Everyone opens the app, picks a name and taps their avatar to choose a picture, color and frame.
2. **One phone taps Host a game.** The lobby shows the room ("Sam's Clash"), its code and a QR code.
   - **Same Wi-Fi:** everyone joins the host's Wi-Fi.
   - **No Wi-Fi around:** the host taps *Turn on your hotspot* and the others connect to it. The QR code
     updates by itself once the hotspot is up.
3. **Everyone else taps Join a game** and scans the QR, types the room code, or picks the room from
   *Rooms nearby* and types its code.
4. Players tap **I'm ready**; the host sets the house rules, adds bots for empty seats if wanted, and taps
   **Start game**.

During play: tap a highlighted card (or drag it up) to play it, and tap the draw pile to draw. On your turn,
hit the **UNO!** buzzer before you play your second-to-last card. If someone forgets, **CATCH!** appears on
their seat.

On Android 17 and newer the app asks for permission to talk to devices on your local network the first
time you host or join. The game can't work without it.

## Troubleshooting

| Problem | Try |
| --- | --- |
| Room doesn't show under *Rooms nearby* | Normal on many routers, which block discovery. Scan the QR or type the room code. |
| "Couldn't reach the host" on a shared Wi-Fi | Guest and office networks often stop phones talking to each other. Use the host's hotspot. |
| Joined the hotspot but can't connect | Make sure the phone stayed on the hotspot (some hop to mobile data when Wi-Fi has no internet), then type the room code. |
| Manual join by IP | On the host, tap the QR to enlarge it; the IP and port are underneath. |
| Host's game stops when its screen is off | Some makers (Samsung, Xiaomi, OnePlus, Huawei…) kill background apps hard. On the host set battery usage to **Unrestricted**: Settings › Apps › Color Clash › Battery. See [dontkillmyapp.com](https://dontkillmyapp.com). |
| Someone's app was closed mid-game | Open it again and tap **Rejoin game**. Within 60 s nobody notices; after that a bot was keeping the seat warm. |
| "Install the same build on every phone" | Everyone needs the same version. |
| "App not installed" when updating | The installed copy is from before 1.0.0. Uninstall it, then install. |
| The host's app was closed | The game can't move to another phone; start a new room. |

Before a big game night, run through the [device test checklist](docs/DEVICE_TESTING.md) once with your phones.

## Build

Needs JDK 17 or newer (21 recommended) and the Android SDK. Gradle comes with the wrapper.

```bash
./gradlew test                    # unit, fuzz and integration tests
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # signed if the release key is set up (below)
```

Install on a USB-connected phone with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
Fuzz the rules harder with `./gradlew :rules:test -PfuzzGames=20000`.

## Releases

The [`Build APK`](.github/workflows/release.yml) workflow runs the tests and lint and builds a signed
APK on every push to `main` and every pull request (download it from the run's artifacts).

**Publishing a version:** tag `main` and push the tag. The build is named after the tag and published
as a GitHub Release with the APK attached.

```bash
git tag v1.0.1 && git push origin v1.0.1
```

**The release key** (already set up). Android only installs an update over an existing copy signed
with the same key, so CI signs with a permanent key kept in four repository secrets. To set it up again:

1. Make a key. `keytool` comes with Android Studio (`jbr\bin\keytool.exe`) or any JDK:
   ```bash
   keytool -genkeypair -v -keystore color-clash-release.jks -alias colorclash -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Turn it into base64 text:
   - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("color-clash-release.jks")) | Set-Content color-clash-release.b64`
   - macOS/Linux: `base64 -w0 color-clash-release.jks > color-clash-release.b64` (macOS: `base64 -i … -o …`)
3. Add the secrets under Settings › Secrets and variables › Actions, or with the GitHub CLI from inside
   the project folder:
   ```powershell
   Get-Content color-clash-release.b64 -Raw | gh secret set KEYSTORE_BASE64
   gh secret set KEYSTORE_PASSWORD
   gh secret set KEY_ALIAS --body colorclash
   gh secret set KEY_PASSWORD
   ```

Keep the `.jks` and its password somewhere safe outside the repo: losing them means everyone has to
uninstall before they can update. Without the secrets, CI signs with a throwaway key and refuses to
publish a release. To sign locally, export `CC_KEYSTORE_PATH`, `CC_KEYSTORE_PASSWORD`, `CC_KEY_ALIAS`
and `CC_KEY_PASSWORD` before `assembleRelease`.

## How it works

The host phone runs the whole game; the other phones only send what their player wants to do
("play this card", "draw") and show what the host sends back.

| Module | What's in it |
| --- | --- |
| `:rules` | The game itself: a pure `(state, action) -> (new state, events)` reducer, the bots, a seedable RNG. Plain Kotlin, no Android. |
| `:protocol` | The JSON messages between phones, each player's redacted view of the game, the join link format. |
| `:net` | The Ktor WebSocket host (seats, reconnects, bots, turn timer, photos) and client (heartbeat, auto-reconnect). Plain Kotlin, tested headless. |
| `:app` | The Android app: Compose UI, animations, sound and voice, the foreground service that keeps the host alive, Wi-Fi and mDNS glue. |

The host never sends anyone another player's cards and checks every move against the rules before
applying it. See [ASSETS.md](ASSETS.md) for where sounds and graphics come from.
