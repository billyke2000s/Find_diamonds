package uk.obskyra.diamonds.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** LevelDB byte primitives, CRC32C, and raw Snappy block decoding. */
final class Binary {
    final byte[] data; int pos;
    Binary(byte[] data) { this.data=data; }
    int left() { return data.length-pos; }
    int u8() throws IOException { if(left()<1)throw new EOFException(); return data[pos++]&255; }
    int i32() throws IOException { int n=0; for(int i=0;i<4;i++)n|=u8()<<(i*8); return n; }
    long i64() throws IOException { long n=0; for(int i=0;i<8;i++)n|=(long)u8()<<(i*8); return n; }
    long var() throws IOException {
        long n=0; for(int shift=0;shift<64;shift+=7) { int v=u8(); if(shift==63 && (v&254)!=0)throw new IOException("Varint overflow"); n|=(long)(v&127)<<shift; if((v&128)==0)return n; } throw new IOException("Invalid varint");
    }
    int size() throws IOException { long n=var(); if(n<0 || n>32*1024*1024)throw new IOException("LevelDB length exceeds 32 MiB"); return (int)n; }
    byte[] take(int n) throws IOException { if(n<0 || n>left())throw new EOFException(); byte[] b=Arrays.copyOfRange(data,pos,pos+n); pos+=n; return b; }
    byte[] str() throws IOException { return take(size()); }
    static int at32(byte[] b,int offset) throws IOException { Binary r=new Binary(b); r.pos=offset; return r.i32(); }
    static long at64(byte[] b,int offset) throws IOException { Binary r=new Binary(b); r.pos=offset; return r.i64(); }
    static byte[] inflate(byte[] input,boolean raw) throws IOException {
        Inflater inflater=new Inflater(raw);
        try(InputStream in=new InflaterInputStream(new ByteArrayInputStream(input),inflater)) { return read(in,32*1024*1024); }
        finally { inflater.end(); }
    }
    static byte[] read(InputStream in,int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[16384]; int n;
        while((n=in.read(b))!=-1) { Search.check(); if((long)out.size()+n>limit)throw new IOException("File exceeds import limit"); out.write(b,0,n); } return out.toByteArray();
    }
    static byte[] snappy(byte[] compressed) throws IOException {
        Binary r=new Binary(compressed); int length=r.size(),written=0; byte[] out=new byte[length];
        while(r.left()>0) {
            int tag=r.u8(),type=tag&3,n,offset=0;
            if(type==0) { int v=tag>>>2; if(v<60)n=v+1; else { int k=v-59; long amount=0; for(int i=0;i<k;i++)amount|=(long)r.u8()<<(8*i); if(amount>=length)throw new IOException("Invalid Snappy literal"); n=(int)amount+1; }
                if(n>length-written)throw new IOException("Snappy output overflow"); byte[] literal=r.take(n); System.arraycopy(literal,0,out,written,n); written+=n;
            } else {
                if(type==1) { n=4+((tag>>>2)&7); offset=((tag&224)<<3)|r.u8(); }
                else { n=1+(tag>>>2); long off=0; int k=type==2?2:4; for(int i=0;i<k;i++)off|=(long)r.u8()<<(8*i); if(off>Integer.MAX_VALUE)throw new IOException("Invalid Snappy offset"); offset=(int)off; }
                if(offset<1 || offset>written || n>length-written)throw new IOException("Invalid Snappy copy");
                for(int i=0;i<n;i++) { out[written]=out[written-offset]; written++; }
            }
        }
        if(written!=length)throw new IOException("Truncated Snappy block"); return out;
    }
    static int crc(byte[] b,int offset,int n,int suffix) {
        int c=-1; for(int i=0;i<n;i++)c=crcByte(c,b[offset+i]&255); if(suffix>=0)c=crcByte(c,suffix); return ~c;
    }
    private static int crcByte(int c,int b) { c^=b; for(int i=0;i<8;i++)c=(c>>>1)^((c&1)!=0?0x82f63b78:0); return c; }
    static int mask(int crc) { return Integer.rotateRight(crc,15)+0xa282ead8; }
}
