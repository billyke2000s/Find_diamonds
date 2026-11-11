package uk.obskyra.diamonds.core;

/** Minecraft's bit-oriented worldgen wrapper, including its two-draw nextLong.
 * This deliberately does not use the underlying xoroshiro bounded-int method.
 */
final class WorldgenRng {
    private static final long MASK = (1L << 48) - 1;
    private final boolean modern;
    private long legacy, low, high;

    WorldgenRng(boolean modern, long seed) {
        this.modern = modern;
        seed(seed);
    }

    void seed(long value) {
        if (!modern) {
            legacy = (value ^ 0x5deece66dL) & MASK;
            return;
        }
        long silver = value ^ 0x6a09e667f3bcc909L;
        low = mix(silver);
        high = mix(silver + 0x9e3779b97f4a7c15L);
        if ((low | high) == 0) {
            low = 0x9e3779b97f4a7c15L;
            high = 0x6a09e667f3bcc909L;
        }
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    int bits(int count) {
        if (!modern) {
            legacy = (legacy * 0x5deece66dL + 11) & MASK;
            return (int)(legacy >>> (48 - count));
        }
        long result = Long.rotateLeft(low + high, 17) + low;
        long xor = high ^ low;
        low = Long.rotateLeft(low, 49) ^ xor ^ (xor << 21);
        high = Long.rotateLeft(xor, 28);
        return (int)(result >>> (64 - count));
    }

    long nextLong() {
        return ((long)bits(32) << 32) + bits(32);
    }

    int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Positive random bound required");
        if ((bound & (bound - 1)) == 0) return (int)((bound * (long)bits(31)) >> 31);
        int value, result;
        do {
            value = bits(31);
            result = value % bound;
        } while (value - result + (bound - 1) < 0);
        return result;
    }

    float nextFloat() { return bits(24) * 0x1.0p-24f; }

    double nextDouble() {
        return (((long)bits(26) << 27) + bits(27)) * 0x1.0p-53;
    }

    long decorationSeed(long worldSeed, int blockX, int blockZ) {
        seed(worldSeed);
        long xMultiplier = nextLong() | 1L;
        long zMultiplier = nextLong() | 1L;
        long result = (blockX * xMultiplier + blockZ * zMultiplier) ^ worldSeed;
        seed(result);
        return result;
    }
}
