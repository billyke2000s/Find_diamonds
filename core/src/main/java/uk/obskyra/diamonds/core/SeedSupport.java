package uk.obskyra.diamonds.core;

/** The app must never quietly use a Java RNG for Bedrock or invent locations. */
public final class SeedSupport {
    public enum Edition { JAVA, BEDROCK }
    private SeedSupport() {}
    public static long numericSeed(String text) {
        String s=text.trim();if(!s.matches("[+-]?[0-9]+"))throw new IllegalArgumentException("Enter the numeric world seed from Minecraft");
        try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IllegalArgumentException("Seed must fit a signed 64-bit number; it was not converted to a text seed");}
    }
    public static String unavailable(Edition edition,String version) {
        return "Seed-only prediction for "+(edition==Edition.JAVA?"Java":"Bedrock")+" "+version+" has not been verified in this preview. Import a saved world to read its actual ore blocks offline. There is no paid unlock.";
    }
}
