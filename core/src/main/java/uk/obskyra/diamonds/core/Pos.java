package uk.obskyra.diamonds.core;

public final class Pos {
    public final int x, y, z;
    public Pos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public double distance(int x, int y, int z) { double a = (double)this.x-x, b = (double)this.y-y, c = (double)this.z-z; return Math.sqrt(a*a+b*b+c*c); }
    @Override public boolean equals(Object o) { if (!(o instanceof Pos)) return false; Pos p=(Pos)o; return x==p.x && y==p.y && z==p.z; }
    @Override public int hashCode() { return (x*31+y)*31+z; }
    @Override public String toString() { return x+", "+y+", "+z; }
}
