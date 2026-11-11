"""Compare Java scanner output to nbtlib over pinned upstream saved-world fixtures.

Fixtures are downloaded only for development/CI, never by the Android app.
Git blob hashes protect the pinned fixture content. No fixture is redistributed.
"""
import gzip
import hashlib
import io
import pathlib
import struct
import subprocess
import tempfile
import urllib.request
import zlib

import nbtlib

FIXTURES = [
    ("1.8.9", -1, 0, "acc1aa17edefc41320860905bdb1063e8d750ca5"),
    ("1.13.2", -1, -1, "8661c4b465e567d83fdd12538bc3748c7c2f525e"),
    ("1.18.2", 0, 0, "582dd52aeaeca7fc105d30a1f0a471286be23066"),
    ("1.21.1", 0, 0, "bb1d59d4ba6e04ba92c2b9a0907541f7cba1e6bb"),
]


def decode(data, rx, rz):
    found = set()
    for slot in range(1024):
        location = int.from_bytes(data[slot * 4:slot * 4 + 4], "big")
        if not location:
            continue
        offset = (location >> 8) * 4096
        size = int.from_bytes(data[offset:offset + 4], "big")
        codec = data[offset + 4]
        blob = data[offset + 5:offset + 4 + size]
        raw = {1: gzip.decompress, 2: zlib.decompress, 3: lambda b: b}[codec](blob)
        root = nbtlib.File.parse(io.BytesIO(raw))
        chunk = root.get("Level", root)
        cx, cz = rx * 32 + slot % 32, rz * 32 + slot // 32
        for section in chunk.get("sections", chunk.get("Sections", [])):
            sy = int(section["Y"])
            if "Blocks" in section:
                ids = section["Blocks"]
                extra = section.get("Add")
                indices = [i for i, item in enumerate(ids) if (int(item) & 255) == 56 and (extra is None or ((int(extra[i // 2]) >> ((i % 2) * 4)) & 15) == 0)]
            else:
                states = section.get("block_states", section)
                palette = states.get("palette", states.get("Palette", []))
                hits = {i for i, entry in enumerate(palette) if str(entry["Name"]) in {"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"}}
                if not hits:
                    continue
                packed = states.get("data", states.get("BlockStates"))
                if packed is None:
                    assert len(palette) == 1
                    indices = list(range(4096)) if 0 in hits else []
                else:
                    width = max(4, (len(palette) - 1).bit_length())
                    longs = [int(n) & ((1 << 64) - 1) for n in packed]
                    if int(root.get("DataVersion", 0)) >= 2529 or "block_states" in section:
                        per = 64 // width
                        values = ((longs[i // per] >> (i % per * width)) & ((1 << width) - 1) for i in range(4096))
                    else:
                        # Convert the entire contiguous bit stream to an integer, an
                        # independent approach to the Java reader's word slicing.
                        stream = int.from_bytes(b"".join(struct.pack("<Q", n) for n in longs), "little")
                        values = ((stream >> (i * width)) & ((1 << width) - 1) for i in range(4096))
                    indices = [i for i, entry in enumerate(values) if entry in hits]
            for i in indices:
                found.add((cx * 16 + i % 16, sy * 16 + i // 256, cz * 16 + i // 16 % 16))
    return found


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    with tempfile.TemporaryDirectory() as temp:
        classes = pathlib.Path(temp) / "classes"
        classes.mkdir()
        sources = list((root / "core/src/main/java").rglob("*.java")) + list((root / "core/src/test/java").rglob("*.java"))
        subprocess.run(["javac", "--release", "8", "-d", str(classes), *map(str, sources)], check=True)
        for version, rx, rz, expected_hash in FIXTURES:
            name = f"r.{rx}.{rz}.mca"
            url = f"https://raw.githubusercontent.com/PrismarineJS/prismarine-provider-anvil/master/test/fixtures/{version}/{name}"
            request = urllib.request.Request(url, headers={"User-Agent": "FindDiamonds-fixture-verification"})
            with urllib.request.urlopen(request, timeout=60) as response:
                data = response.read(8 * 1024 * 1024)
            digest = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
            assert digest == expected_hash, f"Upstream {version} fixture changed; review it before updating its pinned hash"
            all_blocks = decode(data, rx, rz)
            assert all_blocks, f"{version} fixture contains no diamonds"
            centre = sorted(all_blocks)[len(all_blocks) // 2]
            x, _, z = centre
            expected = {f"{a},{b},{c}" for a, b, c in all_blocks if abs(a-x) <= 32 and abs(c-z) <= 32}
            path = pathlib.Path(temp) / name
            path.write_bytes(data)
            output = subprocess.check_output(["java", "-cp", str(classes), "uk.obskyra.diamonds.core.FixtureScan", str(path), str(rx), str(rz), str(x), str(z)], text=True)
            actual = set(output.splitlines())
            assert actual == expected, f"{version}: missing {len(expected-actual)}, extra {len(actual-expected)}"
            print(f"PASS: Java {version}: {len(actual)} exact saved diamond blocks agree with nbtlib")


if __name__ == "__main__":
    main()
