package uk.obskyra.diamonds.core;

/** Exact release identifiers only; other releases are never silently aliased. */
public enum DiamondProfile {
    JAVA_1_21_1("1.21.1", true, true, 18, 21),
    JAVA_1_20_2("1.20.2", true, true, 18, 21),
    JAVA_1_20_1("1.20.1", true, false, 18, 20),
    JAVA_1_19_2("1.19.2", true, false, 18, 20),
    JAVA_1_18_2("1.18.2", true, false, 18, 20),
    JAVA_1_17_1("1.17.1", false, false, 11, -1),
    JAVA_1_16_5("1.16.5", false, false, 9, -1);

    public final String version;
    final boolean modern, medium;
    final int smallIndex, buriedIndex;

    DiamondProfile(String version, boolean modern, boolean medium, int smallIndex, int buriedIndex) {
        this.version = version;
        this.modern = modern;
        this.medium = medium;
        this.smallIndex = smallIndex;
        this.buriedIndex = buriedIndex;
    }

    public static DiamondProfile from(String version) {
        for (DiamondProfile profile : values()) if (profile.version.equals(version)) return profile;
        throw new IllegalArgumentException("No seed profile for Java " + version);
    }

    @Override public String toString() { return version; }
}
