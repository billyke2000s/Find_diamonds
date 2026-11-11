package uk.obskyra.diamonds.core;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Read-only archive access. Entries are never extracted to user-controlled paths. */
public final class WorldArchive {
    private static final Pattern REGION=Pattern.compile("(?:^|/)region/r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    private static final Pattern SINGLE=Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca");
    public static String scan(File file,String originalName,File tempDir,Search q) throws IOException {
        Matcher single=SINGLE.matcher(originalName);
        if(single.matches()) { JavaRegion.scan(file,number(single.group(1)),number(single.group(2)),q);return "Java · Anvil region"; }
        try(ZipFile zip=new ZipFile(file)) {
            if(zip.size()>100000)throw new IOException("Archive contains too many files");
            Map<String,ZipEntry> entries=new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> enumeration=zip.entries();
            while(enumeration.hasMoreElements()) {
                ZipEntry entry=enumeration.nextElement();String name=entry.getName();
                if(name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains(".."))throw new IOException("Invalid archive path");
                if(!entry.isDirectory() && entries.put(name,entry)!=null)throw new IOException("Duplicate archive entries");
            }
            List<String> databases=new ArrayList<>();
            for(String name:entries.keySet())if(name.equals("db/CURRENT") || name.endsWith("/db/CURRENT"))databases.add(name.substring(0,name.length()-7));
            if(databases.size()>1)throw new IOException("Archive contains multiple Bedrock worlds; export one world at a time");
            if(databases.size()==1) {
                String prefix=databases.get(0); Set<String> names=new HashSet<>();
                for(String name:entries.keySet())if(name.startsWith(prefix) && !name.substring(prefix.length()).contains("/"))names.add(name.substring(prefix.length()));
                new BedrockDatabase(new BedrockDatabase.Files() {
                    public byte[] read(String name) throws IOException { ZipEntry entry=entries.get(prefix+name); if(entry==null)throw new IOException("Missing Bedrock database file "+name);try(InputStream in=zip.getInputStream(entry)){return Binary.read(in,128*1024*1024);} }
                    public Set<String> names(){return names;}
                },q).scan(); return "Bedrock · saved world";
            }
            List<String> selected=new ArrayList<>();Set<String> roots=new HashSet<>();
            for(String name:entries.keySet()) {
                Matcher m=REGION.matcher(name);if(!m.find())continue;
                String parent=name.substring(0,m.start());
                // A match at /region starts after the world or dimension prefix.
                int dimension=parent.endsWith("/DIM-1") || parent.equals("DIM-1")?1:parent.endsWith("/DIM1") || parent.equals("DIM1")?2:0;
                if(parent.contains("/dimensions/") || parent.startsWith("dimensions/"))continue;
                String root=dimension==0?parent:parent.substring(0,Math.max(0,parent.lastIndexOf('/')));
                roots.add(root); if(dimension==q.dimension)selected.add(name);
            }
            if(roots.size()>1)throw new IOException("Archive contains multiple Java worlds; export one world at a time");
            if(selected.isEmpty())throw new IOException("No supported world terrain in this dimension. Import a Java world ZIP, r.x.z.mca, or Bedrock .mcworld export.");
            if(!tempDir.isDirectory() && !tempDir.mkdirs())throw new IOException("Cannot create scan cache");
            for(String name:selected) {
                Search.check(); Matcher m=REGION.matcher(name);if(!m.find())continue;int rx=number(m.group(1)),rz=number(m.group(2));
                int minCx=Math.floorDiv(q.x-q.radius,16),maxCx=Math.floorDiv(q.x+q.radius,16),minCz=Math.floorDiv(q.z-q.radius,16),maxCz=Math.floorDiv(q.z+q.radius,16);
                if((long)rx*32>maxCx || (long)rx*32+31<minCx || (long)rz*32>maxCz || (long)rz*32+31<minCz)continue;
                File temp=File.createTempFile("region-",".mca",tempDir);
                try {
                    try(InputStream in=zip.getInputStream(entries.get(name));OutputStream out=new FileOutputStream(temp)) { byte[] b=new byte[16384];int n;long total=0;while((n=in.read(b))!=-1){Search.check();total+=n;if(total>256L*1024*1024)throw new IOException("Region exceeds 256 MiB limit");out.write(b,0,n);} }
                    JavaRegion.scan(temp,rx,rz,q);
                } finally { if(!temp.delete())temp.deleteOnExit(); }
            }
            return "Java · saved world";
        } catch(ZipException e) { throw new IOException("This file is not a supported world ZIP or .mcworld export",e); }
    }
    private static int number(String s) throws IOException { try { int n=Integer.parseInt(s);if(Math.abs((long)n)>60000)throw new NumberFormatException();return n;} catch(NumberFormatException e){throw new IOException("Region coordinate outside supported world bounds");} }
}
