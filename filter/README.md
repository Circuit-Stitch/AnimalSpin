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
| `fetch.py` | Freesound search → regex prefilter → `judge` (metadata) → download → normalise → `audio_gate` (DSP) → dedupe vs `asset-credits.csv` + `rejected-sounds.csv`. Outputs clips + `manifest.json`. |
| `replace.py` | Blocklist named clips → gated re-fetch → overwrite in place → update `asset-credits.csv`. `--wrong` pulls every `wrong` from `screen-report.csv`. The loop you run as you reject clips by ear. |
| `audio_gate.py` | DSP safety net (ffmpeg, no numpy): flags near-silence and near-pure tones. **Deliberately narrow** — calibration showed recorded whistles/instruments/human-imitations overlap real animals (a penguin is flatness 0.012, a cricket 0.002), so catching those would reject real animals. Those stay an ear job. Tonal animals (cicada/cricket/whale/dolphin…) are exempt from the tone test. |

## Requirements

- **Ollama** running (`http://localhost:11434`) with **`gemma4:26b`**. In testing it was the only
  local model that reliably caught wrong-content while keeping genuine recordings; `gemma3:4b` and
  `qwen3.5:9b` both misjudged. Thinking is disabled in `llm.py` (these models otherwise spend their
  whole token budget on hidden reasoning and return nothing).
- **One model at a time.** `gemma4:26b` is ~51 GB across GPU(s); don't co-load another. Swap with
  `ollama stop <other>` first. Change the model in `llm.py:MODEL` if your box is smaller.
- `ffmpeg`/`ffprobe`, Python `requests`. Freesound token in `$FREESOUND_TOKEN` (or `/tmp/fs_key`) — **never commit it**.

## Usage

```bash
python3 filter/screen.py                 # screen all clips -> filter/screen-report.csv
python3 filter/screen.py monkey lion     # just these animals
python3 filter/llm.py monkey "Honda Monkey Sound 6"        # one-off judge
python3 filter/fetch.py monkey:9 lion:6 --out /tmp/fetch   # fetch LLM-approved replacements
```

## Blocklist

`rejected-sounds.csv` (repo root) records every rejected clip: `resource, animal, source, sha256,
date_rejected, reason`. `fetch.py` skips any source id or file hash listed there, so rejects never
come back. Add to it when you reject a clip (by ear or via `screen.py`).

## The three layers (and what each can't do)

1. **`judge` (LLM, metadata)** — catches mislabelled content (a Honda-Monkey motorbike, a Theremin
   Horse, a rhino sold as an elephant, a sea-lion sold as a seal). Can't hear audio.
2. **`audio_gate` (DSP)** — catches dead clips and near-pure tones. Can't safely catch recorded
   whistles/instruments/human imitations (they overlap real animals — see its docstring).
3. **Your ear** — the only thing that catches a real-but-bad recording (weird, scary, a human
   doing a convincing impression, the wrong member of the right family). `replace.py <clip>` after.

No local audio model was available: `gemma3n` is pulled but this ollama build is text-only
(`ollama show` reports `completion`, and it ignores audio input), so audio-level ID isn't an option
here. If an audio-capable runtime appears, a `judge_audio()` would slot in as layer 2.5.
