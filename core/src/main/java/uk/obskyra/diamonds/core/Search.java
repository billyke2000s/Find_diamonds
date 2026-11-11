package uk.obskyra.diamonds.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.*;

public final class Search {
    public final int x,y,z,radius,dimension;
    public final Ore ore;
    public final Set<Pos> blocks = new HashSet<>();
    public int chunks, sections;
    public final Set<String> warnings = new LinkedHashSet<>();
    public interface Progress { void update(String message); }
    private final Progress progress;
    public Search(int x,int y,int z,int radius,int dimension,Ore ore,Progress progress) {
        if(Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000 || y < -2048 || y>2047 || radius<16 || radius>256 || dimension<0 || dimension>2) throw new IllegalArgumentException("Coordinates or radius out of range");
        this.x=x; this.y=y; this.z=z; this.radius=radius; this.dimension=dimension; this.ore=ore; this.progress=progress;
    }
    public static void check() throws InterruptedIOException { if(Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Search cancelled"); }
    public void progress(String s) throws IOException { check(); if(progress!=null) progress.update(s); }
    public boolean includesChunk(int cx,int cz) { return cx>=Math.floorDiv(x-radius,16) && cx<=Math.floorDiv(x+radius,16) && cz>=Math.floorDiv(z-radius,16) && cz<=Math.floorDiv(z+radius,16); }
    public void add(int bx,int by,int bz) throws IOException {
        if(Math.abs((long)bx-x)>radius || Math.abs((long)bz-z)>radius) return;
        if(blocks.size()>=200000) throw new IOException("Too many ore blocks. Choose a smaller radius.");
        blocks.add(new Pos(bx,by,bz));
    }
    public List<Vein> veins() throws IOException {
        Set<Pos> remaining=new HashSet<>(blocks); List<Vein> result=new ArrayList<>();
        int[][] dirs={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while(!remaining.isEmpty()) {
            check(); Pos first=remaining.iterator().next(); remaining.remove(first);
            ArrayDeque<Pos> queue=new ArrayDeque<>(); queue.add(first); Pos nearest=first; int count=0; List<Pos> members=new ArrayList<>();
            while(!queue.isEmpty()) { Pos p=queue.remove(); count++; members.add(p); double d=p.distance(x,y,z), n=nearest.distance(x,y,z);
                if(d<n || d==n && compare(p,nearest)<0) nearest=p;
                for(int[] a:dirs) { Pos next=new Pos(p.x+a[0],p.y+a[1],p.z+a[2]); if(remaining.remove(next)) queue.add(next); }
            }
            result.add(new Vein(nearest,count,nearest.distance(x,y,z),members));
        }
        Collections.sort(result,(a,b)-> { int c=Double.compare(a.distance,b.distance); return c!=0 ? c : compare(a.pos,b.pos); }); return result;
    }
    private static int compare(Pos a,Pos b) { int c=Integer.compare(a.x,b.x); if(c==0)c=Integer.compare(a.y,b.y); if(c==0)c=Integer.compare(a.z,b.z); return c; }
    public static final class Vein {
        public final Pos pos; public final int count; public final double distance; public final List<Pos> blocks;
        Vein(Pos p,int count,double distance,List<Pos> blocks) { this.pos=p; this.count=count; this.distance=distance; this.blocks=Collections.unmodifiableList(blocks); }
    }
}
