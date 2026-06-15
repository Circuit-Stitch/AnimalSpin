#!/usr/bin/env python3
"""Replace named clips with LLM-vetted Freesound clips: blocklist each target (so its source never
returns), fetch graded replacements (drops only "wrong"-verdict candidates), overwrite the file in
place, and update asset-credits.csv. Keeps filenames, so no Kotlin change.

Usage:
  python3 filter/replace.py monkey_fs1 coyote_fs1 ...   # explicit slots (basename, no .mp3)
  python3 filter/replace.py --wrong [extra_slots...]     # all verdict=wrong in screen-report.csv (+extras)
"""
import csv, os, sys, hashlib, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fetch as F

ROOT = F.ROOT
AC = os.path.join(ROOT, "asset-credits.csv")
REJ = os.path.join(ROOT, "rejected-sounds.csv")
RAW = os.path.join(ROOT, "app/src/main/res/raw")
TODAY = "2026-06-14"
MODS = ("downloaded HQ-mp3 preview; trim+silence-strip, capped 8s, fade-out, "
        "loudnorm -16 LUFS, mono 22.05kHz")


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


targets = [a for a in sys.argv[1:] if not a.startswith("--")]
if "--wrong" in sys.argv:
    rep = os.path.join(ROOT, "filter", "screen-report.csv")
    for r in list(csv.reader(open(rep)))[1:]:
        if r[3] == "wrong":
            targets.append(os.path.basename(r[0]).rsplit(".", 1)[0])
targets = sorted(set(targets))


def path(b):
    return f"app/src/main/res/raw/{b}.mp3"


ac = list(csv.reader(open(AC)))
hdr, rows = ac[0], ac[1:]
row_by_path = {r[0]: r for r in rows}

# 1. blocklist targets (append, dedupe by resource)
rej = list(csv.reader(open(REJ))) if os.path.exists(REJ) else [["resource", "animal", "source", "sha256", "date_rejected"]]
rhdr, rbody = rej[0], rej[1:]
have = {r[0] for r in rbody}
by_animal = {}
for b in targets:
    r = row_by_path.get(path(b))
    if not r:
        print("skip (no asset-credits row):", b)
        continue
    by_animal.setdefault(r[1].lower(), []).append(b)
    if path(b) not in have:
        f = os.path.join(RAW, f"{b}.mp3")
        row = [path(b), r[1], r[2], sha(f) if os.path.exists(f) else "MISSING", TODAY]
        if len(rhdr) > 5:
            row.append("screen/manual")
        rbody.append(row)
csv.writer(open(REJ, "w", newline="")).writerows([rhdr] + rbody)
print(f"blocklisted {len(targets)} clips ({len(by_animal)} animals)")

# 2. fetch replacements (seen/rej rebuilt to include the new blocklist entries)
seen, rej_h = F._ids_and_hashes()
out = "/tmp/animalspin_replace"
os.makedirs(out, exist_ok=True)
filled = unfilled = 0
shortfalls = {}
for animal, slots in sorted(by_animal.items()):
    print(f"== {animal}: replacing {len(slots)} ==")
    picks = F.fetch_animal(animal, len(slots), os.path.join(out, animal), seen, rej_h)
    for slot, pk in zip(slots, picks):
        shutil.copy(os.path.join(out, animal, pk["file"]), os.path.join(RAW, f"{slot}.mp3"))
        r = row_by_path[path(slot)]
        r[2] = f"{pk['url']} (id {pk['id']}: {pk['name']})"
        r[3], r[4], r[5], r[6] = pk["username"], pk["license"], TODAY, MODS
        filled += 1
    if len(picks) < len(slots):
        shortfalls[animal] = slots[len(picks):]
        unfilled += len(slots) - len(picks)
csv.writer(open(AC, "w", newline="")).writerows([hdr] + rows)
print(f"\nfilled {filled}, unfilled {unfilled}")
for a, s in shortfalls.items():
    print(f"  SHORT {a}: {s}")
