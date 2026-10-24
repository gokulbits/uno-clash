# Assets

Everything the game shows or plays, and where it came from.

## Sounds and music

All files in `app/src/main/res/raw/` are synthesized by [`tools/sfx/generate_sounds.py`](tools/sfx/generate_sounds.py)
(Python standard library only). They're original work for this project and released under
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/), same as the script.

| File | Used for |
| --- | --- |
| `sfx_shuffle.wav` | Round start, reshuffle, hands swapped or passed |
| `sfx_deal.wav` | Each card dealt |
| `sfx_flip.wav` | Card thrown, starter card turned over |
| `sfx_land.wav` | A card landing on the discard pile |
| `sfx_draw.wav` | Drawing |
| `sfx_skip.wav` | Skip |
| `sfx_reverse.wav` | Reverse |
| `sfx_wild.wav` | Wild color chosen |
| `sfx_last_card.wav` | "UNO!" called |
| `sfx_penalty.wav` | Caught, challenge outcome |
| `sfx_win.wav` | You won the round |
| `sfx_lose.wav` | Someone else won the round |
| `sfx_turn.wav` | Your turn |
| `sfx_pop.wav` | Someone reacts with an emoji |
| `music_loop.wav` | Background music (112 bpm electric piano, loops seamlessly) |

To regenerate after tweaking the script:

```bash
python tools/sfx/generate_sounds.py
```

### Swapping in other CC0 sounds

Any file can be replaced with a CC0 sound (for example from [Kenney](https://kenney.nl/assets/category:Audio),
such as the Casino Audio, Interface Sounds or Music Jingles packs) as long as it keeps the same file name.
WAV and OGG both work. Add a row here with the source when you do.

## Graphics

- Cards, card backs, the logo, avatars' backgrounds, confetti and every other effect are drawn in code
  with Compose. Colors and fonts all come from `GameTheme` in
  `app/src/main/java/dev/colorclash/app/ui/theme/GameTheme.kt`.
- Avatars are standard emoji rendered by the phone's own emoji font.
- The launcher and notification icons are vector drawables made for this project (CC0).

## Fonts

The system sans-serif font, in heavy weights. No font files are bundled.
