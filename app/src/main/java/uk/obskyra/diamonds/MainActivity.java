package uk.obskyra.diamonds;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import java.util.*;
import java.util.concurrent.*;
import uk.obskyra.diamonds.core.*;

/** Seed-first entry point. World scanning is an optional, separate activity. */
public final class MainActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> job;
    private int generation;
    private boolean busy;
    private WorldStore store;
    private SeedLayout ui;
    private Search current;
    private List<Search.Vein> veins=Collections.emptyList();
    private String currentScope="",currentVersion="";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);store=new WorldStore(this);ui=new SeedLayout(this,store.prefs);setContentView(ui);
        ui.find.setOnClickListener(view->find());ui.cancel.setOnClickListener(view->cancel());
        ui.scanner.setOnClickListener(view->startActivity(new Intent(this,WorldScanActivity.class)));
        ui.restore.setOnClickListener(view->{if(busy || currentScope.isEmpty())return;store.prefs.edit().remove(currentScope).apply();find();});
        ui.help.setOnClickListener(view->help());
        ui.edition.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(position==0){
                    if(!String.valueOf(ui.version.getSelectedItem()).matches("[0-9].*"))ui.options(ui.version,SeedLayout.versions());
                    if(current==null)ui.status.setText("Experimental Java seed search. No world import needed.\nCaves and terrain can remove candidates; not every diamond is listed.");
                }else{
                    ui.options(ui.version,new String[]{"Not available yet"});
                    ui.status.setText("Bedrock seed prediction is not implemented yet. It uses a different generator, so Java results would be wrong. There is no paid unlock.");
                    current=null;ui.clear();
                }
                ui.find.setEnabled(!busy && position==0);
            }
        });
    }

    private void find(){
        if(busy || ui.edition.getSelectedItemPosition()!=0)return;
        final long seed;final DiamondProfile profile;final Search query;
        try{
            seed=SeedSupport.numericSeed(ui.seed.getText().toString());
            profile=DiamondProfile.from(String.valueOf(ui.version.getSelectedItem()));
            int x=Integer.parseInt(ui.x.getText().toString().trim());
            int y=Integer.parseInt(ui.y.getText().toString().trim());
            int z=Integer.parseInt(ui.z.getText().toString().trim());
            query=new Search(x,y,z,new int[]{32,64,128,256}[ui.radius.getSelectedItemPosition()],0,Ore.DIAMOND,null);
        }catch(IllegalArgumentException error){new AlertDialog.Builder(this).setMessage(error.getMessage()).setPositiveButton("OK",null).show();return;}
        store.prefs.edit().putString("seed-input",Long.toString(seed)).putString("seed-version",profile.version)
                .putInt("seed-x",query.x).putInt("seed-y",query.y).putInt("seed-z",query.z).apply();
        final String scope="seed:JAVA:"+profile.version+":"+seed+":OVERWORLD:DIAMOND";
        final int token=++generation;current=null;veins=Collections.emptyList();ui.clear();setBusy(true);
        ui.status.setText("Calculating diamond candidates from your seed…");
        job=worker.submit(()->{
            try{
                Search calculation=new Search(query.x,query.y,query.z,query.radius,0,Ore.DIAMOND,
                        message->post(token,()->ui.status.setText(message)));
                JavaDiamondSeeds.search(seed,profile,calculation);
                calculation.blocks.removeAll(store.mined(scope));
                List<Search.Vein> found=calculation.veins();
                post(token,()->{current=calculation;currentScope=scope;currentVersion=profile.version;veins=found;render();setBusy(false);});
            }catch(Exception error){post(token,()->{setBusy(false);ui.status.setText("Search failed: "+error.getMessage());});}
        });
    }

    private void render(){
        ui.status.setText("Java "+currentVersion+" · "+veins.size()+" candidate veins\nPredicted geometry only. Terrain, caves and existing mining can remove these blocks.");
        ui.showCandidates(current,veins,vein->{
            if(busy || current==null)return;
            store.mark(currentScope,vein.blocks);current.blocks.removeAll(vein.blocks);
            List<Search.Vein> remaining=new ArrayList<>(veins);remaining.remove(vein);veins=remaining;render();
        });
    }
    private void help(){
        new AlertDialog.Builder(this).setTitle("Seed search preview").setMessage(
                "Enter your numeric seed, choose Java and its generation version, then enter your coordinates. No world export or server is used.\n\n"+
                "This experimental engine calculates candidate diamond geometry for the seven listed Java releases. It does not reproduce caves, terrain replacement, air exposure or existing mining, so candidates may be absent and the list is incomplete. No in-game accuracy percentage has been established.\n\n"+
                "Match the version that generated these chunks. Custom generation, datapacks, snapshots, other releases and Bedrock seed searches are not supported yet.\n\n"+
                "The world scanner is an optional separate tool for reading actual saved blocks.")
                .setPositiveButton("Got it",null).show();
    }
    private void setBusy(boolean value){busy=value;ui.busy(value);}
    private void cancel(){generation++;if(job!=null)job.cancel(true);setBusy(false);ui.status.setText("Search cancelled.");}
    private void post(int token,Runnable action){runOnUiThread(()->{if(token==generation && !isFinishing() && !isDestroyed())action.run();});}
    @Override protected void onDestroy(){generation++;if(job!=null)job.cancel(true);worker.shutdownNow();super.onDestroy();}
}
