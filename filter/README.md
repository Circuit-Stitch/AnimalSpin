# filter/ — sound-clip quality tools

Picking toddler-appropriate animal sounds off Freesound by keyword is unreliable: a search for
"monkey" returns the **Honda Monkey** (a motorbike), "horse" returns a **Theremin Horse**, "lion"
returns game-asset **"Beast Roar"s**, and plenty of human imitations. Keyword regexes can't tell
those apart. These tools put a **local LLM** in the loop to judge each clip's metadata, plus a
permanent blocklist so a rejected sound is never pulled back in.

## Tools

| script | what it does |
|--------|--------------|
| `llm.py` | `judge(animal, title, tags, description) -> {keep, reason}` via Ollama. The shared gate. |
| `screen.py` | Runs `judge` over every audio clip's stored title in `asset-credits.csv`; writes `screen-report.csv` of likely wrong-content clips. Finds bad clips without auditioning each by ear. |
| `fetch.py` | Freesound search → regex prefilter → `judge` (metadata) → download → normalise → `audio_gate` (DSP) → dedupe vs `asset-credits.csv` + `rejected-sounds.csv` (source id + sha256) **and `clap` perceptual dup vs every kept + known-bad clip**. Outputs clips + `manifest.json`. |
| `replace.py` | Blocklist named clips (archiving the audio into `rejects/`) → gated re-fetch → overwrite in place → update `asset-credits.csv`. `--wrong` pulls every `wrong` from `screen-report.csv`. The loop you run as you reject clips by ear. |
| `clap.py` | Perceptual audio footprint via **CLAP** embeddings (`laion/larger_clap_general`, trained on AudioSet → handles animal sounds). `embed`/`sim` power `fetch.py`'s dup gate; `--scan` reports near-duplicate pairs in `res/raw` for you to audition. Replaced a chromaprint attempt that couldn't tell a real dup from unrelated clips on these short clips. |
| `audio_gate.py` | DSP safety net (ffmpeg, no numpy): flags near-silence and near-pure tones. **Deliberately narrow** — calibration showed recorded whistles/instruments/human-imitations overlap real animals (a penguin is flatness 0.012, a cricket 0.002), so catching those would reject real animals. Those stay an ear job. Tonal animals (cicada/cricket/whale/dolphin…) are exempt from the tone test. |

## Requirements

- **Ollama** running (`http://localhost:11434`) with **`gemma4:26b`**. In testing it was the only
  local model that reliably caught wrong-content while keeping genuine recordings; `gemma3:4b` and
  `qwen3.5:9b` both misjudged. Thinking is disabled in `llm.py` (these models otherwise spend their
  whole token budget on hidden reasoning and return nothing).
- **One model at a time.** `gemma4:26b` is ~51 GB across GPU(s); don't co-load another. Swap with
  `ollama stop <other>` first. Change the model in `llm.py:MODEL` if your box is smaller.
- `ffmpeg`/`ffprobe`, Python `requests`. Freesound token in `$FREESOUND_TOKEN` (or `/tmp/fs_key`) — **never commit it**.
- **CLAP venv** for `clap.py` (and therefore `fetch.py`/`replace.py`, which import it): `torch` + `transformers`.
  System Python is 3.14 with no pip and no torch wheels, so it lives in a 3.12 venv:
  `uv venv filter/.venv --python 3.12 && uv pip install --python filter/.venv torch transformers requests`.
  Run those tools via `filter/.venv/bin/python filter/<tool>.py`. CLAP runs on **CPU** (the box's V100s are
  CC 7.0, which the installed cu13 torch dropped); ~0.5 s/clip, embeddings cached in `filter/embeddings.json`.

## Usage

```bash
# clip-quality tools (stdlib + ollama):
python3 filter/screen.py                 # screen all clips -> filter/screen-report.csv
python3 filter/llm.py monkey "Honda Monkey Sound 6"        # one-off judge
# tools needing CLAP -> run via the venv:
filter/.venv/bin/python filter/clap.py --scan              # report near-duplicate pairs to audition
filter/.venv/bin/python filter/clap.py a.mp3 b.mp3         # cosine similarity of two clips
filter/.venv/bin/python filter/fetch.py monkey:9 lion:6 --out /tmp/fetch   # fetch LLM+CLAP-vetted clips
```

## Blocklist + known-bads corpus

`rejected-sounds.csv` (repo root) records every rejected clip: `resource, animal, source, sha256,
date_rejected`. `fetch.py` skips any source id or sha256 listed there. Because intake renames clips
and **reuses slots** (a rejected `dolphin_fs1` is replaced by a different clip also named
`dolphin_fs1`), dedupe by sha256, not by slot name — a slot can earn several rows over time.

The audio itself is kept in **`filter/rejects/`** (content-addressed `<animal>_<sha12>.mp3`), so
`clap.py` can fingerprint known-bads and stop a perceptual re-upload from coming back even under a
new id and new bytes. `replace.py` archives there automatically.

## The layers (and what each can't do)

1. **`judge` (LLM, metadata)** — catches mislabelled content (a Honda-Monkey motorbike, a Theremin
   Horse, a rhino sold as an elephant, a sea-lion sold as a seal). Can't hear audio.
2. **`audio_gate` (DSP)** — catches dead clips and near-pure tones. Can't safely catch recorded
   whistles/instruments/human imitations (they overlap real animals — see its docstring).
3. **`clap` (perceptual embedding)** — catches duplicates that id+sha256 miss: the same recording
   re-encoded, re-normalised, or re-uploaded under a new id (cosine ≥ ~0.95). Surfaces near-dups
   across the library (`--scan`). Can't judge *quality* — a clip can be unique and still bad.
4. **Your ear** — the only thing that catches a real-but-bad recording (weird, scary, a human
   doing a convincing impression, the wrong member of the right family). `replace.py <clip>` after.

**Why CLAP and not an audio LLM.** A chromaprint footprint was too weak here — looped short clips
gave unrelated pairs scores as high as a true dup. Local audio-LLMs don't help either: ollama serves
`gemma3n`/`gemma4` text-only, and via llama.cpp `mtmd` (which *does* support `gemma4a`/`gemma3na`
audio) gemma-4-E4B couldn't even tell a dog from a cat — those models are speech-oriented and
llama.cpp flags their audio "experimental". CLAP is trained on general audio (AudioSet), so it
separates cleanly. If you want it on GPU, install a torch build with sm_70 (cu12x) for the V100s.
