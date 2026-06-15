#!/usr/bin/env python3
"""Local-LLM judge: read a clip's metadata and classify it as a signal toward whether it is a
REAL recording of the target animal. The title is a SIGNAL, not a default rejection — only clearly
wrong content (a vehicle, instrument, game/monster SFX, toy, music, human imitation, or a different
animal) is "wrong"; a plain/ambiguous title (e.g. "lion.wav") is "unclear", not rejected.

Uses Ollama (http://localhost:11434). Default model gemma4:26b with thinking disabled — in testing
the only local model that reliably caught wrong-content while keeping genuine recordings. Run ONE
model at a time (see README). Note: this judges METADATA, not audio — it can't hear a genuine-but-bad
recording. (gemma3n in this ollama build is text-only; no audio path.)
"""
import json, re, requests

OLLAMA = "http://localhost:11434/api/generate"
MODEL = "gemma4:26b"


def _json(text):
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


def judge(animal, title, tags="", description="", model=MODEL, timeout=120):
    """Return {'verdict': 'wrong'|'real'|'unclear', 'reason': str}.

    wrong   = title/tags clearly indicate it is NOT a real {animal} (vehicle, instrument, game/
              monster SFX, toy, music, human imitation, or a different animal).
    real    = clearly a real {animal} recording.
    unclear = generic/ambiguous title; could be real, can't tell from metadata alone (NOT a reject).
    """
    p = (f'You screen sound clips for a toddler animal app, looking for authentic recordings of a '
         f'real {animal}. Based ONLY on the metadata, classify this clip:\n'
         f'- "wrong": clearly NOT a real {animal} — a vehicle, machine, musical instrument, game/'
         f'monster/fantasy SFX, a toy, music, a human imitation, or a DIFFERENT animal.\n'
         f'- "real": clearly a genuine {animal} recording.\n'
         f'- "unclear": generic or ambiguous title that could be a real {animal} — do NOT mark wrong '
         f'just because the title is plain or post-processed.\n'
         f'Title: "{title}"\nTags: {tags}\nDescription: {description[:300]}\n'
         f'Reply ONLY JSON: {{"verdict": "wrong" or "real" or "unclear", "reason": "<=8 words"}}')
    r = requests.post(OLLAMA, json={"model": model, "stream": False, "think": False,
                                    "options": {"temperature": 0, "num_predict": 80},
                                    "prompt": p}, timeout=timeout).json()
    out = _json(r.get("response", ""))
    if not out or out.get("verdict") not in ("wrong", "real", "unclear"):
        return {"verdict": "unclear", "reason": "parse-fail: " + r.get("response", "")[:50]}
    return out


if __name__ == "__main__":
    import sys
    a, t = sys.argv[1], sys.argv[2]
    print(judge(a, t, tags=sys.argv[3] if len(sys.argv) > 3 else ""))
