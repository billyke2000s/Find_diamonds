package uk.obskyra.diamonds.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded, endian-aware NBT reader. Bedrock disk NBT is not network-varint NBT. */
public final class Nbt {
    private final DataInputStream in;
    private final boolean little;
    private int budget=16*1024*1024, nodes=0;
    public Nbt(InputStream in,boolean little) { this.in=new DataInputStream(in); this.little=little; }
    private void spend(int n) throws IOException { if(n<0 || n>budget)throw new IOException("NBT exceeds 16 MiB budget"); budget-=n; }
    private int u8() throws IOException { spend(1); return in.readUnsignedByte(); }
    private int u16() throws IOException { spend(2); int n=in.readUnsignedShort(); return little ? Short.toUnsignedInt(Short.reverseBytes((short)n)) : n; }
    private int i32() throws IOException { spend(4); int n=in.readInt(); return little?Integer.reverseBytes(n):n; }
    private long i64() throws IOException { spend(8); long n=in.readLong(); return little?Long.reverseBytes(n):n; }
    private String string() throws IOException { int n=u16(); spend(n); byte[] b=new byte[n]; in.readFully(b); return new String(b,StandardCharsets.UTF_8); }
    public Map<String,Object> root() throws IOException { if(u8()!=10)throw new IOException("Expected NBT compound"); string(); return map(value(10,0)); }
    private Object value(int type,int depth) throws IOException {
        if(depth>64 || ++nodes>500000) throw new IOException("NBT nesting or node limit exceeded");
        switch(type) {
            case 1:return (byte)u8(); case 2:return (short)u16(); case 3:return i32(); case 4:return i64();
            case 5:return Float.intBitsToFloat(i32()); case 6:return Double.longBitsToDouble(i64());
            case 7:{ int n=length(1); spend(n); byte[] a=new byte[n]; in.readFully(a); return a; }
            case 8:return string();
            case 9:{ int t=u8(),n=length(1); if(t==0 && n!=0)throw new IOException("Invalid NBT list"); List<Object> a=new ArrayList<>(Math.min(n,4096)); for(int i=0;i<n;i++)a.add(value(t,depth+1)); return a; }
            case 10:{ Map<String,Object> a=new LinkedHashMap<>(); int t; while((t=u8())!=0){String name=string(); a.put(name,value(t,depth+1));} return a; }
            case 11:{int n=length(4); int[] a=new int[n]; for(int i=0;i<n;i++)a[i]=i32(); return a;}
            case 12:{int n=length(8); long[] a=new long[n]; for(int i=0;i<n;i++)a[i]=i64(); return a;}
            default:throw new IOException("Unknown NBT tag "+type);
        }
    }
    private int length(int unit) throws IOException { int n=i32(); if(n<0 || n>budget/unit || n>2000000)throw new IOException("Invalid NBT length"); return n; }
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object o) { return o instanceof Map ? (Map<String,Object>)o : Collections.emptyMap(); }
    @SuppressWarnings("unchecked") public static List<Object> list(Object o) { return o instanceof List ? (List<Object>)o : Collections.emptyList(); }
    public static int integer(Object o,int fallback) { return o instanceof Number?((Number)o).intValue():fallback; }
    public static String text(Object o) { return o instanceof String?(String)o:""; }
}
