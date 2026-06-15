#!/usr/bin/env python3
"""Screen every audio clip in asset-credits.csv: ask the local LLM to grade its stored source
title as a SIGNAL toward whether it's a real recording of that animal. The title is a signal,
not a verdict — "wrong" = clearly wrong content (replace candidates); "unclear" = plain/ambiguous
title worth an ear, not an auto-reject; "real" = looks fine. Saves auditioning everything by ear.

Offline except for the local Ollama call. Writes filter/screen-report.csv (verdict + reason).
Usage:  python3 filter/screen.py            # all audio clips
        python3 filter/screen.py monkey lion   # only these animals
"""
import csv, re, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from llm import judge

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
AC = os.path.join(ROOT, "asset-credits.csv")
only = {a.lower() for a in sys.argv[1:]}

rows = list(csv.reader(open(AC)))[1:]
audio = [r for r in rows if r[0].endswith((".mp3", ".wav", ".ogg"))]
out, n = [], 0
for r in audio:
    path, animal, source = r[0], r[1], r[2]
    if only and animal.lower() not in only:
        continue
    m = re.search(r"id [^:]+: ([^)\"]+)", source)
    title = m.group(1).strip() if m else ""
    if not title or not animal:
        continue
    n += 1
    v = judge(animal, title)
    verdict = v.get("verdict", "unclear")
    if verdict != "real":
        out.append((path, animal, title, verdict, v.get("reason", "")))
        tag = "WRONG  " if verdict == "wrong" else "unclear"
        print(f"{tag} {os.path.basename(path):24} [{animal:9}] {title[:36]:36} -> {v.get('reason')}")

with open(os.path.join(ROOT, "filter", "screen-report.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["resource", "animal", "title", "verdict", "reason"])
    w.writerows(sorted(out, key=lambda x: (x[3] != "wrong", x[1])))
wrong = sum(1 for x in out if x[3] == "wrong")
print(f"\n{wrong} WRONG (replace), {len(out)-wrong} unclear (audition) of {n} checked -> filter/screen-report.csv")
