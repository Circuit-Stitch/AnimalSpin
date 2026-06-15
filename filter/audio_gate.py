#!/usr/bin/env python3
"""DSP gate: a SAFETY NET for the two audio faults that have a clean DSP signature and don't
overlap real animals — near-silence and near-pure electronic tones. Pure ffmpeg, no numpy.

What it does NOT do, and why (measured via --calibrate): recorded whistles/instruments and human
imitations overlap real animal sounds spectrally — a real penguin is flatness 0.012, a bear 0.028,
a cricket 0.002 (more tone-like than a sine). So a gate aggressive enough to catch a recorded
whistle also rejects real penguins/bears, and tonal animals (cicada, cricket, whale, dolphin) are
legitimately tonal. Those faults stay an ear job. This gate only catches what it can without ever
rejecting a real animal: dead clips and near-pure tones (and skips the tone test for tonal animals).

Signals (ffmpeg): max_db (volumedetect) ; flatness + centroid coeff-of-variation (aspectralstats).

Usage: python3 filter/audio_gate.py clip.mp3 ...                       # verdict per file
       python3 filter/audio_gate.py --calibrate 'app/src/main/res/raw/cat_*.mp3'
"""
import subprocess, re, sys, glob, math, os

# thresholds calibrated so NO real animal clip trips them (lowest real non-tonal: penguin flat 0.012)
SILENCE_DB = -38.0     # peak quieter than this => effectively silent/dead
TONE_FLAT = 0.010      # mean flatness below this => near-pure tone (sine/electronic)
TONE_CV = 0.10         # AND centroid this steady => a held tone, not a pitch-modulating animal
# animals whose genuine sound is tonal/narrowband — never apply the tone test to these
TONAL = {"cicada", "cricket", "whale", "dolphin", "frog", "owl", "penguin", "goose", "seal"}


def features(path):
    vd = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af", "volumedetect", "-f", "null", "-"],
                        capture_output=True, text=True).stderr
    mx = re.search(r"max_volume:\s*(-?[\d.]+)", vd)
    max_db = float(mx.group(1)) if mx else 0.0
    ss = subprocess.run(["ffmpeg", "-hide_banner", "-i", path, "-af",
                         "aspectralstats=measure=flatness+centroid,ametadata=print:file=-", "-f", "null", "-"],
                        capture_output=True, text=True).stdout
    flat = [float(x) for x in re.findall(r"flatness=([\d.eE+-]+)", ss)]
    cen = [float(x) for x in re.findall(r"centroid=([\d.eE+-]+)", ss)]
    mean_flat = sum(flat) / len(flat) if flat else 1.0
    if cen:
        cm = sum(cen) / len(cen)
        cv = (math.sqrt(sum((c - cm) ** 2 for c in cen) / len(cen)) / cm) if cm else 1.0
    else:
        cm, cv = 0.0, 1.0
    return {"max_db": round(max_db, 1), "flatness": round(mean_flat, 4),
            "cen_cv": round(cv, 3), "centroid": round(cm)}


def is_bad(path, animal=None):
    """Return (bad, reason, features). Silence is always bad; the tone test is skipped for animals
    whose real sound is tonal (TONAL)."""
    f = features(path)
    if f["max_db"] < SILENCE_DB:
        return True, f"near-silent ({f['max_db']} dB)", f
    tonal_animal = animal is not None and animal.lower() in TONAL
    if not tonal_animal and f["flatness"] < TONE_FLAT and f["cen_cv"] < TONE_CV:
        return True, f"near-pure tone (flat {f['flatness']}, cv {f['cen_cv']})", f
    return False, "ok", f


if __name__ == "__main__":
    if "--calibrate" in sys.argv:
        files = []
        for g in sys.argv[sys.argv.index("--calibrate") + 1:]:
            files += glob.glob(g)
        for p in sorted(files):
            f = features(p)
            print(f"  {os.path.basename(p):22} max={f['max_db']:6} flat={f['flatness']:.4f} cv={f['cen_cv']:.3f}")
    else:
        for p in sys.argv[1:]:
            animal = os.path.basename(p).split("_")[0]
            bad, reason, f = is_bad(p, animal)
            print(f"{'BAD ' if bad else 'ok  '} {os.path.basename(p):22} {reason}")
