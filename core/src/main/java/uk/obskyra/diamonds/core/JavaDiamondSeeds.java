package uk.obskyra.diamonds.core;

import java.io.IOException;
import java.util.*;

/** Seed-derived diamond candidate geometry, not a terrain or cave simulation.
 *
 * For partially air-discarding features only their FIRST attempt is emitted.
 * Later attempts can drift because terrain changes the number of RNG draws.
 * Buried diamonds have discard chance 1, so those draws do not occur; all four
 * attempts can be simulated without inventing a solid-world random stream.
 */
public final class JavaDiamondSeeds {
    private static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float)Math.sin(i * Math.PI * 2 / 65536);
    }

    private JavaDiamondSeeds() {}

    public static void search(long seed, DiamondProfile profile, Search query) throws IOException {
        if (query.dimension != 0 || query.ore != Ore.DIAMOND)
            throw new IllegalArgumentException("This seed engine currently supports Overworld diamonds only");
        // Adjacent origin chunks can place blocks inside the selected search area.
        int minX = Math.floorDiv(query.x - query.radius - 4, 16);
        int maxX = Math.floorDiv(query.x + query.radius + 4, 16);
        int minZ = Math.floorDiv(query.z - query.radius - 4, 16);
        int maxZ = Math.floorDiv(query.z + query.radius + 4, 16);
        WorldgenRng random = new WorldgenRng(profile.modern, seed);
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                Search.check();
                long population = random.decorationSeed(seed, cx * 16, cz * 16);
                if (!profile.modern) {
                    feature(query, random, population, cx, cz, profile.smallIndex, 1, 8, 0, 15, false, 1);
                } else {
                    feature(query, random, population, cx, cz, profile.smallIndex, 1, 4, -144, 16, true, 1);
                    if (profile.medium)
                        feature(query, random, population, cx, cz, 19, 1, 8, -64, -4, false, 1);
                    feature(query, random, population, cx, cz, profile.medium ? 20 : 19, 1, 12, -144, 16, true, 9);
                    feature(query, random, population, cx, cz, profile.buriedIndex, 4, 8, -144, 16, true, 1);
                }
                query.chunks++;
            }
            query.progress("Calculating diamond candidates from your seed…");
        }
    }

    private static void feature(Search query, WorldgenRng random, long population,
            int cx, int cz, int index, int attempts, int size, int minY, int maxY,
            boolean triangular, int rarity) throws IOException {
        random.seed(population + 60000L + index);
        if (rarity > 1 && random.nextFloat() >= 1f / rarity) return;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = cx * 16 + random.nextInt(16);
            int z = cz * 16 + random.nextInt(16);
            int range = maxY - minY;
            int y = triangular
                    ? minY + random.nextInt(range / 2 + 1) + random.nextInt(range - range / 2 + 1)
                    : minY + random.nextInt(range + 1);
            geometry(query, random, x, y, z, size, minY < 0 ? -64 : 0);
        }
    }

    private static void geometry(Search query, WorldgenRng random, int x, int y, int z,
            int size, int floor) throws IOException {
        float angle = random.nextFloat() * (float)Math.PI;
        float extent = size / 8f;
        double x0 = x + Math.sin(angle) * extent, x1 = x - Math.sin(angle) * extent;
        double z0 = z + Math.cos(angle) * extent, z1 = z - Math.cos(angle) * extent;
        double y0 = y + random.nextInt(3) - 2, y1 = y + random.nextInt(3) - 2;
        int margin = (int)Math.ceil((size / 16f * 2 + 1) / 2);
        int baseX = x - (int)Math.ceil(extent) - margin;
        int baseY = y - 2 - margin;
        int baseZ = z - (int)Math.ceil(extent) - margin;
        double[][] spheres = new double[size][4];
        for (int i = 0; i < size; i++) {
            float t = (float)i / size;
            double noise = random.nextDouble() * size / 16;
            float sine = SIN[(int)((float)Math.PI * t * 10430.378f) & 65535];
            spheres[i][0] = x0 + t * (x1 - x0);
            spheres[i][1] = y0 + t * (y1 - y0);
            spheres[i][2] = z0 + t * (z1 - z0);
            spheres[i][3] = ((sine + 1f) * noise + 1) / 2;
        }
        for (int i = 0; i < size - 1; i++) {
            if (spheres[i][3] <= 0) continue;
            for (int j = i + 1; j < size; j++) {
                if (spheres[j][3] <= 0) continue;
                double dx = spheres[i][0] - spheres[j][0];
                double dy = spheres[i][1] - spheres[j][1];
                double dz = spheres[i][2] - spheres[j][2];
                double dr = spheres[i][3] - spheres[j][3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0) spheres[j][3] = -1;
                    else spheres[i][3] = -1;
                }
            }
        }
        Set<Pos> visited = new HashSet<>();
        for (double[] s : spheres) {
            if (s[3] < 0) continue;
            int loX = Math.max((int)Math.floor(s[0] - s[3]), baseX);
            int loY = Math.max((int)Math.floor(s[1] - s[3]), baseY);
            int loZ = Math.max((int)Math.floor(s[2] - s[3]), baseZ);
            int hiX = Math.max((int)Math.floor(s[0] + s[3]), loX);
            int hiY = Math.max((int)Math.floor(s[1] + s[3]), loY);
            int hiZ = Math.max((int)Math.floor(s[2] + s[3]), loZ);
            for (int bx = loX; bx <= hiX; bx++) {
                double dx = (bx + .5 - s[0]) / s[3];
                for (int by = loY; by <= hiY; by++) {
                    if (by < floor || by >= 320) continue;
                    double dy = (by + .5 - s[1]) / s[3];
                    for (int bz = loZ; bz <= hiZ; bz++) {
                        double dz = (bz + .5 - s[2]) / s[3];
                        if (dx * dx + dy * dy + dz * dz < 1 && visited.add(new Pos(bx, by, bz)))
                            query.add(bx, by, bz);
                    }
                }
            }
        }
    }
}
