package uk.obskyra.diamonds.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only LevelDB snapshot reader: honours CURRENT, live tables, log sequence and tombstones. */
public final class BedrockDatabase {
    public interface Files { byte[] read(String name) throws IOException; Set<String> names(); }
    interface Record { void accept(byte[] record) throws IOException; }
    interface Entry { void accept(byte[] key,byte[] value) throws IOException; }
    private final Files files;
    private final Search q;
    private final Map<String,Value> latest=new HashMap<>();
    private long bytesRetained=0;
    private final Set<Long> live=new HashSet<>();
    private long logNumber=0,prevLog=0;
    private static final class Value { long seq; byte[] key,bytes; Value(long seq,byte[] key,byte[] bytes) { this.seq=seq; this.key=key; this.bytes=bytes; } }
    public BedrockDatabase(Files files,Search q) { this.files=files; this.q=q; }
    public void scan() throws IOException {
        String manifest=new String(files.read("CURRENT"),StandardCharsets.US_ASCII).trim();
        if(!manifest.matches("MANIFEST-[0-9]+"))throw new IOException("Invalid Bedrock CURRENT manifest");
        records(files.read(manifest),this::versionEdit);
        if(live.size()>20000)throw new IOException("Bedrock database has too many tables");
        List<Long> tables=new ArrayList<>(live); Collections.sort(tables); int read=0;
        for(long n:tables) {
            Search.check(); String name=String.format(Locale.ROOT,"%06d.ldb",n); if(!files.names().contains(name))name=String.format(Locale.ROOT,"%06d.sst",n);
            table(files.read(name),this::tableEntry); if(++read%8==0)q.progress("Read "+read+" / "+tables.size()+" Bedrock tables");
        }
        List<String> names=new ArrayList<>(files.names()); Collections.sort(names);
        for(String name:names)if(name.matches("[0-9]+\\.log")) { long n=Long.parseLong(name.substring(0,name.length()-4)); if(n>=logNumber || n==prevLog)records(files.read(name),this::batch); }
        Set<String> chunks=new HashSet<>();
        for(Value v:latest.values()) {
            Search.check(); if(v.bytes==null)continue; Binary key=new Binary(v.key); int cx=key.i32(),cz=key.i32();
            int dimension=v.key.length>=13?key.i32():0; int tag=key.u8();
            if(tag==0x2f)BedrockSubChunk.scan(v.bytes,cx,(byte)key.u8(),cz,q); else if(tag==0x30)BedrockSubChunk.legacyTerrain(v.bytes,cx,cz,q);
            chunks.add(cx+":"+cz+":"+dimension);
        }
        q.chunks+=chunks.size();
    }
    private void versionEdit(byte[] bytes) throws IOException {
        Binary b=new Binary(bytes);
        while(b.left()>0) {
            int tag=(int)b.var();
            switch(tag) {
                case 1:{String c=new String(b.str(),StandardCharsets.US_ASCII); if(!c.equals("leveldb.BytewiseComparator"))throw new IOException("Unsupported LevelDB comparator"); break;}
                case 2:logNumber=b.var();break; case 9:prevLog=b.var();break;
                case 3:case 4:b.var();break;
                case 5:b.var();b.str();break;
                case 6:b.var();live.remove(b.var());break;
                case 7:b.var();long n=b.var();b.var();b.str();b.str();live.add(n);break;
                default:throw new IOException("Unsupported LevelDB manifest tag "+tag);
            }
        }
    }
    private boolean selected(byte[] key) throws IOException {
        if(key.length!=9 && key.length!=10 && key.length!=13 && key.length!=14)return false;
        int dimension=key.length>=13?Binary.at32(key,8):0,tagOffset=key.length>=13?12:8;
        if(dimension!=q.dimension || !q.includesChunk(Binary.at32(key,0),Binary.at32(key,4)))return false;
        int tag=key[tagOffset]&255; return tag==0x2f && key.length==tagOffset+2 || tag==0x30 && key.length==tagOffset+1;
    }
    private void retain(byte[] key,byte[] value,long seq) throws IOException {
        if(!selected(key))return;
        String id=Base64.getEncoder().encodeToString(key); Value old=latest.get(id);
        if(old!=null && old.seq>=seq)return;
        long newSize=value==null?0:value.length; bytesRetained+=newSize-(old==null || old.bytes==null?0:old.bytes.length);
        if(bytesRetained>96*1024*1024)throw new IOException("Bedrock search exceeds memory budget; use a smaller radius");
        latest.put(id,new Value(seq,key,value));
    }
    private void tableEntry(byte[] key,byte[] value) throws IOException {
        if(key.length<8)throw new IOException("Invalid internal LevelDB key"); long trailer=Binary.at64(key,key.length-8); int type=(int)trailer&255;
        if(type!=0 && type!=1)throw new IOException("Unsupported LevelDB value type"); retain(Arrays.copyOf(key,key.length-8),type==0?null:value,trailer>>>8);
    }
    private void batch(byte[] bytes) throws IOException {
        Binary b=new Binary(bytes); long seq=b.i64(); int count=b.i32(); if(count<0 || count>2000000)throw new IOException("Invalid write batch");
        for(int i=0;i<count;i++) { int tag=b.u8(); if(tag!=0 && tag!=1)throw new IOException("Invalid batch operation"); byte[] key=b.str(),value=tag==1?b.str():null; retain(key,value,seq+i); }
        if(b.left()!=0)throw new IOException("Unexpected trailing batch bytes");
    }
    static void records(byte[] data,Record receiver) throws IOException {
        int offset=0; ByteArrayOutputStream fragmented=null;
        while(offset<data.length) {
            Search.check(); int left=32768-(offset%32768); if(left<7){offset+=left;continue;}
            if(data.length-offset<7)throw new IOException("Truncated LevelDB log header");
            int crc=Binary.at32(data,offset),length=(data[offset+4]&255)|((data[offset+5]&255)<<8),type=data[offset+6]&255;
            if(type==0 && length==0) { offset+=Math.min(left,data.length-offset);continue; }
            if(length>left-7 || length>data.length-offset-7)throw new IOException("Truncated LevelDB log record");
            byte[] checked=new byte[length+1]; checked[0]=(byte)type; System.arraycopy(data,offset+7,checked,1,length);
            if(Binary.mask(Binary.crc(checked,0,checked.length,-1))!=crc)throw new IOException("LevelDB log checksum mismatch");
            byte[] part=Arrays.copyOfRange(data,offset+7,offset+7+length); offset+=7+length;
            if(type==1) { if(fragmented!=null)throw new IOException("Interrupted log fragments"); receiver.accept(part); }
            else if(type==2) { if(fragmented!=null)throw new IOException("Duplicate first log fragment"); fragmented=new ByteArrayOutputStream(); fragmented.write(part); }
            else if(type==3 || type==4) { if(fragmented==null)throw new IOException("Missing first log fragment"); if(fragmented.size()+length>32*1024*1024)throw new IOException("Log record exceeds limit"); fragmented.write(part); if(type==4){receiver.accept(fragmented.toByteArray());fragmented=null;} }
            else throw new IOException("Unknown LevelDB log fragment");
        }
        if(fragmented!=null)throw new IOException("Incomplete LevelDB log record; export the world after closing Minecraft");
    }
    static void table(byte[] file,Entry receiver) throws IOException {
        if(file.length<48 || Binary.at64(file,file.length-8)!=0xdb4775248b80fb57L)throw new IOException("Invalid LevelDB table footer");
        Binary footer=new Binary(file); footer.pos=file.length-48; footer.var();footer.var();long off=footer.var(),len=footer.var();
        entries(block(file,off,len),(key,value)-> { Binary handle=new Binary(value); long blockOffset=handle.var(),blockLength=handle.var(); entries(block(file,blockOffset,blockLength),receiver); });
    }
    private static byte[] block(byte[] file,long offset,long length) throws IOException {
        Search.check(); if(offset<0 || length<0 || length>32*1024*1024 || offset>file.length-5-length)throw new IOException("Invalid LevelDB block handle");
        int off=(int)offset,n=(int)length,codec=file[off+n]&255,crc=Binary.at32(file,off+n+1);
        if(Binary.mask(Binary.crc(file,off,n,codec))!=crc)throw new IOException("LevelDB table checksum mismatch");
        byte[] payload=Arrays.copyOfRange(file,off,off+n);
        if(codec==0)return payload; if(codec==1)return Binary.snappy(payload); if(codec==2)return Binary.inflate(payload,false); if(codec==4)return Binary.inflate(payload,true);
        throw new IOException("Unsupported Bedrock table compression "+codec);
    }
    private static void entries(byte[] block,Entry receiver) throws IOException {
        if(block.length<4)throw new IOException("Truncated table block"); int restarts=Binary.at32(block,block.length-4);
        if(restarts<1 || restarts>(block.length-4)/4)throw new IOException("Invalid restart count"); int end=block.length-4-restarts*4;
        Binary b=new Binary(block); byte[] previous=new byte[0];
        while(b.pos<end) {
            int shared=b.size(),unique=b.size(),valueSize=b.size(); if(shared>previous.length || unique>4096 || shared+unique>4096)throw new IOException("Invalid prefix-compressed key");
            byte[] key=new byte[shared+unique]; System.arraycopy(previous,0,key,0,shared); byte[] suffix=b.take(unique);System.arraycopy(suffix,0,key,shared,unique);
            byte[] value=b.take(valueSize); if(b.pos>end)throw new IOException("Entry overlaps restart table"); receiver.accept(key,value); previous=key;
        }
        if(b.pos!=end)throw new IOException("Invalid block boundary");
    }
}
