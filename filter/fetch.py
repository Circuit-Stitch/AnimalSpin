#!/usr/bin/env python3
"""Fetch replacement animal clips from Freesound, gated by the local LLM judge.

Pipeline per animal: search Freesound (CC0 then CC-BY) -> cheap regex prefilter -> LLM judge on
title+tags+description -> download HQ preview -> normalise (same ffmpeg recipe as the app's other
clips) -> dedupe against asset-credits.csv AND rejected-sounds.csv (source id + sha256). Keepers
land in <out>/ with a manifest.json the caller can wire in.

Token: env FREESOUND_TOKEN, else /tmp/fs_key. Never commit it.
Usage: python3 filter/fetch.py monkey:9 lion:6 elephant:2 --out /tmp/fetch
"""
import json, os, re, csv, sys, hashlib, subprocess, urllib.request, urllib.parse
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from llm import judge
import audio_gate

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOK = (os.environ.get("FREESOUND_TOKEN") or open("/tmp/fs_key").read()).strip()
AF = ("silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,"
      "silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,"
      "atrim=0:8,afade=t=out:st=7.7:d=0.3,loudnorm=I=-16:TP=-1.5:LRA=11")
# cheap prefilter: skip obvious junk before spending an LLM call (LLM is the real gate)
PREJUNK = re.compile(r"\b(honda|motorcycle|motorbike|theremin|remix|8.?bit|chiptune|midi)\b", re.I)


def lic_name(u):
    if "zero" in u:
        return "CC0"
    m = re.search(r"/by/(\d\.\d)", u)
    return f"CC BY {m.group(1)}" if m else u


def search(q, lic, n=150):
    p = {"query": q, "filter": f'duration:[0.3 TO 12] license:"{lic}"',
         "fields": "id,name,tags,description,license,previews,duration,username,url",
         "page_size": str(n), "sort": "score"}
    req = urllib.request.Request("https://freesound.org/apiv2/search/text/?" + urllib.parse.urlencode(p),
                                 headers={"Authorization": f"Token {TOK}"})
    return json.load(urllib.request.urlopen(req, timeout=30)).get("results", [])


def fetch(u):
    for h in ({"Authorization": f"Token {TOK}"}, {}):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers=h), timeout=40).read()
        except Exception:
            pass


def sha(p):
    return hashlib.sha256(open(p, "rb").read()).hexdigest()


def _ids_and_hashes():
    seen, hashes = set(), set()
    for fn in ("asset-credits.csv", "rejected-sounds.csv"):
        p = os.path.join(ROOT, fn)
        if not os.path.exists(p):
            continue
        for r in list(csv.reader(open(p)))[1:]:
            m = re.search(r"sounds/(\d+)|id (\d+)", r[2])
            if m:
                seen.add(int(m.group(1) or m.group(2)))
            if fn == "rejected-sounds.csv" and len(r) > 3:
                hashes.add(r[3])
    return seen, hashes


# default per-animal queries; override by editing here as needed
QUERIES = {
 "monkey": ["monkey", "chimpanzee", "macaque", "gibbon", "baboon", "howler monkey"],
 "lion": ["lion roar", "lion growl", "lioness", "lion"],
 "elephant": ["elephant trumpet", "elephant call", "elephant"],
 "horse": ["horse neigh", "horse whinny", "horse nicker"],
 "duck": ["duck quack", "mallard quack", "duck quacking"],
 "tiger": ["tiger roar", "tiger growl", "tiger"],
 "dog": ["dog bark", "puppy bark"], "fox": ["fox bark", "red fox call", "fox screech"],
 "frog": ["frog croak", "tree frog", "bullfrog"], "goat": ["goat bleat", "goat baa"],
 "dolphin": ["dolphin", "bottlenose dolphin"], "hyena": ["hyena", "spotted hyena"],
 "coyote": ["coyote howl", "coyote yip", "coyote call"],
 "pig": ["pig oink", "pig grunt", "piglet squeal"],
 "cricket": ["cricket chirp", "field cricket", "cricket night"],
 "cicada": ["cicada", "cicada call", "cicada summer"],
 "whale": ["humpback whale song", "whale call", "whale song"],
 "chipmunk": ["chipmunk chirp", "chipmunk call", "chipmunk squeak"],
 "parrot": ["parrot squawk", "parrot call", "macaw squawk"],
 "eagle": ["eagle screech", "bald eagle call", "eagle cry"],
 "seal": ["harbor seal", "seal bark", "seal pup"],
 "peacock": ["peacock call", "peafowl call", "indian peafowl"],
 "wolf": ["wolf howl", "gray wolf howl", "wolf pack"],
 "squirrel": ["squirrel chatter", "red squirrel", "squirrel scolding"],
 "bear": ["bear growl", "bear roar", "grizzly bear"],
}


def fetch_animal(animal, need, outdir, seen, rej_h, queries=None):
    os.makedirs(outdir, exist_ok=True)
    picks = []
    for lic in ("Creative Commons 0", "Attribution"):
        if len(picks) >= need:
            break
        for q in (queries or QUERIES.get(animal, [animal])):
            if len(picks) >= need:
                break
            for s in search(q, lic):
                if len(picks) >= need:
                    break
                if s["id"] in seen:
                    continue
                seen.add(s["id"])
                nm = s["name"]
                if PREJUNK.search(nm):
                    continue
                # title is a signal, not a default reject: drop only clearly-wrong content,
                # keep "real" and "unclear" (the user auditions the final picks).
                v = judge(animal, nm, ",".join(s.get("tags", [])[:12]), s.get("description", ""))
                if v.get("verdict") == "wrong":
                    continue
                prev = (s.get("previews") or {}).get("preview-hq-mp3")
                data = fetch(prev) if prev else None
                if not data:
                    continue
                open(f"{outdir}/_src", "wb").write(data)
                cand = f"{outdir}/cand.mp3"
                subprocess.run(["ffmpeg", "-y", "-i", f"{outdir}/_src", "-af", AF, "-ac", "1",
                                "-ar", "22050", "-b:a", "96k", cand], capture_output=True)
                d = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration",
                                    "-of", "default=noprint_wrappers=1:nokey=1", cand],
                                   capture_output=True, text=True).stdout.strip()
                df = float(d) if d else 0
                if df < 0.5 or df > 9 or sha(cand) in rej_h:
                    continue
                bad, why, _ = audio_gate.is_bad(cand, animal)   # DSP net: silence / near-pure tone
                if bad:
                    print(f"  dsp-skip {nm[:40]} ({why})")
                    continue
                fn = f"{animal}_{len(picks)+1}"
                os.replace(cand, f"{outdir}/{fn}.mp3")
                picks.append({"file": f"{fn}.mp3", "id": s["id"], "name": nm,
                              "license": lic_name(s["license"]), "username": s["username"], "url": s["url"]})
                print(f"  {v.get('verdict','?'):7} {fn:14} {lic_name(s['license']):8} {nm[:44]}")
    return picks


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if ":" in a]
    out = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else "/tmp/animalspin_fetch"
    seen, rej_h = _ids_and_hashes()
    manifest = {}
    for spec in args:
        animal, n = spec.split(":")
        print(f"== {animal} (need {n}) ==")
        manifest[animal] = fetch_animal(animal, int(n), os.path.join(out, animal), seen, rej_h)
        print(f"   -> {len(manifest[animal])}/{n}")
    json.dump(manifest, open(os.path.join(out, "manifest.json"), "w"), indent=1)
    print(f"\nmanifest -> {out}/manifest.json")
