"""Synthesises GambleCraft's own sounds with numpy and encodes them to Ogg Vorbis with ffmpeg.

  python tools/gen_sounds.py

Writes assets/gamblecraft/sounds/*.ogg and sounds.json (one event per sound, variants where it helps).
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gamblecraft")
RATE = 44100
RNG = np.random.default_rng(7)


def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env(seconds, attack=0.005, decay=0.1):
    t = t_axis(seconds)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / decay)


def tone(freq, seconds, harmonics=(1.0,), attack=0.005, decay=0.2, vibrato=0.0):
    t = t_axis(seconds)
    phase = 2 * np.pi * freq * (t + vibrato * np.sin(2 * np.pi * 5.5 * t) / (2 * np.pi * 5.5))
    wave_ = sum(amp * np.sin(phase * (i + 1)) for i, amp in enumerate(harmonics))
    return wave_ * env(seconds, attack, decay)


def ring(freqs, seconds, decay):
    """Inharmonic partials for metal: chips, coins, a bell."""
    t = t_axis(seconds)
    out = np.zeros_like(t)
    for f, amp in freqs:
        out += amp * np.sin(2 * np.pi * f * t + RNG.uniform(0, 6.28))
    return out * env(seconds, 0.001, decay)


def noise(seconds, decay, low=None, high=None):
    n = RNG.standard_normal(int(seconds * RATE))
    if low or high:
        spectrum = np.fft.rfft(n)
        freqs = np.fft.rfftfreq(len(n), 1 / RATE)
        mask = np.ones_like(freqs)
        if low:
            mask *= freqs >= low
        if high:
            mask *= freqs <= high
        n = np.fft.irfft(spectrum * mask, len(n))
    return n * env(seconds, 0.0005, decay)


def mix(length, *parts):
    """Lays (offset seconds, signal) parts over a buffer of {@code length} seconds."""
    out = np.zeros(int(length * RATE))
    for offset, sig in parts:
        start = int(offset * RATE)
        end = min(len(out), start + len(sig))
        out[start:end] += sig[:end - start]
    return out


def add(*signals):
    """Sums signals of different lengths (all starting together)."""
    out = np.zeros(max(len(x) for x in signals))
    for x in signals:
        out[:len(x)] += x
    return out


def clink(pitch=1.0):
    base = [(2600, 1.0), (3950, 0.7), (6100, 0.45), (8300, 0.25)]
    return add(ring([(f * pitch, a) for f, a in base], 0.25, 0.035), 0.15 * noise(0.01, 0.002, 2000))


def note(n):
    """MIDI note number to Hz."""
    return 440.0 * 2 ** ((n - 69) / 12)


BRASS = (1.0, 0.55, 0.4, 0.25, 0.15, 0.08)
BELL = (1.0, 0.0, 0.25, 0.0, 0.1)


def sounds():
    s = {}
    for i in range(3):
        s[f"chip_clink_{i}"] = mix(0.35, (0, clink(RNG.uniform(0.9, 1.1))), (RNG.uniform(0.04, 0.08), 0.7 * clink(RNG.uniform(0.95, 1.2))))
    for i in range(2):
        thunk = add(tone(170 + 20 * i, 0.18, (1.0, 0.5, 0.2), 0.002, 0.04), 0.4 * noise(0.03, 0.006, 300, 4000))
        s[f"reel_stop_{i}"] = mix(0.25, (0, thunk), (0.01, 0.3 * clink(0.6)))
    s["wheel_click"] = mix(0.08, (0, 0.6 * noise(0.02, 0.003, 1800, 6000)), (0, tone(1500, 0.06, (1.0,), 0.001, 0.008)))
    for i in range(2):
        knocks = [(k * RNG.uniform(0.06, 0.11), add(noise(0.08, 0.02, 200, 1500), tone(RNG.uniform(500, 800), 0.06, (1.0,), 0.001, 0.015)))
                  for k in range(3)]
        s[f"dice_{i}"] = mix(0.45, *knocks)
    s["plinko_peg"] = tone(1568, 0.3, (1.0, 0.0, 0.0, 0.25), 0.001, 0.07)
    # wins: a bright arpeggio; bigger ones in brass with a held chord; the jackpot a full fanfare and coins
    win = [(i * 0.08, tone(note(n), 0.35, BELL, 0.004, 0.18)) for i, n in enumerate((72, 76, 79, 84))]
    s["win"] = mix(0.7, *win)
    big = [(i * 0.07, tone(note(n), 0.3, BRASS, 0.01, 0.2)) for i, n in enumerate((60, 64, 67, 72, 76, 79))]
    chord = [(0.45, 0.6 * tone(note(n), 1.0, BRASS, 0.02, 0.6, vibrato=0.004)) for n in (72, 76, 79, 84)]
    s["big_win"] = mix(1.6, *big, *chord)
    coins = []
    for k in range(48):
        at = 1.6 * (k / 48) ** 1.6
        coins.append((at, (0.9 - 0.5 * k / 48) * clink(RNG.uniform(0.8, 1.35))))
    s["coins"] = mix(1.9, *coins)
    fanfare = [(0.0, tone(note(67), 0.16, BRASS, 0.01, 0.12)), (0.16, tone(note(67), 0.16, BRASS, 0.01, 0.12)),
               (0.32, tone(note(67), 0.16, BRASS, 0.01, 0.12)), (0.48, tone(note(72), 0.5, BRASS, 0.01, 0.4)),
               (0.95, tone(note(70), 0.16, BRASS, 0.01, 0.12)), (1.11, tone(note(72), 0.16, BRASS, 0.01, 0.12)),
               (1.27, tone(note(76), 0.2, BRASS, 0.01, 0.15))]
    final = [(1.5, 0.7 * tone(note(n), 1.6, BRASS, 0.02, 1.0, vibrato=0.005)) for n in (72, 76, 79, 84)]
    shower = [(1.5 + 1.8 * (k / 40) ** 1.3, 0.35 * clink(RNG.uniform(0.9, 1.4))) for k in range(40)]
    s["jackpot"] = mix(3.6, *fanfare, *final, *shower)
    # the alarm: a two-tone siren, one second (the security desk plays it every second)
    t = t_axis(1.0)
    freq = np.where((t * 4).astype(int) % 2 == 0, 960.0, 770.0)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    siren = np.sin(phase) + 0.3 * np.sin(3 * phase) + 0.12 * np.sin(5 * phase)
    s["alarm"] = siren * np.clip(t / 0.02, 0, 1) * np.clip((1.0 - t) / 0.03, 0, 1)
    return s


EVENTS = {
    "chip_clink": ["chip_clink_0", "chip_clink_1", "chip_clink_2"],
    "reel_stop": ["reel_stop_0", "reel_stop_1"],
    "wheel_click": ["wheel_click"],
    "dice": ["dice_0", "dice_1"],
    "plinko_peg": ["plinko_peg"],
    "win": ["win"],
    "big_win": ["big_win"],
    "jackpot": ["jackpot"],
    "coins": ["coins"],
    "alarm": ["alarm"],
}


def write_ogg(name, signal):
    peak = np.max(np.abs(signal)) or 1.0
    pcm = (signal / peak * 0.7 * 32767).astype(np.int16)
    os.makedirs(os.path.join(ROOT, "sounds"), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(RATE)
            w.writeframes(pcm.tobytes())
        out = os.path.join(ROOT, "sounds", name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", out], check=True)


def main():
    for name, signal in sounds().items():
        write_ogg(name, signal)
    sounds_json = {event: {"sounds": [f"gamblecraft:{f}" for f in files], "subtitle": f"subtitles.gamblecraft.{event}"}
                   for event, files in EVENTS.items()}
    with open(os.path.join(ROOT, "sounds.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(sounds_json, f, indent=2)
        f.write("\n")
    print(f"{sum(len(v) for v in EVENTS.values())} sounds written")


if __name__ == "__main__":
    main()
