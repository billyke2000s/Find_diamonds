package uk.obskyra.diamonds;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;
import uk.obskyra.diamonds.core.*;

final class WorldStore {
    static final class World {
        final String id,name;
        World(String id,String name){this.id=id;this.name=name;}
        @Override public String toString(){return name;}
    }
    private final File directory;
    final SharedPreferences prefs;
    WorldStore(Context c) { directory=new File(c.getFilesDir(),"worlds");directory.mkdirs();prefs=c.getSharedPreferences("worlds",Context.MODE_PRIVATE); }
    File file(World w){return new File(directory,w.id);}
    List<World> all() {
        List<World> result=new ArrayList<>();
        try { JSONArray array=new JSONArray(prefs.getString("worlds","[]"));for(int i=0;i<array.length();i++){JSONObject o=array.getJSONObject(i);String id=o.getString("id");if(id.matches("[a-f0-9]{64}")){World w=new World(id,o.getString("name"));if(file(w).isFile())result.add(w);}} }catch(JSONException ignored){}
        return result;
    }
    void select(World w){prefs.edit().putString("selected",w.id).apply();}
    World selected(){List<World> worlds=all();String id=prefs.getString("selected","");for(World w:worlds)if(w.id.equals(id))return w;return worlds.isEmpty()?null:worlds.get(0);}
    World importWorld(InputStream in,String name) throws IOException {
        File temp=File.createTempFile("import-",".part",directory);
        try {
            MessageDigest digest;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
            try(OutputStream out=new FileOutputStream(temp)){byte[] b=new byte[65536];int n;long total=0;while((n=in.read(b))!=-1){Search.check();total+=n;if(total>1024L*1024*1024)throw new IOException("World export exceeds the 1 GiB import budget");out.write(b,0,n);digest.update(b,0,n);}}
            Search.check(); StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
            World world=new World(hex.toString(),name);File target=file(world);if(!target.exists() && !temp.renameTo(target))throw new IOException("Could not save imported world");
            List<World> worlds=all();boolean exists=false;for(World w:worlds)if(w.id.equals(world.id))exists=true;if(!exists)worlds.add(world);save(worlds);select(world);return world;
        } finally { if(temp.exists())temp.delete(); }
    }
    void remove(World w){List<World> worlds=all();worlds.removeIf(item->item.id.equals(w.id));save(worlds);file(w).delete();}
    private void save(List<World> worlds){JSONArray a=new JSONArray();for(World w:worlds){JSONObject o=new JSONObject();try{o.put("id",w.id);o.put("name",w.name);a.put(o);}catch(JSONException ignored){}}prefs.edit().putString("worlds",a.toString()).apply();}
    String scope(World w,int dimension,Ore ore){return "mined:"+w.id+":"+dimension+":"+ore.name();}
    Set<Pos> mined(String scope){Set<Pos> result=new HashSet<>();for(String s:prefs.getStringSet(scope,Collections.emptySet())){String[] a=s.split(",");try{if(a.length==3)result.add(new Pos(Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])));}catch(NumberFormatException ignored){}}return result;}
    void mark(String scope,List<Pos> blocks){Set<String> values=new HashSet<>(prefs.getStringSet(scope,Collections.emptySet()));for(Pos p:blocks)values.add(p.x+","+p.y+","+p.z);prefs.edit().putStringSet(scope,values).apply();}
}
