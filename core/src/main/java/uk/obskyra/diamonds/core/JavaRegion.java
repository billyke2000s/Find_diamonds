package uk.obskyra.diamonds.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Reads Anvil terrain; searches saved blocks rather than guessing generation. */
public final class JavaRegion {
    private JavaRegion() {}
    public static void scan(File file,int rx,int rz,Search q) throws IOException {
        try(RandomAccessFile f=new RandomAccessFile(file,"r")) {
            if(f.length()<8192)throw new IOException("Truncated region header");
            for(int local=0;local<1024;local++) {
                Search.check(); int cx=rx*32+(local%32),cz=rz*32+(local/32); if(!q.includesChunk(cx,cz))continue;
                f.seek(local*4L); int location=f.readInt(),sector=location>>>8,sectors=location&255; if(sector==0 && sectors==0)continue;
                if(sector<2 || sectors==0 || sector*4096L+5>f.length())throw new IOException("Invalid region sector");
                f.seek(sector*4096L); int length=f.readInt(),codec=f.readUnsignedByte();
                if((codec&128)!=0)throw new IOException("External .mcc chunks need a future importer; this file is unsupported");
                if(length<2 || length>sectors*4096-4 || sector*4096L+4+length>f.length())throw new IOException("Invalid chunk length");
                byte[] payload=new byte[length-1]; f.readFully(payload); InputStream input=new ByteArrayInputStream(payload);
                if(codec==1)input=new GZIPInputStream(input); else if(codec==2)input=new InflaterInputStream(input); else if(codec!=3)throw new IOException("Unsupported region compression "+codec+" (LZ4 is not enabled)");
                try(InputStream decoded=input) { scanChunk(new Nbt(decoded,false).root(),cx,cz,q); }
                q.chunks++; if(q.chunks%16==0)q.progress("Read "+q.chunks+" Java chunks");
            }
        }
    }
    public static void scanChunk(Map<String,Object> root,int cx,int cz,Search q) throws IOException {
        int version=Nbt.integer(root.get("DataVersion"),0);
        Map<String,Object> level=root.containsKey("Level")?Nbt.map(root.get("Level")):root;
        if(Nbt.integer(level.get("xPos"),cx)!=cx || Nbt.integer(level.get("zPos"),cz)!=cz)throw new IOException("Region chunk coordinates disagree with NBT");
        Object sectionList=level.containsKey("sections")?level.get("sections"):level.get("Sections");
        if(!(sectionList instanceof List))throw new IOException("Chunk has no supported Anvil section list");
        for(Object o:Nbt.list(sectionList)) {
            Search.check(); Map<String,Object> sec=Nbt.map(o); int sy=Nbt.integer(sec.get("Y"),Integer.MIN_VALUE);
            if(sy < -128 || sy>127)throw new IOException("Invalid section Y");
            if(sec.get("Blocks") instanceof byte[]) {
                byte[] ids=(byte[])sec.get("Blocks"),extra=sec.get("Add") instanceof byte[]?(byte[])sec.get("Add"):null;
                if(ids.length!=4096 || extra!=null && extra.length!=2048)throw new IOException("Invalid legacy section");
                for(int i=0;i<4096;i++) { int id=ids[i]&255; if(extra!=null)id|=((extra[i>>1]>>>((i&1)*4))&15)<<8;
                    if(q.ore.matches(id))q.add(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)); }
            } else {
                Map<String,Object> states=Nbt.map(sec.get("block_states"));
                Object paletteObject=states.isEmpty()?sec.get("Palette"):states.get("palette");
                if(paletteObject==null) { if(states.isEmpty())continue; throw new IOException("Missing block palette"); }
                List<Object> palette=Nbt.list(paletteObject); if(palette.isEmpty() || palette.size()>4096)throw new IOException("Invalid block palette");
                boolean[] match=new boolean[palette.size()]; boolean any=false;
                for(int i=0;i<match.length;i++) { Map<String,Object> entry=Nbt.map(palette.get(i)); if(!(entry.get("Name") instanceof String))throw new IOException("Missing palette block name"); match[i]=q.ore.matches(Nbt.text(entry.get("Name"))); any|=match[i]; }
                if(!any) { q.sections++; continue; }
                Object raw=states.isEmpty()?sec.get("BlockStates"):states.get("data");
                if(raw==null && palette.size()==1) { if(match[0])for(int i=0;i<4096;i++)q.add(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15)); }
                else {
                    if(!(raw instanceof long[]))throw new IOException("Missing packed block states"); long[] data=(long[])raw;
                    int bits=Math.max(4,32-Integer.numberOfLeadingZeros(palette.size()-1)); int per=64/bits;
                    boolean padded=version>=2529 || !states.isEmpty();
                    int needed=padded?(4096+per-1)/per:(4096*bits+63)/64;
                    if(data.length!=needed)throw new IOException("Unrecognised Java palette packing"); long mask=(1L<<bits)-1;
                    for(int i=0;i<4096;i++) {
                        int index;
                        if(padded)index=(int)((data[i/per]>>>((i%per)*bits))&mask);
                        else { int bit=i*bits,word=bit>>>6,shift=bit&63; long n=data[word]>>>shift; if(shift+bits>64)n|=data[word+1]<<(64-shift); index=(int)(n&mask); }
                        if(index>=match.length)throw new IOException("Block palette index out of range");
                        if(match[index])q.add(cx*16+(i&15),sy*16+(i>>8),cz*16+((i>>4)&15));
                    }
                }
            }
            q.sections++;
        }
    }
}
