#!/usr/bin/env python3
# Search Wikimedia Commons for candidate images, filter by free license + raster,
# download thumbs, emit a manifest. Usage: find.py <prefix> <query> [limit]
import json, os, sys, time, urllib.parse, urllib.request

prefix, query = sys.argv[1], sys.argv[2]
limit = int(sys.argv[3]) if len(sys.argv) > 3 else 14
UA = "AnimalSpin-asset-fetch/1.0 (kyle@circuitstitch.com)"
OK_LIC = ("cc0", "public domain", "cc by", "cc-by")  # CC-BY / CC-BY-SA / CC0 / PD

api = ("https://commons.wikimedia.org/w/api.php?action=query&format=json"
       "&generator=search&gsrnamespace=6&gsrlimit=%d&gsrsearch=%s"
       "&prop=imageinfo&iiprop=url|extmetadata&iiurlwidth=420"
       % (limit, urllib.parse.quote(query)))
req = urllib.request.Request(api, headers={"User-Agent": UA})
data = json.load(urllib.request.urlopen(req, timeout=30))
pages = list(data.get("query", {}).get("pages", {}).values())
pages.sort(key=lambda p: p.get("index", 999))

outdir = f"/tmp/asimg/cand/{prefix}"
os.makedirs(outdir, exist_ok=True)
manifest = []
i = 0
for p in pages:
    title = p.get("title", "")
    if not title.lower().endswith((".jpg", ".jpeg", ".png")):
        continue
    ii = p.get("imageinfo", [{}])[0]
    em = ii.get("extmetadata", {})
    lic = em.get("LicenseShortName", {}).get("value", "")
    if not any(k in lic.lower() for k in OK_LIC):
        continue
    thumb = ii.get("thumburl")
    full = ii.get("url")
    if not thumb:
        continue
    artist = em.get("Artist", {}).get("value", "")
    # crude strip of html tags from artist
    import re
    artist = re.sub("<[^>]+>", "", artist).strip()
    i += 1
    fn = f"{outdir}/{i:02d}.jpg"
    ok = False
    for attempt in range(4):
        try:
            r = urllib.request.Request(thumb, headers={"User-Agent": UA})
            with open(fn, "wb") as f:
                f.write(urllib.request.urlopen(r, timeout=30).read())
            ok = True; break
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(6 * (attempt + 1)); continue
            print("skip dl", title, e, file=sys.stderr); break
        except Exception as e:
            print("skip dl", title, e, file=sys.stderr); break
    if not ok:
        i -= 1; continue
    time.sleep(1.5)  # be polite to the thumb server
    manifest.append({"i": i, "title": title, "license": lic,
                     "artist": artist, "full": full, "file": fn})
    if i >= 9:
        break

with open(f"{outdir}/manifest.json", "w") as f:
    json.dump(manifest, f, indent=1)
for m in manifest:
    print(f'{m["i"]:>2}  {m["license"]:<14} {m["title"]}')
