package uk.obskyra.diamonds.core;
import java.io.*;
import java.util.*;

/** CLI for comparison with an independent world-file decoder in CI. */
public final class FixtureScan {
    public static void main(String[] args)throws Exception {
        Search q=new Search(Integer.parseInt(args[3]),0,Integer.parseInt(args[4]),32,0,Ore.DIAMOND,null);
        JavaRegion.scan(new File(args[0]),Integer.parseInt(args[1]),Integer.parseInt(args[2]),q);
        List<String> blocks=new ArrayList<>();for(Pos p:q.blocks)blocks.add(p.x+","+p.y+","+p.z);Collections.sort(blocks);for(String line:blocks)System.out.println(line);
    }
}
