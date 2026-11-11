package uk.obskyra.diamonds.core;

public enum Ore {
    DIAMOND("Diamond", 56, "diamond_ore", "deepslate_diamond_ore"),
    DEBRIS("Ancient debris", -1, "ancient_debris"),
    IRON("Iron", 15, "iron_ore", "deepslate_iron_ore"),
    GOLD("Gold", 14, "gold_ore", "deepslate_gold_ore", "nether_gold_ore"),
    REDSTONE("Redstone", 73, "redstone_ore", "lit_redstone_ore", "deepslate_redstone_ore", "lit_deepslate_redstone_ore"),
    LAPIS("Lapis", 21, "lapis_ore", "deepslate_lapis_ore"),
    EMERALD("Emerald", 129, "emerald_ore", "deepslate_emerald_ore"),
    COPPER("Copper", -1, "copper_ore", "deepslate_copper_ore"),
    COAL("Coal", 16, "coal_ore", "deepslate_coal_ore"),
    QUARTZ("Quartz", 153, "quartz_ore", "nether_quartz_ore");
    public final String label;
    private final int legacy;
    private final String[] names;
    Ore(String label, int legacy, String... names) { this.label=label; this.legacy=legacy; this.names=names; }
    public boolean matches(String name) { if (name.startsWith("minecraft:")) name=name.substring(10); for(String n:names) if(n.equals(name)) return true; return false; }
    public boolean matches(int id) { return id>=0 && (id==legacy || this==REDSTONE && id==74); }
    @Override public String toString() { return label; }
}
