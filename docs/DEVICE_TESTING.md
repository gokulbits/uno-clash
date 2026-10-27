# Real-device test checklist

The automated tests cover the rules, the protocol and the networking (including a game played through
~100 dropped connections, and a phone that freezes, loses its seat to a bot and takes it back). What they
can't cover is real radios, real batteries and real Android builds. This list is for that.

Run it with at least **3 phones**, ideally from different makers and Android versions, all on the same APK.
Note each phone's model and Android version at the top of your results.

Tick a box only when the **Expect** line matches what you saw. Anything else is a bug; jot down what happened.

Handy commands for a phone on USB (`adb devices` to check it's connected):

```bash
adb shell am kill dev.colorclash                 # kill the app like Android does in the background
adb shell dumpsys deviceidle force-idle          # push a screen-off phone into Doze right now
adb shell dumpsys deviceidle unforce             # and back out
adb logcat -s GameHost SessionManager RoomDiscovery AndroidRuntime
```

## 1. First run

- [ ] Install the APK on every phone; open it, pick a name and avatar.
  **Expect:** the home screen is portrait, colorful, and the logo letters are readable.
- [ ] Rotate a phone while on the home screen and in a game.
  **Expect:** it stays portrait (tablets and unfolded foldables on Android 16+ may rotate; phones must not).
- [ ] Host a game for the first time on Android 17+.
  **Expect:** a "local network" permission prompt, then the notifications prompt. Denying local network shows a
  message explaining the game needs it.
- [ ] While hosting, pull down the notification shade.
  **Expect:** a "Hosting Color Clash" notification with the room code; tapping it opens the game.

## 2. Joining on the same Wi-Fi

Host on one phone; join from the others using each method once.

- [ ] **Rooms nearby:** the room shows up within ~5 s on the Join screen, without its code. Tap it.
  **Expect:** a box asking for the code; a wrong code says so and stays on the Join screen.
- [ ] **In-app QR scan:** scan the lobby QR.
- [ ] **Stock camera app:** point the phone's normal camera at the QR, tap the link it offers.
  **Expect:** the game opens and joins.
- [ ] **Room code:** type the 4-letter code.
- [ ] **Manual IP:** tap the host's QR to enlarge it and type the IP shown underneath.
- [ ] **Expect for all:** the joiner appears in every phone's lobby within a second or two; toggling Ready
  updates everywhere; the host's rule toggles show up on guests.
- [ ] Try joining with a wrong code. **Expect:** a clear "doesn't match" message, back on the join screen.
- [ ] Start a game, then try to join from another phone without having been in it.
  **Expect:** "A game is already running".

## 3. Hotspot (no router)

- [ ] Host taps **Turn on your hotspot**; switch the hotspot on in settings; come back to the lobby.
  **Expect:** within ~3 s the QR and IP update to the hotspot address.
- [ ] Guests connect to the hotspot **with their mobile data on**. Join by room code.
  **Expect:** joins (the app finds the host via the hotspot gateway even if "Rooms nearby" is empty).
- [ ] Play a full round. **Expect:** no drops. Check a guest's Wi-Fi settings: it should still be on the hotspot,
  not switched to mobile data because "Wi-Fi has no internet".
- [ ] Repeat with the host's own mobile data **off**. **Expect:** same.

## 4. Screen off and Doze

- [ ] **Host screen off** mid-game for 2 minutes while guests keep playing (the host is a bot-free human, so
  make sure it isn't the host's turn, or use a turn timer).
  **Expect:** guests' moves keep going through; the host sees the right state when the screen comes back.
- [ ] Same, but also run `adb shell dumpsys deviceidle force-idle` on the host after the screen is off.
  **Expect:** the game keeps running. If it stops, note the phone model (see Troubleshooting in the README).
- [ ] **Guest screen off** for 20 s on someone else's turn, then back on.
  **Expect:** the table catches up straight away with no error.
- [ ] **Guest screen off** for 90 s while it's their turn.
  **Expect:** others see them marked 🤖 after ~60 s and the bot plays for them; when the screen comes back they
  get their seat back (🤖 disappears) and can play.

## 5. Background and process death

- [ ] Guest presses Home for 30 s, then reopens the app. **Expect:** back at the table, up to date.
- [ ] Guest swipes the app away from Recents mid-game, then opens it again.
  **Expect:** home screen offers **Rejoin game XXXX**; tapping it puts them back in the same seat with their hand.
- [ ] `adb shell am kill dev.colorclash` on a guest in the background, then reopen. **Expect:** same as above.
- [ ] Host presses Home and opens another app for 2 minutes. **Expect:** the game keeps going for guests.
- [ ] Host swipes the app away from Recents. **Expect (most phones):** the game keeps running thanks to the
  notification; reopening shows the table. If the phone kills it, guests should see "Connection lost,
  reconnecting…" and eventually "Lost connection to the host" (the game can't move hosts).

## 6. Flaky Wi-Fi

- [ ] Turn a guest's Wi-Fi off for 10 s, then on. **Expect:** red "reconnecting" banner, then back in play with
  the right hand, no duplicate or missing cards.
- [ ] Airplane mode on a guest for 70 s. **Expect:** a bot covers them after 60 s; back in their seat on return.
- [ ] Walk a guest phone to the edge of Wi-Fi range and back during play. **Expect:** recovers on its own.
- [ ] Play 10 minutes on the host's hotspot with Bluetooth headphones connected to a guest.
  **Expect:** no "reconnecting" banner at all (a short freeze is fine; the link should ride it out).
- [ ] Turn the **host's** Wi-Fi off for 5 s and on (router mode only). **Expect:** guests reconnect once it's back
  (the host's IP may change; if it does, guests can't find it, which is expected).
- [ ] Spam taps on cards and the draw pile during a reconnect. **Expect:** nothing played twice, a short
  "Not connected" or rejection message at most.

## 7. Version mismatch

- [ ] Install an older build on one phone and try to join. **Expect:** "This phone has game version X but the host
  has Y. Install the same build on every phone."

## 8. Full table and feel

- [ ] 6 seats (mix of phones and bots), play to 500 with stacking, jump-in and 7-0 on.
- [ ] Every rule shows something on screen: skip flash, reverse spin, color burst after a wild, shake on +2/+4,
  "UNO!" and "CATCH!", challenge result, hands swapping, confetti on a win.
- [ ] Skip, Reverse, +2, Wild and +4 each pop a banner in the card's color as the card lands, and the phone says
  it out loud ("Skip!", "Wild draw four!"). Picking a color says the color; calling UNO says "UNO!". The
  "Announce cards out loud" switch silences the voice but keeps the banners. On a phone with no speech engine
  installed, nothing breaks; there's just no voice.
- [ ] With music on, play a few action cards. **Expect:** the music sits well under the voice, dips almost to
  silence while a call-out plays, and comes straight back after it.
- [ ] Tap 😊 and send a few reactions. **Expect:** every phone sees the emoji pop out of your seat with a stream of
  small copies and a soft pop; mashing the tray sends at most one every 0.7 s.
- [ ] Send the **Faaa!** bubble. **Expect:** every phone shows it and plays the Faaa clip instead of the pop,
  at a sensible volume next to the other sounds; with sound off in Settings, it's silent.
- [ ] Each turn shows a 15-second countdown in the ring by "Your turn", red and pulsing for the last few seconds;
  when it runs out a move is made for you.
- [ ] Turn on Developer options › Profile HWUI rendering › On screen as bars during dealing and confetti.
  **Expect:** bars mostly under the green line on a mid-range phone.
- [ ] Settings: sound, music and vibration toggles each take effect immediately, in the menu and from ⚙ in game.
  Music stops when the app goes to the background.
- [ ] Home › tap your avatar: flip through the four tabs, pick a character, a color and each frame. **Expect:**
  every emoji shows (no empty boxes on older phones) and the preview updates straight away. Join a room:
  other phones show the same avatar, color and frame in the lobby and at the table.
- [ ] Avatar picker › Photo › Choose a photo: pick a portrait camera shot and a screenshot. **Expect:** the
  picture is the right way up, drags and pinches smoothly, and the circle shows exactly what you framed.
  Join a room: every phone shows your photo (with your frame) in the lobby, at the table and on the
  scoreboard. Try it on an Android 8 phone too (older picture loading path).
- [ ] Settings › Table and cards: pick each table color and card back. **Expect:** the background changes
  at once; the deck, opponents' fanned hands and dealt cards use the new back.
- [ ] Host a room, change a few house rules, close it and host again. **Expect:** the new room starts with
  those rules.
- [ ] Join as a guest. **Expect:** without scrolling, the I'm ready button sits at the bottom, pulsing until
  it's tapped. The lobby fits on about one screen (the host's scrolls a little further for the QR card).
- [ ] Set the phone's font size to the largest. **Expect:** the table still fits without overlapping text.
- [ ] Take a phone call mid-game on a guest. **Expect:** back in the game after hanging up.

## 9. After the hardening pass

These cover the changes from the memory, concurrency and security pass. Nothing here can be checked
on the JVM.

- [ ] Install the **release** build (`./gradlew :app:assembleRelease`), which is now minified and
  resource-shrunk by R8, on every phone and play a full game. **Expect:** phones find each other,
  the lobby and table look the same as debug, and the game plays to the end. A missing ProGuard keep
  shows up here as a crash on connect (serialization) or a blank scan screen (ZXing).
- [ ] Release build › Settings › scan a QR to join. **Expect:** the camera opens and a code is read.
- [ ] Mid-game, back out of the scan screen the instant it opens, repeatedly. **Expect:** no crash,
  and the camera light goes out each time.
- [ ] Guest, mid-game: turn Wi-Fi off, wait for "reconnecting", turn it back on. **Expect:** the same
  seat and hand come back. The rejoin now proves the token instead of sending it, so a failure here
  looks like being given a *new* seat, or the room being full.
- [ ] Two phones, same seat: let one guest drop, then rejoin from the app on that same phone.
  **Expect:** the seat comes back. The phone that lost it (if still open) says the seat was taken
  over from another phone rather than just going quiet.
- [ ] Kill a guest from Recents mid-game and reopen within two hours. **Expect:** the rejoin offer
  still appears on Home. (The saved token is now keystore-encrypted; a token written by an older
  build won't decrypt, so the first launch after updating offers no rejoin. That's expected once.)
- [ ] Play with music on, background the app for a minute, come back. **Expect:** the music restarts
  from the top rather than resuming — it's released while the app is away — and card sounds still play.
- [ ] Fire several long reaction clips (Sus, Eh eh eh, Woww) in a row during dealing. **Expect:** no
  stutter as each one starts, and the music dips under each.
- [ ] Profile HWUI rendering › On screen as bars, then watch an opponent's turn with the avatar
  pulsing, and a win with confetti. **Expect:** bars under the green line; this pass removed the
  per-frame recompositions that drove both.
- [ ] Host, then tap Host/Practice/Join quickly in succession. **Expect:** one room, and no leftover
  hosting notification after leaving.

## Results

| Phone | Android | Sections passed | Notes |
| --- | --- | --- | --- |
|  |  |  |  |
