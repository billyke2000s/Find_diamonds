"""Check versioned indices and rule assumptions against exported game data.

This is configuration validation, not a game-world accuracy measurement.
All network access is confined to this development/CI script.
"""
import json
import pathlib
import subprocess
import tempfile
import urllib.request


def data(version, path):
    url = f"https://raw.githubusercontent.com/misode/mcmeta/{version}-data/data/minecraft/worldgen/{path}.json"
    with urllib.request.urlopen(url, timeout=30) as response:
        return json.load(response)


def main():
    root = pathlib.Path(__file__).resolve().parent.parent
    with tempfile.TemporaryDirectory() as temp:
        sources = list((root / "core/src/main/java").rglob("*.java")) + list((root / "core/src/test/java").rglob("*.java"))
        subprocess.run(["javac", "--release", "8", "-d", temp, *map(str, sources)], check=True)
        output = subprocess.check_output(["java", "-cp", temp, "uk.obskyra.diamonds.core.SeedProfileDump"], text=True)
        for line in output.splitlines():
            version, small, buried, medium = line.split(",")
            ores = data(version, "biome/plains")["features"][6]
            assert ores.index("minecraft:ore_diamond") == int(small), version
            if int(buried) >= 0:
                assert ores.index("minecraft:ore_diamond_buried") == int(buried), version
                placement = data(version, "placed_feature/ore_diamond_buried")["placement"]
                assert placement[0]["count"] == 4
                height = placement[2]["height"]
                assert height["type"] == "minecraft:trapezoid"
                assert height["min_inclusive"] == {"above_bottom": -80}
                assert height["max_inclusive"] == {"above_bottom": 80}
                config = data(version, "configured_feature/ore_diamond_buried")["config"]
                assert config["size"] == 8 and config["discard_chance_on_air_exposure"] == 1
                if medium == "true":
                    assert ores.index("minecraft:ore_diamond_medium") == 19
                    assert ores.index("minecraft:ore_diamond_large") == 20
                else:
                    assert "minecraft:ore_diamond_medium" not in ores
                    assert ores.index("minecraft:ore_diamond_large") == 19
            print(f"PASS: Java {version} seed profile agrees with exported game rule data (not an in-game accuracy test)")


if __name__ == "__main__":
    main()
