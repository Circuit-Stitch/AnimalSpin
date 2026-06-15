#!/usr/bin/env python3
"""Perceptual dedup for animal clips: a two-signal hybrid, because neither signal alone works.

  CLAP embedding (laion/larger_clap_general, AudioSet-trained) -> cosine = "same KIND of sound".
    Good recall, poor precision: it rates two different crow caws (8 vs 4 caws) at 0.97 because both
    are "crow". So cosine alone floods with same-animal false positives.
  Envelope cross-correlation -> "same RECORDING": does the amplitude shape over time line up (after a
    small lag search)? This is what your eye does in Audacity. Good precision: crow_fs3 vs crow_fs5
    drops to 0.31. But on its own it false-matches by coincidence across animals (a dog yip vs a fox
    yip with the same envelope).

So: CLAP proposes same-kind candidates, envelope confirms same-recording. A pair is a duplicate only
if BOTH agree.  Calibrated (your audited pairs): true dups cos>=0.95 & env 0.87-1.0; same-animal-but-
distinct land high on cos yet <0.7 on env; re-encodes/re-uploads score ~0.99 on both.

Needs torch + transformers -> run via the filter venv:  filter/.venv/bin/python filter/clap.py ...

  feats(path)        -> (emb, env)   CLAP embedding (512-d, L2-norm) + RMS amplitude envelope
  sim(a, b)          -> float        cosine of two embeddings
  ncc(a, b)          -> float        best-lag normalised cross-correlation of two envelopes
  is_dup(fa, fb)     -> bool         cosine>=COS AND envelope-ncc>=ENV
  feats_cached(p, c) -> feats(), memoised by file sha256 (load_cache/save_cache persist it)

CLI:
  clap.py a.mp3 b.mp3          # print cosine + envelope-ncc for two files
  clap.py --scan [dir]         # CLAP-recall (>=SCAN_COS) then envelope-confirm; ranked for auditioning
"""
import subprocess, os, sys, glob, json, hashlib

MODEL_ID = "laion/larger_clap_general"
COS = 0.95       # gate: cosine at/above this AND...
ENV = 0.90       # ...envelope-ncc at/above this = duplicate (conservative; misses fuzzy near-dups)
SCAN_COS = 0.90  # --scan recall floor; the human auditions everything above it, ranked by env-ncc
CLAP_SR = 48000  # CLAP's expected sample rate
ENV_SR = 16000   # envelope decode rate
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "embeddings.json")
_model = _proc = None


def _load():
    # CPU only: the installed torch (cu13) dropped sm_70, so the box's V100s aren't usable. CLAP is
    # small and these clips are short, so CPU embedding is fast enough for a batch tool.
    global _model, _proc
    if _model is None:
        from transformers import ClapAudioModelWithProjection, ClapProcessor
        _model = ClapAudioModelWithProjection.from_pretrained(MODEL_ID).eval()
        _proc = ClapProcessor.from_pretrained(MODEL_ID)
    return _model, _proc


def _decode(path, sr):
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", path,
                          "-ac", "1", "-ar", str(sr), "-f", "f32le", "-"],
                         capture_output=True).stdout
    import numpy as np
    return np.frombuffer(raw, dtype=np.float32)


def _embed(audio):
    import torch
    model, proc = _load()
    inputs = proc(audio=audio, sampling_rate=CLAP_SR, return_tensors="pt")
    with torch.no_grad():
        v = model(**inputs).audio_embeds[0]
    return (v / v.norm()).cpu().tolist()


def _envelope(audio, win=320, hop=160):
    import numpy as np
    n = max(1, 1 + (len(audio) - win) // hop)
    return [float(np.sqrt(np.mean(audio[i * hop:i * hop + win] ** 2) + 1e-9)) for i in range(n)]


def feats(path):
    a48 = _decode(path, CLAP_SR)
    if a48.size == 0:
        return ([], [])
    return (_embed(a48), _envelope(_decode(path, ENV_SR)))


def sim(a, b):
    if not a or not b:
        return 0.0
    return float(sum(x * y for x, y in zip(a, b)))   # both L2-normalised -> dot == cosine


def ncc(a, b, fr=0.6):
    """Best-lag normalised cross-correlation of two 1-D envelopes (mean-removed)."""
    import numpy as np
    if not a or not b:
        return 0.0
    a = np.array(a) - np.mean(a)
    b = np.array(b) - np.mean(b)
    na, nb, need, best = len(a), len(b), int(fr * min(len(a), len(b))), -1.0
    for lag in range(-(nb - 1), na):
        lo, hi = max(0, lag), min(na, nb + lag)
        if hi - lo < need:
            continue
        x, y = a[lo:hi], b[lo - lag:hi - lag]
        d = np.linalg.norm(x) * np.linalg.norm(y)
        if d > 0:
            best = max(best, float((x * y).sum() / d))
    return best


def is_dup(fa, fb):
    return sim(fa[0], fb[0]) >= COS and ncc(fa[1], fb[1]) >= ENV


def load_cache():
    return json.load(open(CACHE)) if os.path.exists(CACHE) else {}


def save_cache(c):
    json.dump(c, open(CACHE, "w"))


def feats_cached(path, cache):
    h = hashlib.sha256(open(path, "rb").read()).hexdigest()
    v = cache.get(h)
    if v is None:
        v = cache[h] = list(feats(path))
    return v


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if "--scan" in sys.argv:
        import numpy as np
        d = args[0] if args else os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/res/raw")
        files = sorted(glob.glob(os.path.join(d, "*.mp3")))
        cache = load_cache()
        F = []
        for i, f in enumerate(files, 1):
            F.append(feats_cached(f, cache))
            print(f"\r  featurising {i}/{len(files)}", end="", file=sys.stderr)
        save_cache(cache)
        print(file=sys.stderr)
        M = np.array([f[0] for f in F])
        S = M @ M.T                                          # cosine matrix (CLAP recall)
        rows = []
        for i in range(len(files)):
            for j in range(i + 1, len(files)):
                if S[i, j] >= SCAN_COS:                      # envelope-confirm only the candidates
                    e = ncc(F[i][1], F[j][1])
                    rows.append((e, float(S[i, j]), os.path.basename(files[i]), os.path.basename(files[j])))
        rows.sort(reverse=True)
        dup = [r for r in rows if r[0] >= ENV]
        print(f"### DUPLICATES (cos>={SCAN_COS} & env>={ENV}): {len(dup)} ###")
        for e, c, x, y in dup:
            print(f"  env={e:.3f} cos={c:.3f}  {x:18} {y}")
        rev = [r for r in rows if 0.70 <= r[0] < ENV]
        print(f"\n### REVIEW (env 0.70-{ENV}): {len(rev)} ###")
        for e, c, x, y in rev:
            print(f"  env={e:.3f} cos={c:.3f}  {x:18} {y}")
    elif len(args) == 2:
        fa, fb = feats(args[0]), feats(args[1])
        print(f"cosine={sim(fa[0], fb[0]):.3f}  env-ncc={ncc(fa[1], fb[1]):.3f}  dup={is_dup(fa, fb)}")
    else:
        print(__doc__)
        sys.exit(1)
