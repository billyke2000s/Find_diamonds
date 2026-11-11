package uk.obskyra.diamonds.core;

import java.io.*;
import java.util.*;

/** Disk subchunks only: persistent little-endian NBT palettes, not runtime IDs. */
public final class BedrockSubChunk {
    private BedrockSubChunk() {}
    public static void scan(byte[] payload,int cx,int sy,int cz,Search q) throws IOException {
        ByteArrayInputStream bytes=new ByteArrayInputStream(payload); DataInputStream in=new DataInputStream(bytes);
        int version=in.readUnsignedByte();
        if(version==0) {
            byte[] ids=new byte[4096]; in.readFully(ids);
            for(int i=0;i<4096;i++)if(q.ore.matches(ids[i]&255))q.add(cx*16+(i>>8),sy*16+(i&15),cz*16+((i>>4)&15));
        } else {
            if(version!=1 && version!=8 && version!=9)throw new IOException("Unsupported Bedrock subchunk format "+version);
            int layers=version==1?1:in.readUnsignedByte(); if(layers>2)throw new IOException("Unsupported Bedrock storage layer count");
            if(version==9) { int storedY=in.readByte(); if(storedY!=sy)throw new IOException("Bedrock subchunk Y mismatch"); }
            for(int layer=0;layer<layers;layer++) {
                Search.check(); int header=in.readUnsignedByte(),bits=header>>>1;
                if((header&1)!=0)throw new IOException("Runtime palette IDs cannot be decoded as saved world data");
                if(!(bits==0 || bits==1 || bits==2 || bits==3 || bits==4 || bits==5 || bits==6 || bits==8 || bits==16))throw new IOException("Invalid Bedrock palette width");
                int per=bits==0?0:32/bits,words=bits==0?0:(4096+per-1)/per; int[] packed=new int[words];
                for(int i=0;i<words;i++)packed[i]=Integer.reverseBytes(in.readInt());
                int size=Integer.reverseBytes(in.readInt()); if(size<1 || size>4096 || bits==0 && size!=1)throw new IOException("Invalid Bedrock palette size");
                boolean[] match=new boolean[size];
                for(int i=0;i<size;i++) { Map<String,Object> tag=new Nbt(bytes,true).root(); if(!(tag.get("name") instanceof String))throw new IOException("Missing Bedrock palette block name"); match[i]=q.ore.matches(Nbt.text(tag.get("name"))); }
                int mask=bits==0?0:(1<<bits)-1;
                for(int i=0;i<4096;i++) {
                    int ix=bits==0?0:(packed[i/per]>>>((i%per)*bits))&mask; if(ix>=size)throw new IOException("Bedrock palette index out of range");
                    if(match[ix])q.add(cx*16+(i>>8),sy*16+(i&15),cz*16+((i>>4)&15));
                }
            }
        }
        q.sections++;
    }
    public static void legacyTerrain(byte[] bytes,int cx,int cz,Search q) throws IOException {
        if(bytes.length<32768)throw new IOException("Truncated legacy Bedrock terrain");
        for(int i=0;i<32768;i++)if(q.ore.matches(bytes[i]&255))q.add(cx*16+(i>>11),i&127,cz*16+((i>>7)&15));
        q.sections+=8;
    }
}
