# Project instructions

## Product requirements

Build a free Android Minecraft ore finder. All runtime calculations and persistence must work offline. Do not add a backend, API key, account, ads, subscriptions, billing, analytics, or artificial search limits. Avoid requesting INTERNET permission.

## Correctness

- Implement actual version-specific Minecraft generation, not random coordinates, a hash-based approximation, or UI fixtures presented as results.
- Treat Java and Bedrock as separate engines. Reject unsupported editions and versions explicitly.
- Store numeric seeds losslessly as signed 64-bit values. Distinguish numeric overflow from intentional text seeds.
- Use floor division for negative chunk coordinates.
- Keep candidate ore generation distinct from verified block locations. Never claim exactness or a measured accuracy percentage without supporting real-world fixtures.
- Consider biome-dependent feature indices, RNG consumption during placement, terrain replacement, air-exposure rules, and cross-chunk features.
- Existing chunks can have been generated with an older version. Explain generation-version selection.
- Do not copy proprietary app code or assets. Check licenses before incorporating third-party code and retain required notices.

## Architecture

- Keep the generation engine free of Android dependencies so it can be tested on a normal JVM.
- Separate UI, search orchestration, generation, and persistence into focused files.
- Run searches away from the main thread; support cancellation and bounded resource usage.
- Scope saved mined targets by world identity, edition, version, dimension, and ore type.
- Use Android's document picker for any future world imports. Do not request broad storage access.

## Verification and reporting

- Test the RNG against independent reference vectors and ore predictions against actual matching-version game-generated world fixtures.
- Include negative coordinates, chunk edges, extreme valid seeds, invalid numeric seeds, edition/version rejection, and cancellation.
- Test the Android build and check the merged manifest for unwanted permissions.
- Record the exact supported edition/version matrix, completed checks, and remaining limitations in the README.
- Do not describe scaffolding, unbuilt source, or mocked results as a finished or tested app.
