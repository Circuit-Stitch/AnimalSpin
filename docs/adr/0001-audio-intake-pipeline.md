# 1. Audio intake pipeline: how a clip is processed, annotated, and recorded

- Status: Accepted
- Date: 2026-07-01
- Deciders: Kyle Falconer

## Context

Animal Spin ships ~55 raw audio clips (198 files across 28 animals) that must be
**offline**, **free-to-use** (CC0 / CC-BY / public-domain), **toddler-appropriate**, and
**loudness-consistent** (a toddler should not get whispered at by one clip and blasted by the
next). Sourcing these by keyword off Freesound is unreliable — "monkey" returns a Honda Monkey
motorbike, "horse" a Theremin, "lion" game-asset roars — so intake needs judgement, DSP safety
nets, dedup, and a durable provenance record for the Play Store license audit.

The tooling lives in `filter/` (Python + ffmpeg + a local LLM + CLAP embeddings). This ADR records
**the lifecycle of one clip** as it currently works, so the invariants below aren't rediscovered
the hard way.

## Decision

A clip moves through **discover → gate → fetch → process → validate → land → annotate → register**.
Two intake entry points drive it, both using the same recipe:

- `filter/fetch.py` — bulk fetch into a scratch dir + `manifest.json` (caller wires the results in).
- `filter/replace.py` — overwrite named slots in place *and* update `asset-credits.csv` (the loop you
  run as you reject clips by ear). Keeps filenames, so no Kotlin change.

### The lifecycle of one clip

1. **Discover** — Freesound search per license class (`Creative Commons 0`, then `Attribution`) ×
   per-animal query. (`fetch.py:126-137`)
2. **Gate (metadata, cheap → expensive)** — a `PREJUNK` regex drops obvious junk (honda, theremin,
   chiptune…), then the LLM (`llm.judge`, gemma4 via Ollama) drops only clearly-**wrong** content;
   "real" and "unclear" pass to the ear later. (`fetch.py:139-145`)
3. **Fetch** — download the HQ mp3 *preview* to a temp `_src`. (`fetch.py:146-150`)
4. **Process (ffmpeg, the one place audio is transformed)** — filter chain `AF` (`fetch.py:21-23`):
   silence-strip both ends → cap at 8 s → 0.3 s fade-out → **`loudnorm=I=-16:TP=-1.5:LRA=11`**;
   re-encoded **mono, 22.05 kHz, 96 kbps mp3** (`fetch.py:152-153`). This is the **only**
   normalization a clip ever receives.
5. **Validate (DSP + perceptual nets)** — duration must be 0.5–9 s; sha256 must not be in the reject
   ledger; `audio_gate.is_bad` flags near-silence / near-pure-tone; `clap` (CLAP embedding) rejects
   perceptual duplicates of any kept-or-known-bad clip. (`fetch.py:154-168`)
6. **Land** — survivor is renamed `<animal>_<N>.mp3` into `app/src/main/res/raw/`. (`fetch.py:169-170`)
7. **Annotate (provenance ledger)** — `replace.py` writes/updates the clip's row in
   `asset-credits.csv`: `resource, animal, source (url + id), author, license, date_downloaded,
   modifications`. The `modifications` text records the recipe applied (e.g. *"trim+silence-strip,
   capped 8s, fade-out, loudnorm -16 LUFS, mono 22.05kHz"*). (`replace.py:19-20,76-83`)
8. **Register (in-app)** — a clip is live only once `models/Animals.kt` maps it:
   `AnimalNoise(Animal.X, R.raw.<name>)` (`Animals.kt:54-63`). `replace.py` reuses slot names so an
   overwrite needs **no** code change; a genuinely new animal needs a manual `Animal` enum entry
   (drawable + `tts_<name>_says` string) plus its `AnimalNoise` rows.

### Where a clip's state is recorded

| Record | Role |
|--------|------|
| `asset-credits.csv` | Provenance + license + **processed-state ledger** (`modifications` = what was applied). One row per live clip. |
| `rejected-sounds.csv` | Permanent blocklist: `resource, animal, source, sha256, date_rejected`. `fetch.py` skips any listed source id or sha256. A reused slot earns a row per distinct bad clip. |
| `filter/rejects/` | Content-addressed known-bad audio (`<animal>_<sha12>.mp3`) so CLAP can stop a perceptual re-upload returning under a new id/bytes. |
| `filter/embeddings.json` | CLAP embedding cache keyed by clip, powering the dedup net. |

## Consequences

**Invariants that must hold (violating them is how clips degrade):**

- **No untouched originals are kept locally.** `fetch.py`'s `_src` is a temp file overwritten per
  candidate; only the processed clip lands in `res/raw`. **`res/raw/*.mp3` *is* the master.** A
  pristine original is recoverable only by re-downloading the `source` URL in `asset-credits.csv`.
- **Normalization happens exactly once, at intake.** All 198 clips are already at −16 LUFS (every
  `asset-credits.csv` row says so). **Never run `loudnorm`/EQ against a file already in `res/raw`** —
  that is a second decode→re-encode generation for zero loudness benefit, i.e. slowly turning a
  finished master into mush. The `modifications` column is the ledger that proves a clip is done; a
  backfill pass over `res/raw` would be a no-op at best and destructive at worst.
- **The loudnorm target is duplicated** in code (`fetch.py:AF`) and in the human-readable
  `modifications` text (`replace.py:MODS`). Keep them in sync if the target ever changes.

**Trade-offs accepted:**

- Single-pass `loudnorm` has ±~1 LUFS slop; if audible inconsistency is ever reported, audit it with
  a **read-only** `ffmpeg ebur128` measurement (no re-encode) before considering any reprocessing —
  and reprocess from a re-fetched original, not from the shipped master.
- Because no originals are stored, changing the normalization target (or format) later means
  **re-fetching** affected clips from their `source` URLs, not re-deriving locally. This is the
  deliberate cost of not carrying HQ masters in the repo.
- A live clip's existence is asserted in three places (file on disk, `asset-credits.csv` row,
  `AnimalNoise` row). `replace.py` keeps the first two consistent; the third is manual for new
  animals.

## Planned follow-up: untouched-originals archive

To lift the "no originals kept" invariant, we intend to re-download every clip's source into an
out-of-build master archive (gitignored `filter/originals/` or LFS), so future reprocessing derives
from originals instead of the shipped masters. Feasibility check (2026-07-01): all 198 rows are
re-downloadable — **19 bigsoundbank** sources are direct `.mp3` URLs (= the original), **179
freesound** sources are page URLs + id. Open decision: for Freesound, archive the **HQ preview we
actually imported** (token-only, faithful to what shipped) vs the uploader's **true original**
(full WAV/FLAC via OAuth2 — higher fidelity but *different bytes* than what was processed). Not
started.

## Related

- `filter/README.md` — the tools and what each layer can/can't catch.
- `CLAUDE.md` → *Architecture* — playback side (how a registered clip is chosen and played).
