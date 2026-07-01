#!/usr/bin/env python3
"""Fan a <locale>-tagged "What's new" file out into the fastlane changelog tree.

Usage:
    tools/release-notes.py <tagged-file> [versionCode]

The tagged file holds one block per Play Store locale:

    <en-US>
    - line one
    - line two
    </en-US>
    <fr-FR>
    ...
    </fr-FR>

versionCode defaults to the value in app/build.gradle. Each block is written to
fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt and checked
against Play's 500-character "What's new" limit; the script exits non-zero if
any locale is over (nothing half-written is left behind — it writes only after
all blocks pass).

ponytail: no deps, no translation — Claude writes the tagged file, this splits it.
"""
import re
import sys
import pathlib

LIMIT = 500
ROOT = pathlib.Path(__file__).resolve().parent.parent


def version_code():
    m = re.search(r'versionCode\s+(\d+)', (ROOT / 'app/build.gradle').read_text())
    if not m:
        sys.exit('could not find versionCode in app/build.gradle')
    return m.group(1)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    src = pathlib.Path(sys.argv[1]).read_text()
    code = sys.argv[2] if len(sys.argv) > 2 else version_code()

    blocks = re.findall(r'<([\w-]+)>\n(.*?)\n</\1>', src, re.S)
    if not blocks:
        sys.exit('no <locale>...</locale> blocks found')

    over = []
    for loc, body in blocks:
        n = len(body.strip())
        print(f'{loc:8} {n:4} {"OVER" if n > LIMIT else "ok"}')
        if n > LIMIT:
            over.append(f'{loc} ({n})')
    if over:
        sys.exit(f'\n{len(over)} locale(s) over {LIMIT} chars, nothing written: {", ".join(over)}')

    for loc, body in blocks:
        out = ROOT / 'fastlane/metadata/android' / loc / 'changelogs' / f'{code}.txt'
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(body.strip() + '\n')
    print(f'\nwrote {len(blocks)} changelogs for versionCode {code}')


if __name__ == '__main__':
    main()
