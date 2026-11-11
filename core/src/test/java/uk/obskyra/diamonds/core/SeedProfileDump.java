package uk.obskyra.diamonds.core;

/** CI-only introspection: no runtime downloads or dependencies in the app. */
public final class SeedProfileDump {
    public static void main(String[] args) {
        for(DiamondProfile p:DiamondProfile.values())
            System.out.println(p.version+","+p.smallIndex+","+p.buriedIndex+","+p.medium);
    }
}
