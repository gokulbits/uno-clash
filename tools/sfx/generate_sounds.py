"""
Synthesizes every sound effect and the music loop for the game.

Standard library only, so it runs anywhere with Python 3.8+:
    python tools/sfx/generate_sounds.py

Writes 16-bit mono WAVs into app/src/main/res/raw. The output is original work and is
released under CC0 along with this script (see ASSETS.md).
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 22050
OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"
rng = random.Random(7)  # fixed seed so the files are reproducible


# --- building blocks -------------------------------------------------------

def silence(seconds):
    return [0.0] * int(seconds * RATE)


def note_freq(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


def osc(kind, freq, seconds, freq_end=None):
    """Oscillator with an optional linear pitch glide."""
    n = int(seconds * RATE)
    out, phase = [], 0.0
    for i in range(n):
        f = freq if freq_end is None else freq + (freq_end - freq) * i / max(1, n - 1)
        phase += f / RATE
        p = phase % 1.0
        if kind == "sine":
            v = math.sin(2 * math.pi * p)
        elif kind == "square":
            v = 1.0 if p < 0.5 else -1.0
        elif kind == "pulse":
            v = 1.0 if p < 0.25 else -1.0
        elif kind == "triangle":
            v = 4 * abs(p - 0.5) - 1
        elif kind == "saw":
            v = 2 * p - 1
        else:
            raise ValueError(kind)
        out.append(v)
    return out


def noise(seconds):
    return [rng.uniform(-1, 1) for _ in range(int(seconds * RATE))]


def lowpass(samples, cutoff):
    """One-pole low-pass; cutoff can be a number or a function of progress 0..1."""
    out, y = [], 0.0
    n = len(samples)
    for i, x in enumerate(samples):
        c = cutoff(i / max(1, n - 1)) if callable(cutoff) else cutoff
        a = 1 - math.exp(-2 * math.pi * c / RATE)
        y += a * (x - y)
        out.append(y)
    return out


def envelope(samples, attack=0.005, decay=0.1, sustain=0.0, release=0.05):
    n = len(samples)
    a, d, r = int(attack * RATE), int(decay * RATE), int(release * RATE)
    out = []
    for i, x in enumerate(samples):
        if i < a:
            g = i / max(1, a)
        elif i < a + d:
            g = 1 - (1 - sustain) * (i - a) / max(1, d)
        else:
            g = sustain
        if i > n - r:
            g *= (n - i) / max(1, r)
        out.append(x * g)
    return out


def exp_decay(samples, speed):
    return [x * math.exp(-speed * i / RATE) for i, x in enumerate(samples)]


def gain(samples, g):
    return [x * g for x in samples]


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def at(samples, seconds, total=None):
    """Place samples starting at a time offset."""
    pad = silence(seconds) + samples
    if total is not None:
        pad = (pad + silence(total))[: int(total * RATE)]
    return pad


def concat(*parts):
    out = []
    for p in parts:
        out.extend(p)
    return out


def tone(kind, semis, seconds, vol=1.0, decay=None, attack=0.004):
    s = osc(kind, note_freq(semis), seconds)
    s = envelope(s, attack=attack, decay=seconds * 0.9 if decay is None else decay, sustain=0.0, release=0.02)
    return gain(s, vol)


def normalize(samples, peak=0.85):
    top = max(1e-9, max(abs(x) for x in samples))
    return [x * peak / top for x in samples]


def write(name, samples, peak=0.85):
    samples = normalize(samples, peak)
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in samples))
    print(f"{name}.wav  {len(samples) / RATE:.2f}s")


# --- sound effects ---------------------------------------------------------

def card_snap(length=0.07, bright=5000):
    body = exp_decay(lowpass(noise(length), bright), 60)
    thump = exp_decay(osc("sine", 180, length, 90), 50)
    return mix(body, gain(thump, 0.5))


def sfx_shuffle():
    # A riffle: lots of tiny snaps speeding up, then a settle.
    total, t = 0.75, 0.0
    track = silence(total)
    gap = 0.03
    while t < total - 0.1:
        track = mix(track, at(gain(card_snap(0.025, rng.uniform(3000, 6000)), rng.uniform(0.4, 0.8)), t, total))
        t += gap
        gap = max(0.012, gap * 0.93)
    return mix(track, at(card_snap(0.09, 2500), total - 0.1, total))


def sfx_deal():
    return card_snap(0.06, 6000)


def sfx_flip():
    return mix(card_snap(0.09, 4500), gain(tone("triangle", 15, 0.08), 0.15))


def sfx_draw():
    slide = lowpass(noise(0.16), lambda p: 800 + 5000 * p)
    return mix(envelope(slide, attack=0.02, decay=0.14), at(gain(card_snap(0.05, 4000), 0.6), 0.12))


def sfx_skip():
    return concat(tone("square", 7, 0.09, 0.5), tone("square", 2, 0.14, 0.5))


def sfx_reverse():
    up = envelope(osc("triangle", 330, 0.17, 660), decay=0.17)
    down = envelope(osc("triangle", 660, 0.17, 330), decay=0.17)
    return mix(concat(up, down), gain(concat(silence(0.02), up, down), 0.35))


def sfx_wild():
    # Sparkly rising arpeggio over a shimmer.
    notes = [3, 7, 10, 15, 19, 22]
    track = silence(0.65)
    for i, n in enumerate(notes):
        bell = mix(tone("sine", n, 0.35, 0.6), tone("sine", n + 12, 0.25, 0.2))
        track = mix(track, at(bell, i * 0.05, 0.65))
    shimmer = envelope(lowpass(noise(0.6), 9000), attack=0.05, decay=0.55)
    return mix(track, gain(shimmer, 0.08))


def sfx_last_card():
    return concat(tone("pulse", 12, 0.12, 0.55), tone("pulse", 19, 0.26, 0.6, decay=0.26))


def sfx_penalty():
    buzz = envelope(osc("saw", 160, 0.45, 70), attack=0.005, decay=0.45)
    return lowpass(mix(buzz, gain(envelope(osc("square", 80, 0.45, 50), decay=0.45), 0.4)), 1800)


def sfx_win():
    fanfare = [(0, 0.12), (4, 0.12), (7, 0.12), (12, 0.35), (7, 0.1), (12, 0.6)]
    track, t = silence(1.6), 0.0
    for semis, length in fanfare:
        voice = mix(tone("pulse", semis + 3, length + 0.1, 0.45), tone("triangle", semis - 9, length + 0.1, 0.35))
        track = mix(track, at(voice, t, 1.6))
        t += length
    return track


def sfx_lose():
    track, t = silence(1.1), 0.0
    for semis in (3, 1, -2):
        track = mix(track, at(tone("triangle", semis, 0.35, 0.6), t, 1.1))
        t += 0.28
    return track


def sfx_turn():
    return mix(tone("sine", 22, 0.35, 0.6), tone("sine", 34, 0.2, 0.15))


# --- music -----------------------------------------------------------------

def music_loop():
    """Bouncy 4-bar chiptune loop, 128 bpm, played twice with a variation."""
    bpm = 128
    beat = 60 / bpm
    step = beat / 2  # eighth notes
    bars = 8
    total = bars * 4 * beat
    track = silence(total)

    chords = [(0, 4, 7), (-3, 0, 4), (5, 9, 12), (7, 11, 14)]  # C, Am, F, G (relative to C4 = -9 from A4)
    root_shift = -9
    melody = [
        [12, None, 14, 16, None, 19, 16, 14], [12, None, 16, None, 12, 9, None, 7],
        [9, None, 12, 14, None, 17, 16, 14], [14, None, 11, None, 7, 9, 11, None],
    ]
    variation = [
        [19, None, 16, 19, None, 21, 19, 16], [16, None, 12, None, 16, 14, 12, None],
        [17, None, 21, 19, None, 17, 16, 14], [11, 14, 19, None, 17, 16, 14, None],
    ]
    for bar in range(bars):
        chord = chords[bar % 4]
        start = bar * 4 * beat
        line = (melody if bar < 4 else variation)[bar % 4]
        for s in range(8):
            t = start + s * step
            # Bass: root on beats, fifth on offbeats.
            bass_note = chord[0] + root_shift - 12 + (7 if s % 2 else 0)
            track = mix(track, at(tone("triangle", bass_note, step * 0.9, 0.55), t, total))
            # Arpeggio sparkle.
            arp = chord[s % 3] + root_shift + 12
            track = mix(track, at(tone("pulse", arp, step * 0.5, 0.12), t, total))
            # Lead.
            if line[s] is not None:
                track = mix(track, at(tone("square", line[s] + root_shift, step * 0.95, 0.22, decay=step), t, total))
            # Drums: kick on 1 and 3, snare on 2 and 4, hats on every eighth.
            if s in (0, 4):
                kick = exp_decay(osc("sine", 120, 0.15, 45), 25)
                track = mix(track, at(gain(kick, 0.7), t, total))
            if s in (2, 6):
                snare = exp_decay(lowpass(noise(0.12), 5000), 30)
                track = mix(track, at(gain(snare, 0.35), t, total))
            hat = exp_decay(lowpass(noise(0.03), 9000), 120)
            track = mix(track, at(gain(hat, 0.12), t, total))
    return track


if __name__ == "__main__":
    write("sfx_shuffle", sfx_shuffle())
    write("sfx_deal", sfx_deal(), peak=0.6)
    write("sfx_flip", sfx_flip(), peak=0.75)
    write("sfx_draw", sfx_draw(), peak=0.7)
    write("sfx_skip", sfx_skip(), peak=0.7)
    write("sfx_reverse", sfx_reverse(), peak=0.7)
    write("sfx_wild", sfx_wild())
    write("sfx_last_card", sfx_last_card())
    write("sfx_penalty", sfx_penalty())
    write("sfx_win", sfx_win())
    write("sfx_lose", sfx_lose(), peak=0.7)
    write("sfx_turn", sfx_turn(), peak=0.6)
    write("music_loop", music_loop(), peak=0.55)
