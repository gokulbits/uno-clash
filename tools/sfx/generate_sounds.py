"""
Synthesizes every sound effect and the music loop for the game.

Standard library only, so it runs anywhere with Python 3.8+:
    python tools/sfx/generate_sounds.py

Writes 16-bit mono 44.1 kHz WAVs into app/src/main/res/raw. The output is original work and is
released under CC0 along with this script (see ASSETS.md).
"""
import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "res" / "raw"
rng = random.Random(7)  # fixed seed so the files are reproducible
TAU = 2 * math.pi


# --- building blocks -------------------------------------------------------

def n_samples(seconds):
    return int(seconds * RATE)


def silence(seconds):
    return [0.0] * n_samples(seconds)


def note_freq(semitones_from_a4):
    return 440.0 * 2 ** (semitones_from_a4 / 12)


def noise(seconds):
    return [rng.uniform(-1, 1) for _ in range(n_samples(seconds))]


def sine(freq, seconds, freq_end=None):
    n = n_samples(seconds)
    out, phase = [0.0] * n, 0.0
    for i in range(n):
        f = freq if freq_end is None else freq * (freq_end / freq) ** (i / max(1, n - 1))
        phase += f / RATE
        out[i] = math.sin(TAU * phase)
    return out


def osc(kind, freq, seconds, freq_end=None):
    """Naive oscillators; only used under a low-pass, so aliasing stays out of earshot."""
    n = n_samples(seconds)
    out, phase = [0.0] * n, 0.0
    for i in range(n):
        f = freq if freq_end is None else freq * (freq_end / freq) ** (i / max(1, n - 1))
        phase += f / RATE
        p = phase % 1.0
        if kind == "saw":
            out[i] = 2 * p - 1
        elif kind == "square":
            out[i] = 1.0 if p < 0.5 else -1.0
        elif kind == "triangle":
            out[i] = 4 * abs(p - 0.5) - 1
        else:
            raise ValueError(kind)
    return out


def biquad(samples, kind, freq, q=0.707):
    """RBJ cookbook filter. [freq] can be a number or a function of progress 0..1 (a sweep)."""
    out = [0.0] * len(samples)
    x1 = x2 = y1 = y2 = 0.0
    n = len(samples)
    b0 = b1 = b2 = a1 = a2 = 0.0
    for i, x in enumerate(samples):
        if i % 32 == 0:
            f = freq(i / max(1, n - 1)) if callable(freq) else freq
            f = min(max(f, 20.0), RATE * 0.45)
            w0 = TAU * f / RATE
            cw, alpha = math.cos(w0), math.sin(w0) / (2 * q)
            if kind == "lowpass":
                b0, b1, b2 = (1 - cw) / 2, 1 - cw, (1 - cw) / 2
            elif kind == "highpass":
                b0, b1, b2 = (1 + cw) / 2, -(1 + cw), (1 + cw) / 2
            elif kind == "bandpass":
                b0, b1, b2 = alpha, 0.0, -alpha
            else:
                raise ValueError(kind)
            a0 = 1 + alpha
            b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, -2 * cw / a0, (1 - alpha) / a0
        y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, x, y1, y
        out[i] = y
    return out


def env_ad(samples, attack, decay_rate):
    """Linear attack, then exponential decay at [decay_rate] (1/seconds)."""
    a = max(1, n_samples(attack))
    return [x * (i / a if i < a else math.exp(-decay_rate * (i - a) / RATE)) for i, x in enumerate(samples)]


def env_shape(samples, attack, hold, release):
    """Attack, hold flat, then a smooth (cosine) release."""
    a, h, r = n_samples(attack), n_samples(hold), max(1, n_samples(release))
    out = []
    for i, x in enumerate(samples):
        if i < a:
            g = i / max(1, a)
        elif i < a + h:
            g = 1.0
        elif i < a + h + r:
            g = 0.5 + 0.5 * math.cos(math.pi * (i - a - h) / r)
        else:
            g = 0.0
        out.append(x * g)
    return out


def gain(samples, g):
    return [x * g for x in samples]


class Track:
    """A mix bus: drop sounds in at a time offset, in place, without rebuilding lists."""

    def __init__(self, seconds):
        self.buf = [0.0] * n_samples(seconds)

    def add(self, samples, at=0.0, vol=1.0):
        start = n_samples(at)
        buf = self.buf
        end = min(len(buf), start + len(samples))
        for i in range(start, end):
            buf[i] += samples[i - start] * vol
        return self


def fade_edges(samples, seconds=0.004):
    n = min(len(samples) // 2, n_samples(seconds))
    out = list(samples)
    for i in range(n):
        g = i / n
        out[i] *= g
        out[-1 - i] *= g
    return out


def reverb(samples, room=0.75, damp=0.35, wet=0.25, tail=0.8):
    """A small Schroeder/Freeverb-style room: four damped combs into two allpasses."""
    dry = samples + [0.0] * n_samples(tail)
    combs = [1116, 1188, 1277, 1356]
    feedback = room * 0.28 + 0.7
    wet_out = [0.0] * len(dry)
    for d in combs:
        buf, idx, store = [0.0] * d, 0, 0.0
        for i, x in enumerate(dry):
            y = buf[idx]
            store = y * (1 - damp) + store * damp
            buf[idx] = x * 0.015 + store * feedback
            idx = idx + 1 if idx + 1 < d else 0
            wet_out[i] += y
    for d in (556, 441):
        buf, idx = [0.0] * d, 0
        for i, x in enumerate(wet_out):
            b = buf[idx]
            wet_out[i] = -x + b
            buf[idx] = x + b * 0.5
            idx = idx + 1 if idx + 1 < d else 0
    peak_dry = max(1e-9, max(abs(x) for x in samples))
    peak_wet = max(1e-9, max(abs(x) for x in wet_out))
    k = wet * peak_dry / peak_wet
    return [x + w * k for x, w in zip(dry, wet_out)]


def bell(semis, seconds, vol=1.0, index=2.2, ratio=3.5, decay=5.0):
    """FM bell: bright at the strike, mellowing as the modulation dies away."""
    f = note_freq(semis)
    n = n_samples(seconds)
    out = [0.0] * n
    attack = n_samples(0.002)
    for i in range(n):
        t = i / RATE
        env = math.exp(-decay * t) * (i / attack if i < attack else 1.0)
        idx = index * math.exp(-decay * 1.6 * t)
        out[i] = vol * env * math.sin(TAU * f * t + idx * math.sin(TAU * f * ratio * t))
    return fade_edges(out, 0.01)


def marimba(semis, seconds, vol=1.0, decay=7.0):
    """Wooden bar: a fundamental, a quickly fading overtone near 4x, and a soft mallet tick."""
    f = note_freq(semis)
    n = n_samples(seconds)
    out = [0.0] * n
    attack = n_samples(0.0015)
    for i in range(n):
        t = i / RATE
        a = i / attack if i < attack else 1.0
        out[i] = vol * a * (
            math.sin(TAU * f * t) * math.exp(-decay * t)
            + 0.3 * math.sin(TAU * f * 3.93 * t) * math.exp(-decay * 4 * t)
            + 0.12 * math.sin(TAU * f * 9.2 * t) * math.exp(-decay * 9 * t)
        )
    tick = env_ad(biquad(noise(0.012), "bandpass", 3000, 1.2), 0.0005, 400)
    return fade_edges(Track(seconds).add(out).add(tick, vol=0.25 * vol).buf, 0.01)


def soft_clip(samples, drive=1.0):
    return [math.tanh(x * drive) for x in samples]


def normalize(samples, peak=0.85):
    top = max(1e-9, max(abs(x) for x in samples))
    return [x * peak / top for x in samples]


def trim_tail(samples, floor=0.001):
    """Drops the near-silent end of a reverb tail (below -60 dB of the peak)."""
    limit = floor * max(1e-9, max(abs(x) for x in samples))
    end = len(samples)
    while end > 1 and abs(samples[end - 1]) < limit:
        end -= 1
    return samples[:end]


def write(name, samples, peak=0.85, trim=True):
    if trim:
        samples = trim_tail(samples)
    samples = normalize(fade_edges(samples, 0.003), peak)
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x)) * 32767)) for x in samples))
    print(f"{name}.wav  {len(samples) / RATE:.2f}s")


# --- card sounds -----------------------------------------------------------

def swish(seconds, lo, hi, q=1.1, attack=0.012):
    """Air moving past a card: band-passed noise sweeping up, swelling in and dying away."""
    s = biquad(noise(seconds), "bandpass", lambda p: lo * (hi / lo) ** p, q)
    return env_shape(s, attack, seconds * 0.25, seconds * 0.75 - attack)


def snap(seconds=0.02, center=4000, q=1.4):
    """The crisp edge of a card hitting something."""
    return env_ad(biquad(noise(seconds), "bandpass", center, q), 0.0005, 180)


def thud(freq=150, seconds=0.06):
    """The table under the card: a short, dull low knock."""
    return env_ad(sine(freq, seconds, freq * 0.6), 0.001, 70)


def sfx_flip():
    # A card leaving the hand: a flick of the edge, then a short airy toss.
    t = Track(0.2)
    t.add(snap(0.01, 5500, 2.0), vol=0.35)
    t.add(swish(0.17, 1200, 4200), at=0.004, vol=1.0)
    return t.buf


def sfx_land():
    # A card slapping onto the pile: a papery snap with a little body under it.
    t = Track(0.16)
    t.add(snap(0.025, 2600, 0.9), vol=1.0)
    t.add(thud(140, 0.07), vol=0.55)
    t.add(env_ad(biquad(noise(0.12), "lowpass", 2500), 0.002, 45), vol=0.18)
    return t.buf


def sfx_deal():
    # Lighter and quicker than a throw, since there are lots of them in a row.
    t = Track(0.12)
    t.add(swish(0.09, 2000, 5500, q=1.4, attack=0.006), vol=0.8)
    t.add(snap(0.015, 3800, 1.6), at=0.075, vol=0.5)
    return t.buf


def sfx_draw():
    # Sliding a card off the deck: gritty friction that rises, then it lifts clear.
    seconds = 0.26
    grit = [x * (0.6 + 0.4 * math.sin(TAU * 38 * i / RATE + rng.random())) for i, x in enumerate(noise(seconds))]
    slide = env_shape(biquad(grit, "bandpass", lambda p: 900 * (3.2 ** p), 0.9), 0.03, 0.1, 0.13)
    t = Track(0.32)
    t.add(slide)
    t.add(swish(0.1, 2500, 5000, attack=0.008), at=0.2, vol=0.5)
    return t.buf


def sfx_shuffle():
    # A riffle: lots of tiny snaps speeding up over a rustle, then the bridge and a squared-off tap.
    total = 0.95
    t = Track(total)
    when, gap = 0.02, 0.026
    while when < 0.62:
        t.add(snap(0.006, rng.uniform(3000, 6500), 2.5), at=when, vol=rng.uniform(0.35, 0.75))
        when += gap * rng.uniform(0.8, 1.2)
        gap = max(0.009, gap * 0.94)
    t.add(env_shape(biquad(noise(0.62), "bandpass", 2200, 0.6), 0.05, 0.4, 0.17), at=0.02, vol=0.25)
    t.add(swish(0.18, 700, 2500, q=0.8, attack=0.02), at=0.62, vol=0.7)
    t.add(snap(0.03, 2200, 0.8), at=0.82, vol=0.8)
    t.add(thud(120, 0.06), at=0.82, vol=0.5)
    return t.buf


# --- table events ----------------------------------------------------------

def sfx_skip():
    # Two wooden knocks stepping down: "nope".
    t = Track(0.55)
    t.add(marimba(7, 0.35, decay=9), vol=0.9)
    t.add(marimba(0, 0.4, decay=8), at=0.11, vol=0.9)
    return reverb(t.buf, wet=0.18, tail=0.3)


def sfx_reverse():
    # A swoop up and back down, with two notes that trade places.
    t = Track(0.6)
    t.add(swish(0.25, 600, 3000, q=0.9, attack=0.05), vol=0.45)
    t.add(swish(0.25, 3000, 600, q=0.9, attack=0.02), at=0.22, vol=0.45)
    t.add(bell(12, 0.4, decay=7, index=1.4), at=0.02, vol=0.5)
    t.add(bell(19, 0.45, decay=6, index=1.4), at=0.14, vol=0.45)
    t.add(bell(12, 0.4, decay=7, index=1.4), at=0.26, vol=0.4)
    return reverb(t.buf, wet=0.25, tail=0.5)


def sfx_wild():
    # Sparkly rising arpeggio of bells over a shimmer.
    t = Track(1.0)
    for i, semis in enumerate([15, 19, 22, 27, 31, 34]):
        t.add(bell(semis, 0.7, decay=4.5, index=2.0), at=i * 0.055, vol=0.55)
    shimmer = env_shape(biquad(noise(0.8), "highpass", 7000), 0.1, 0.25, 0.45)
    t.add(shimmer, at=0.05, vol=0.08)
    return reverb(t.buf, wet=0.35, tail=0.9)


def sfx_last_card():
    # A bright two-note chime that gets your attention without nagging.
    t = Track(0.7)
    t.add(bell(22, 0.5, decay=5, index=2.5), vol=0.8)
    t.add(bell(29, 0.6, decay=4, index=2.5), at=0.12, vol=0.9)
    t.add(bell(34, 0.5, decay=5, index=1.5), at=0.12, vol=0.25)
    return reverb(t.buf, wet=0.28, tail=0.6)


def sfx_penalty():
    # "Womp womp": a muffled brassy fall, twice, on a soft knock.
    t = Track(0.75)
    for at, (a, b) in ((0.0, (-14, -16)), (0.24, (-17, -21))):
        f0, f1 = note_freq(a), note_freq(b)
        horn = mix_voices(osc("saw", f0, 0.3, f1), osc("square", f0 * 0.5, 0.3, f1 * 0.5), 0.35)
        horn = biquad(horn, "lowpass", lambda p: 1800 - 1300 * p, 1.6)
        t.add(env_shape(horn, 0.015, 0.12, 0.16), at=at, vol=0.8)
    t.add(thud(90, 0.12), vol=0.6)
    return reverb(soft_clip(t.buf, 1.3), wet=0.15, tail=0.3)


def mix_voices(a, b, b_gain):
    return [x + y * b_gain for x, y in zip(a, b)]


def sfx_win():
    # A little fanfare: bells and marimba climbing to a held chord, with a sparkle on top.
    t = Track(2.0)
    line = [(3, 0.0), (7, 0.11), (10, 0.22), (15, 0.33)]
    for semis, at in line:
        t.add(marimba(semis, 0.4, decay=6), at=at, vol=0.7)
        t.add(bell(semis + 12, 0.4, decay=6, index=1.5), at=at, vol=0.3)
    for semis in (15, 19, 22, 27):
        t.add(bell(semis, 1.4, decay=2.2, index=1.8), at=0.46, vol=0.32)
    t.add(marimba(-9, 1.2, decay=3), at=0.46, vol=0.6)
    for i in range(8):
        t.add(bell(34 + rng.choice([0, 3, 7, 12]), 0.3, decay=8, index=1.0), at=0.5 + i * 0.07, vol=0.12)
    return reverb(t.buf, wet=0.3, tail=1.0)


def sfx_lose():
    # Three soft notes falling away. Gentle; someone else just won, nobody needs rubbing in.
    t = Track(1.3)
    for i, semis in enumerate((3, -2, -5)):
        t.add(marimba(semis, 0.6, decay=4), at=i * 0.24, vol=0.75)
    t.add(marimba(-17, 0.9, decay=2.5), at=0.48, vol=0.35)
    return reverb(t.buf, wet=0.3, tail=0.8)


def sfx_turn():
    # A soft glassy ping: your move.
    t = Track(0.6)
    t.add(bell(19, 0.5, decay=6, index=1.6), vol=0.7)
    t.add(bell(26, 0.45, decay=7, index=1.2), at=0.07, vol=0.45)
    return reverb(t.buf, wet=0.3, tail=0.5)


# --- music -----------------------------------------------------------------

def music_loop():
    """Easygoing 8-bar loop at 112 bpm: electric piano chords, round bass, light drums."""
    bpm = 112
    beat = 60 / bpm
    bars = 8
    total = bars * 4 * beat
    t = Track(total)

    chords = [(0, 4, 7, 11), (-3, 0, 4, 7), (5, 9, 12, 16), (7, 11, 14, 17)]  # Cmaj7, Am7, Fmaj7, G7 around C4
    root = -9  # C4 relative to A4
    melody = [
        [(0, 12, 1), (1.5, 14, 0.5), (2, 16, 1), (3, 19, 1)],
        [(0, 16, 1.5), (2, 12, 1), (3, 9, 1)],
        [(0, 17, 1), (1, 16, 0.5), (1.5, 14, 0.5), (2, 12, 2)],
        [(0, 14, 1.5), (2, 11, 1), (3, 7, 1)],
        [(0, 19, 1), (1, 16, 0.5), (1.5, 19, 0.5), (2, 21, 2)],
        [(0, 19, 1), (1, 16, 1), (2, 12, 2)],
        [(0, 21, 1), (1, 19, 0.5), (1.5, 17, 0.5), (2, 16, 1), (3, 14, 1)],
        [(0, 11, 1), (1, 14, 1), (2, 12, 2)],
    ]

    def keys(semis, seconds, vol):
        # Soft FM electric piano: a gentle bell with a low modulation ratio.
        return bell(semis, seconds, vol=vol, index=1.1, ratio=1.0, decay=2.2)

    def bass(semis, seconds):
        f = note_freq(semis)
        body = mix_voices(sine(f, seconds), osc("saw", f, seconds), 0.25)
        return env_shape(biquad(body, "lowpass", 600), 0.01, seconds * 0.6, seconds * 0.35)

    kick = env_ad(sine(110, 0.25, 42), 0.002, 14)
    snare = Track(0.2).add(env_ad(biquad(noise(0.2), "bandpass", 1900, 0.7), 0.001, 22)).add(env_ad(sine(190, 0.1, 160), 0.001, 40), vol=0.4).buf
    hat = env_ad(biquad(noise(0.05), "highpass", 8000), 0.0005, 90)

    for bar in range(bars):
        chord = chords[bar % 4]
        start = bar * 4 * beat
        # Chords on 1 and the "and" of 2, a little pushed.
        for at in (0.0, 1.5):
            for k, semis in enumerate(chord):
                t.add(keys(semis + root, beat * 2.2, 0.16), at=start + at * beat + k * 0.008)
        # Bass: root, root, fifth, approach note.
        for at, step, length in ((0, 0, 1.4), (1.5, 0, 0.45), (2, 7, 1.0), (3, 12 if bar % 2 else 5, 0.9)):
            t.add(bass(chord[0] + root - 12 + step, length * beat), at=start + at * beat, vol=0.5)
        for at, semis, length in melody[bar]:
            t.add(bell(semis + root + 12, length * beat + 0.3, vol=0.2, index=1.6, ratio=2.0, decay=3.0), at=start + at * beat)
        for b in range(4):
            if b in (0, 2):
                t.add(kick, at=start + b * beat, vol=0.6)
            if b in (1, 3):
                t.add(snare, at=start + b * beat, vol=0.22)
            for half in (0, 0.5):
                t.add(hat, at=start + (b + half) * beat, vol=0.07 if half else 0.1)

    # Seamless loop: run the reverb over two passes and keep the second, so the tail wraps around.
    twice = t.buf + t.buf
    wet = reverb(twice, room=0.7, wet=0.22, tail=0.0)
    n = len(t.buf)
    return soft_clip(wet[n:2 * n], 1.1)


if __name__ == "__main__":
    write("sfx_shuffle", sfx_shuffle(), peak=0.8)
    write("sfx_deal", sfx_deal(), peak=0.55)
    write("sfx_flip", sfx_flip(), peak=0.7)
    write("sfx_land", sfx_land(), peak=0.8)
    write("sfx_draw", sfx_draw(), peak=0.7)
    write("sfx_skip", sfx_skip(), peak=0.75)
    write("sfx_reverse", sfx_reverse(), peak=0.7)
    write("sfx_wild", sfx_wild(), peak=0.8)
    write("sfx_last_card", sfx_last_card(), peak=0.8)
    write("sfx_penalty", sfx_penalty(), peak=0.8)
    write("sfx_win", sfx_win(), peak=0.85)
    write("sfx_lose", sfx_lose(), peak=0.7)
    write("sfx_turn", sfx_turn(), peak=0.6)
    write("music_loop", music_loop(), peak=0.55, trim=False)  # exact length, so it loops on the beat
