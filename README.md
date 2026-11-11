# Find Diamonds

A free, offline Android Minecraft diamond finder. **The home screen now uses the requested seed + edition/version + X/Y/Z workflow. No world import is needed for seed searches.**

The requested goal is both Java and Bedrock with broad version support, no paywall and no server. It is not finished: seed search currently calculates experimental **Java diamond candidates** for seven exact releases. Bedrock seed prediction is unavailable, and universal Minecraft compatibility is not claimed. The existing world scanner is now an optional separate screen.

## Seed searches

Enter the numeric world seed, select Java and the generation version, enter your X/Y/Z and tap **Find diamonds**. The calculation runs entirely on the phone. Seeds retain all 64 bits; numeric overflow is rejected rather than silently converted to an unrelated text-seed hash.

Candidate profiles: Java **1.16.5, 1.17.1, 1.18.2, 1.19.2, 1.20.1, 1.20.2 and 1.21.1**. Each is an exact version identifier, not an alias for other releases. Only Overworld diamonds are implemented in seed mode.

This is not a full world generator. It reproduces decoration/feature seeding, the game's bit-oriented legacy/xoroshiro wrapper, placement heights and vein geometry. It deliberately emits only the first small/medium/large attempt and the buried batch; later air-discarding attempts can consume a terrain-dependent random stream, so pretending those are exact would be misleading.

Caves, terrain replacement, air exposure, bedrock, structures and already-mined blocks can remove candidates. Not every diamond is listed. Block counts are potential geometry, not confirmed ore. No real-game seed-prediction accuracy percentage has been established. Selecting Bedrock explicitly explains its separate engine is unavailable and does not display Java results as Bedrock results.

## Optional world scanner

- Import a Java world ZIP, an individual `r.x.z.mca` Anvil region, or a Bedrock `.mcworld` export.
- Read actual saved ore blocks locally. There is no remote ore API.
- Find diamonds, ancient debris, iron, gold, redstone, lapis, emerald, copper, coal and quartz where present in the export.
- Choose a dimension, player coordinates and search radius. Ore groups are sorted by 3D distance, with exact saved block coordinates and a top-down map.
- Copy coordinates, keep multiple imported worlds locally, and mark targets as mined. Tracking is scoped by imported-world content, dimension and ore type. It never edits your Minecraft world.
- Cancel background imports/searches. Unknown formats or corrupt data produce an error rather than fabricated coordinates or silently partial results.
- No account, advertising, billing, analytics, network permission or server. The app itself has no third-party runtime dependencies.

## Compatibility

Compatibility is based on **saved-world format**, not a dropdown claiming every game version.

| Mode / format | Implementation | Verification |
| --- | --- | --- |
| Java Anvil numeric sections | Legacy block IDs, including Add nibbles | Constructed fixtures; real saved-world comparison in CI |
| Java Anvil palettes | Contiguous 1.13-style and padded 1.16+ long arrays; modern negative-Y sections | Constructed fixtures; CI compares 1.8.9, 1.13.2, 1.18.2 and 1.21.1 saved-world fixtures to nbtlib |
| Bedrock LevelDB export | CURRENT/MANIFEST live-file selection, SST tables, WAL replay, sequence ordering and tombstones; none/Snappy/zlib/raw-zlib table compression | Constructed database integration fixtures only; real Bedrock-world validation is still required |
| Bedrock terrain | Legacy numeric terrain/subchunks and persistent palette subchunks 1, 8 and 9 | Constructed coordinate fixtures |
| Java seed-only search | Experimental diamond candidates for the seven exact releases above | RNG reference vectors, JDK legacy RNG comparison and exported rule metadata; game-world prediction accuracy still unmeasured |
| Bedrock seed-only search | Not implemented | No supported versions yet |
| Legacy Console, Java pre-Anvil `.mcr`, LZ4/external `.mcc`, unknown future formats | Unsupported | Explicit failure |

The CI fixture checks validate reading existing blocks, **not ore prediction accuracy**. This repository does not claim all versions work. Experimental versions, snapshots, mods and unusual exports require additional tests.

## Using the optional world scanner

1. Close Minecraft and export or ZIP a copy of one world. A Bedrock export contains a `db` folder with `CURRENT` and its referenced database files. A Java export includes the world's `region` folder (and dimension folders if needed).
2. Import that file using Android's document picker. The app stores a private copy; it does not request broad storage access or modify your original.
3. Select the ore and dimension, enter X/Y/Z, then search.
4. Reimport a fresh export after exploring or mining. Unexplored chunks are absent from an export and cannot be scanned.

Groups contain face-connected ore blocks. Groups and counts can be clipped at the search boundary. “Mark mined” records a local hidden-target list, not a change to Minecraft. Long-press a saved-world entry to remove its imported copy.

Imports have a 1 GiB per-file budget. Region, NBT, database and result budgets bound resource use on a phone; these are resource safeguards, not paid search limits.

## Build and checks

Use JDK 17, Gradle 8.9 and Android SDK 35:

```sh
gradle :core:verifyCore :app:assembleDebug
bash scripts/test-core.sh
```

The core module is plain Java 8-compatible code. Android minimum: 8.0 / API 26.

GitHub Actions builds a debug-signed preview APK, checks the merged manifest for network/billing permissions, runs the core integration suite, and compares four upstream Java world fixtures with an independent `nbtlib` decoder. The APK is uploaded only when these jobs pass. Debug builds from different runners may have different signing keys; installing one over another may require uninstalling the earlier preview.

Local verification: 185 core checks passed, including independent unsigned-64 RNG reference vectors, JDK legacy RNG comparisons, version rejection, negative coordinates, cross-chunk/radius consistency and seed cancellation. `scripts/verify-seed-profiles.py` separately checks versioned feature indices and buried-diamond assumptions against exported game data; that is configuration validation, not a game-world accuracy test. The four real Java save-fixture comparisons still validate the optional scanner only. No Android emulator/device validation has been performed at this stage.

## Remaining work for the requested app

1. Validate the experimental Java candidate profiles against matching-version game-generated worlds, then add terrain/cave replacement, air exposure and broader ore support. Implement the separate Bedrock seed-generation engine.
2. Add measured, reproducible seed-to-world fixtures and accuracy reports before advertising reliable or exact prediction. Unsupported versions must remain unavailable rather than returning another version's results.
3. Validate Bedrock scanning against real exports from old and recent releases.
4. Add missing save formats, accessibility/device checks, a persistent release signing process and installable release delivery.

Minecraft's generation changed over time; chunks first explored on an older version keep their stored blocks. Match that original generation version for seed searches. Optional offline scanning reads stored blocks directly but requires the world file.

## Research and source notes

- [Find Diamonds Android listing](https://play.google.com/store/apps/details?id=com.rushedbytes.diamondsfinder): seed/coordinate interface and advertised targets; does not establish internal computation location.
- [Meteor Rejects OreSim](https://github.com/AntiCope/meteor-rejects/blob/master/src/main/java/anticope/rejects/modules/OreSim.java): reference for the scope of a Java seed simulator; not included as an app dependency.
- [Exported Minecraft game data](https://github.com/misode/mcmeta): exact-release biome feature order, diamond placement/configuration and height rules.
- [WorldgenRandom](https://github.com/mahtomedi/minecraft/blob/main/src/main/java/net/minecraft/world/level/levelgen/WorldgenRandom.java) and [BitRandomSource](https://github.com/mahtomedi/minecraft/blob/main/src/main/java/net/minecraft/world/level/levelgen/BitRandomSource.java): references for the wrapper's draw semantics. Direct xoroshiro bounded integers are not interchangeable with the game's bit-oriented worldgen wrapper.
- [Prismarine chunk persistent palette reader](https://github.com/PrismarineJS/prismarine-chunk/blob/master/src/bedrock/1.3/SubChunk.js): reference for disk-palette format, distinct from network runtime IDs.
- [Prismarine Anvil fixture worlds](https://github.com/PrismarineJS/prismarine-provider-anvil/tree/master/test/fixtures): downloaded during CI only, with Git blob hashes checked before decoding. They are not bundled or redistributed.

Original app/core code is MIT licensed. Minecraft names, game data and third-party projects remain the property of their respective owners. This unofficial app is not affiliated with Mojang or Microsoft.
