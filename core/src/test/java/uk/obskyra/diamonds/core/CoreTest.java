package uk.obskyra.diamonds.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Dependency-free integration checks with explicit expected coordinates. */
public final class CoreTest {
    private static int checks=0;
    interface Checked { void run() throws Exception; }
    private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void bad(Checked run,String message) throws Exception {boolean rejected=false;try{run.run();}catch(IOException|IllegalArgumentException e){rejected=true;}ok(rejected,message);}
    public static void main(String[] args) throws Exception {
        testBinary();testJava();testBedrock();testDatabase();testArchive();testSearch();testSeeds();testSeedGeneration();
        System.out.println("PASS: "+checks+" core checks");
    }
    static Search query(int x,int y,int z){return new Search(x,y,z,32,0,Ore.DIAMOND,null);}
    static void testBinary() throws Exception {
        byte[] abc="123456789".getBytes(StandardCharsets.US_ASCII);ok(Binary.crc(abc,0,abc.length,-1)==0xe3069283,"CRC32C independent standard check vector");
        ok(new String(Binary.snappy(new byte[]{5,16,'h','e','l','l','o'}),StandardCharsets.US_ASCII).equals("hello"),"Snappy literal");
        ok(new String(Binary.snappy(new byte[]{8,0,'a',13,1}),StandardCharsets.US_ASCII).equals("aaaaaaaa"),"Snappy overlapping copy");
        bad(()->Binary.snappy(new byte[]{8,29,0}),"Reject invalid Snappy backref");
        bad(()->new Binary(new byte[]{(byte)128}).var(),"Reject truncated varint");
        byte[] text="compressed contents".getBytes(StandardCharsets.US_ASCII);
        for(boolean raw:new boolean[]{false,true}){Deflater d=new Deflater(6,raw);d.setInput(text);d.finish();byte[] b=new byte[200];int n=d.deflate(b);d.end();ok(Arrays.equals(Binary.inflate(Arrays.copyOf(b,n),raw),text),"Inflate raw="+raw);}
    }
    static void testJava() throws Exception {
        for(boolean modern:new boolean[]{false,true}) {
            int bits=5,index=(2<<8)|(3<<4)|4;boolean padded=modern;int per=64/bits;
            long[] data=new long[padded?(4096+per-1)/per:(4096*bits+63)/64];
            if(padded)data[index/per]|=1L<<((index%per)*bits);else{int bit=index*bits;data[bit>>>6]|=1L<<(bit&63);}
            List<Object> palette=new ArrayList<>();palette.add(Collections.singletonMap("Name","minecraft:stone"));palette.add(Collections.singletonMap("Name","minecraft:deepslate_diamond_ore"));for(int i=2;i<17;i++)palette.add(Collections.singletonMap("Name","minecraft:unused_"+i));
            Map<String,Object> sec=new HashMap<>();sec.put("Y",(byte)-4);if(modern){Map<String,Object> states=new HashMap<>();states.put("palette",palette);states.put("data",data);sec.put("block_states",states);}else{sec.put("Palette",palette);sec.put("BlockStates",data);}
            Map<String,Object> level=new HashMap<>();level.put("xPos",-1);level.put("zPos",0);level.put(modern?"sections":"Sections",Collections.singletonList(sec));Map<String,Object> root=new HashMap<>();root.put("DataVersion",modern?3105:1976);if(modern)root.putAll(level);else root.put("Level",level);
            Search q=query(-12,-62,3);JavaRegion.scanChunk(root,-1,0,q);ok(q.blocks.equals(Collections.singleton(new Pos(-12,-62,3))),"Java "+(modern?"padded negative Y":"cross-word negative Y"));
            bad(()->JavaRegion.scanChunk(root,0,0,query(0,0,0)),"Reject wrong chunk coordinate");
        }
        byte[] ids=new byte[4096];ids[(7<<8)|(2<<4)|1]=56;Map<String,Object> sec=new HashMap<>();sec.put("Y",(byte)0);sec.put("Blocks",ids);Map<String,Object> level=new HashMap<>();level.put("Sections",Collections.singletonList(sec));Search q=query(1,7,2);JavaRegion.scanChunk(Collections.singletonMap("Level",level),0,0,q);ok(q.blocks.contains(new Pos(1,7,2)),"Legacy numeric ID section");
        byte[] huge={10,0,0,7,0,1,'x',127,-1,-1,-1};bad(()->new Nbt(new ByteArrayInputStream(huge),false).root(),"Reject huge NBT array");
        // Palette entry 16 at block index 12 crosses the 64-bit boundary in
        // pre-1.16 five-bit packing. Its single set bit is bit zero of word 1.
        List<Object> wide=new ArrayList<>();for(int i=0;i<17;i++)wide.add(Collections.singletonMap("Name",i==16?"minecraft:diamond_ore":"minecraft:stone"));
        long[] crossing=new long[320];crossing[1]=1;Map<String,Object> wideSection=new HashMap<>();wideSection.put("Y",(byte)0);wideSection.put("Palette",wide);wideSection.put("BlockStates",crossing);
        Map<String,Object> wideRoot=new HashMap<>();wideRoot.put("DataVersion",1976);wideRoot.put("Sections",Collections.singletonList(wideSection));Search crossed=query(12,0,0);JavaRegion.scanChunk(wideRoot,0,0,crossed);ok(crossed.blocks.equals(Collections.singleton(new Pos(12,0,0))),"Five-bit palette entry spanning two long words");
    }
    static byte[] nbtName(String name) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(10);le16(b,0);b.write(8);string(b,"name");string(b,name);b.write(0);return b.toByteArray();}
    static byte[] subchunk(int version,int sy) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(version);if(version>=8)b.write(1);if(version==9)b.write(sy);b.write(2);
        int index=(4<<8)|(3<<4)|2;for(int i=0;i<128;i++)le32(b,i==index/32?1<<(index%32):0);le32(b,2);b.write(nbtName("minecraft:stone"));b.write(nbtName("minecraft:diamond_ore"));return b.toByteArray();
    }
    static void testBedrock() throws Exception {
        for(int v:new int[]{1,8,9}){Search q=query(-12,-62,3);BedrockSubChunk.scan(subchunk(v,-4),-1,-4,0,q);ok(q.blocks.equals(Collections.singleton(new Pos(-12,-62,3))),"Bedrock v"+v+" persistent palette");}
        Search q=query(4,2,3);byte[] old=new byte[1+4096];old[1+(4<<8)+(3<<4)+2]=56;BedrockSubChunk.scan(old,0,0,0,q);ok(q.blocks.contains(new Pos(4,2,3)),"Bedrock numeric legacy");
        bad(()->BedrockSubChunk.scan(new byte[]{42},0,0,0,query(0,0,0)),"Reject unknown Bedrock format");
        bad(()->BedrockSubChunk.scan(new byte[]{9,1,0,3},0,0,0,query(0,0,0)),"Reject runtime IDs");
        bad(()->BedrockSubChunk.scan(subchunk(9,-4),0,0,0,query(0,0,0)),"Reject Bedrock Y mismatch");
    }
    static byte[] key(int cx,int cz,int sy,int dimension) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();le32(b,cx);le32(b,cz);if(dimension!=0)le32(b,dimension);b.write(0x2f);b.write(sy);return b.toByteArray();}
    static byte[] batch(long seq,byte[] key,byte[] value) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();le64(b,seq);le32(b,1);b.write(value==null?0:1);varString(b,key);if(value!=null)varString(b,value);return log(b.toByteArray());}
    static byte[] log(byte[] payload) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] checked=new byte[payload.length+1];checked[0]=1;System.arraycopy(payload,0,checked,1,payload.length);le32(b,Binary.mask(Binary.crc(checked,0,checked.length,-1)));le16(b,payload.length);b.write(1);b.write(payload);return b.toByteArray();}
    static void testDatabase() throws Exception {
        Map<String,byte[]> files=new HashMap<>();files.put("CURRENT","MANIFEST-000001\n".getBytes(StandardCharsets.US_ASCII));files.put("MANIFEST-000001",log(new byte[]{2,2}));byte[] k=key(-1,0,-4,0);
        files.put("000002.log",batch(20,k,subchunk(9,-4)));files.put("000001.log",batch(10,k,subchunk(9,-4)));
        Search q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.contains(new Pos(-12,-62,3)),"Replay current Bedrock log");
        files.put("000003.log",batch(21,k,null));q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.isEmpty(),"Latest tombstone hides old ore");
        files.remove("000002.log");files.remove("000003.log");q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.isEmpty(),"Do not resurrect obsolete log");
        files.put("000002.log",batch(22,key(-1,0,-4,1),subchunk(9,-4)));q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.isEmpty(),"Separate dimension keys");
        byte[] damaged=batch(30,k,subchunk(9,-4));damaged[damaged.length-1]^=1;files.put("000002.log",damaged);bad(()->database(files,query(-12,-62,3)).scan(),"Reject corrupt log CRC");
        // One SST: an actual internal key, persistent subchunk payload, restart arrays and block handles.
        byte[] internal=concat(k,longLE((40L<<8)|1));byte[] table=sst(internal,subchunk(9,-4));
        files.remove("000002.log");ByteArrayOutputStream edit=new ByteArrayOutputStream();edit.write(new byte[]{2,2,7,0,4});var(edit,table.length);varString(edit,internal);varString(edit,internal);files.put("MANIFEST-000001",log(edit.toByteArray()));files.put("000004.ldb",table);q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.equals(Collections.singleton(new Pos(-12,-62,3))),"Live SST ore decoded");
        files.put("000005.ldb",new byte[]{0});q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.size()==1,"Ignore obsolete SST files");
        edit.write(new byte[]{6,0,4});files.put("MANIFEST-000001",log(edit.toByteArray()));q=query(-12,-62,3);database(files,q).scan();ok(q.blocks.isEmpty(),"Manifest deletion removes obsolete SST");
        table[1]^=1;bad(()->BedrockDatabase.table(table,(a,b)->{}),"Reject corrupt table checksum");
    }
    static BedrockDatabase database(Map<String,byte[]> source,Search q){return new BedrockDatabase(new BedrockDatabase.Files(){public byte[] read(String name)throws IOException{byte[] b=source.get(name);if(b==null)throw new IOException("Missing "+name);return b;}public Set<String> names(){return source.keySet();}},q);}
    static byte[] block(byte[] key,byte[] value) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();var(b,0);var(b,key.length);var(b,value.length);b.write(key);b.write(value);le32(b,0);le32(b,1);return b.toByteArray();}
    static void appendBlock(ByteArrayOutputStream b,byte[] data)throws IOException{b.write(data);b.write(0);le32(b,Binary.mask(Binary.crc(data,0,data.length,0)));}
    static byte[] sst(byte[] key,byte[] value)throws IOException{byte[] data=block(key,value);ByteArrayOutputStream b=new ByteArrayOutputStream();appendBlock(b,data);int off=b.size();ByteArrayOutputStream handle=new ByteArrayOutputStream();var(handle,0);var(handle,data.length);byte[] index=block(key,handle.toByteArray());appendBlock(b,index);ByteArrayOutputStream footer=new ByteArrayOutputStream();var(footer,0);var(footer,0);var(footer,off);var(footer,index.length);while(footer.size()<40)footer.write(0);le64(footer,0xdb4775248b80fb57L);b.write(footer.toByteArray());return b.toByteArray();}
    static void testArchive() throws Exception {
        File zip=File.createTempFile("world-test-",".mcworld"),cache=Files.createTempDirectory("world-cache").toFile();
        try{Map<String,byte[]> entries=new LinkedHashMap<>();entries.put("db/CURRENT","MANIFEST-000001\n".getBytes(StandardCharsets.US_ASCII));entries.put("db/MANIFEST-000001",log(new byte[]{2,2}));entries.put("db/000002.log",batch(20,key(-1,0,-4,0),subchunk(9,-4)));writeZip(zip,entries);Search q=query(-12,-62,3);String edition=WorldArchive.scan(zip,"world.mcworld",cache,q);ok(edition.startsWith("Bedrock") && q.blocks.size()==1,"Import full Bedrock export");entries.put("../oops",new byte[]{1});writeZip(zip,entries);bad(()->WorldArchive.scan(zip,"world.mcworld",cache,query(0,0,0)),"Reject traversal paths");}finally{zip.delete();cache.delete();}
    }
    static void testSearch() throws Exception {Search q=query(-1,0,-1);ok(q.includesChunk(-1,-1),"Negative floor chunk");q.add(-1,0,-1);q.add(-2,0,-1);q.add(8,0,8);List<Search.Vein> v=q.veins();ok(v.size()==2 && v.get(0).count==2 && v.get(0).pos.equals(new Pos(-1,0,-1)),"Group and order ore veins");q.add(1000,0,1000);ok(q.blocks.size()==3,"Exclude out-of-radius ore");Thread.currentThread().interrupt();try{bad(()->Search.check(),"Cancellation interrupts searches");}finally{Thread.interrupted();}}
    static void testSeeds() throws Exception {ok(SeedSupport.numericSeed("-9223372036854775808")==Long.MIN_VALUE,"Minimum signed seed");ok(SeedSupport.numericSeed("9223372036854775807")==Long.MAX_VALUE,"Maximum signed seed");bad(()->SeedSupport.numericSeed("9223372036854775808"),"No numeric overflow fallback to text hash");bad(()->SeedSupport.numericSeed("hello"),"No invented text seed support");ok(SeedSupport.unavailable(SeedSupport.Edition.BEDROCK,"1.21").contains("not been verified"),"Unsupported seed version is explicit");}
    static void testSeedGeneration() throws Exception {
        long[] seeds={0,1,42,Long.MAX_VALUE,Long.MIN_VALUE};
        long[][] vectors={
            {3038984751730664151L,4633751808550626728L,1839370570109928317L},
            {-1033667707451936253L,-1821890264506418920L,8094835630624897715L},
            {-4695948378802814517L,-7542733517267348717L,8419651029966521324L},
            {-4337892647980267059L,6464058115248227873L,-4107676909484759718L},
            {5559461709638992951L,-3866801758199126022L,-137015973405880411L}
        };
        // Generated independently with a Python unsigned-64 reference, including
        // signed low-word addition in the game's bit-oriented wrapper.
        for(int i=0;i<seeds.length;i++) {
            WorldgenRng modern=new WorldgenRng(true,seeds[i]);
            for(long expected:vectors[i])ok(modern.nextLong()==expected,"Modern worldgen RNG reference vector");
            Random standard=new Random(seeds[i]);WorldgenRng legacy=new WorldgenRng(false,seeds[i]);
            for(int bound:new int[]{1,2,3,16,81,1000000007,Integer.MAX_VALUE}) {
                ok(legacy.nextInt(bound)==standard.nextInt(bound),"Legacy bounded RNG agrees with JDK");
                ok(legacy.nextLong()==standard.nextLong(),"Legacy long RNG agrees with JDK");
                ok(legacy.nextDouble()==standard.nextDouble(),"Legacy double RNG agrees with JDK");
            }
        }
        for(DiamondProfile profile:DiamondProfile.values()) {
            Search small=new Search(-1,0,-1,32,0,Ore.DIAMOND,null);
            JavaDiamondSeeds.search(42,profile,small);
            ok(!small.blocks.isEmpty(),"Seed search produces candidate geometry: "+profile.version);
            Search large=new Search(-1,0,-1,64,0,Ore.DIAMOND,null);
            JavaDiamondSeeds.search(42,profile,large);
            ok(large.blocks.containsAll(small.blocks),"Cross-chunk/radius consistency: "+profile.version);
            boolean bounded=true;
            for(Pos p:small.blocks)bounded&=Math.abs(p.x+1)<=32 && Math.abs(p.z+1)<=32 && p.y>=(profile.modern?-64:0) && p.y<=16;
            ok(bounded,"Version height and search bounds: "+profile.version);
        }
        bad(()->DiamondProfile.from("1.21.11"),"Do not alias unsupported version to a different generator");
        bad(()->JavaDiamondSeeds.search(42,DiamondProfile.JAVA_1_21_1,new Search(0,0,0,32,1,Ore.DIAMOND,null)),"Reject unsupported seed dimension");
        Thread.currentThread().interrupt();try{bad(()->JavaDiamondSeeds.search(42,DiamondProfile.JAVA_1_21_1,query(0,0,0)),"Cancel seed generation");}finally{Thread.interrupted();}
    }
    static void writeZip(File file,Map<String,byte[]> entries)throws IOException{try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))){for(Map.Entry<String,byte[]> e:entries.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}}
    static void string(OutputStream out,String s)throws IOException{byte[] bytes=s.getBytes(StandardCharsets.UTF_8);le16(out,bytes.length);out.write(bytes);}
    static void le16(OutputStream out,int n)throws IOException{out.write(n);out.write(n>>>8);}
    static void le32(OutputStream out,int n)throws IOException{for(int i=0;i<4;i++)out.write(n>>>(i*8));}
    static void le64(OutputStream out,long n)throws IOException{for(int i=0;i<8;i++)out.write((int)(n>>>(i*8)));}
    static byte[] longLE(long n)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();le64(b,n);return b.toByteArray();}
    static void var(OutputStream out,long n)throws IOException{while((n&~127L)!=0){out.write((int)(n&127)|128);n>>>=7;}out.write((int)n);}
    static void varString(OutputStream out,byte[] b)throws IOException{var(out,b.length);out.write(b);}
    static byte[] concat(byte[] a,byte[] b){byte[] out=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;}
}
