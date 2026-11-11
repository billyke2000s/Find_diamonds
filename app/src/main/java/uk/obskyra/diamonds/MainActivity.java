package uk.obskyra.diamonds;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import uk.obskyra.diamonds.core.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff0c151a,PANEL=0xff15242b,ACCENT=0xff62e8cb,TEXT=0xffedf6f7,MUTED=0xffa0b3bb;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;private int epoch=0;private boolean busy=false;
    private WorldStore store;private WorldStore.World world;
    private LinearLayout body,results;private EditText xInput,yInput,zInput;private Spinner oreInput,dimensionInput,radiusInput;
    private TextView worldLabel,status;private Button importButton,worldButton,searchButton,cancelButton;private OreMapView map;
    private Search current;private List<Search.Vein> veins=Collections.emptyList();private String resultScope="";private int shown=0;
    @Override public void onCreate(Bundle state){super.onCreate(state);store=new WorldStore(this);world=store.selected();build();}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
    private void gap(int n){Space space=new Space(this);body.addView(space,new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout card(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(18),dp(14),dp(18),dp(14));r.setBackground(background(PANEL,0));return r;}
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(primary?BG:TEXT);b.setTextSize(14);b.setMinHeight(dp(48));b.setBackground(background(primary?ACCENT:0xff22353e,0));b.setPadding(dp(12),dp(6),dp(12),dp(6));return b;}
    private void weighted(LinearLayout r,View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(v,p);}
    private Spinner spinner(Object[] values){Spinner s=new Spinner(this);ArrayAdapter<Object> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(a);s.setMinimumHeight(dp(48));return s;}
    private EditText coordinate(String label,int value){EditText e=new EditText(this);e.setSingleLine();e.setText(String.valueOf(value));e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setHint(label);e.setContentDescription(label+" coordinate");e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);return e;}
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(20),dp(20),dp(32));scroll.addView(body);setContentView(scroll);
        body.setOnApplyWindowInsetsListener((v,insets)->{body.setPadding(dp(20),dp(20)+insets.getSystemWindowInsetTop(),dp(20),dp(32)+insets.getSystemWindowInsetBottom());return insets;});
        TextView tag=text("OBSKYRA  /  OFFLINE TOOLS",11,ACCENT);tag.setLetterSpacing(.13f);body.addView(tag);
        TextView title=text("Find Diamonds",32,TEXT);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        body.addView(text("Your world. Your ores. No subscription.",15,MUTED));gap(14);
        LinearLayout modes=row();Button scanMode=button("World scanner",true),seedMode=button("Seed support",false);weighted(modes,scanMode);weighted(modes,seedMode);body.addView(modes);seedMode.setOnClickListener(v->showSeedSupport());scanMode.setOnClickListener(v->help());gap(12);
        LinearLayout source=card();source.addView(text("01  CHOOSE YOUR WORLD",11,ACCENT));worldLabel=text(world==null?"Import a saved world":world.name,19,TEXT);source.addView(worldLabel);
        source.addView(text("Java: world ZIP or .mca region\nBedrock: exported .mcworld",13,MUTED));LinearLayout sourceActions=row();importButton=button("Import world",true);worldButton=button("Saved worlds",false);weighted(sourceActions,importButton);weighted(sourceActions,worldButton);source.addView(sourceActions);body.addView(source);
        importButton.setOnClickListener(v->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,1);});worldButton.setOnClickListener(v->chooseWorld());gap(14);
        LinearLayout settings=card();settings.addView(text("02  PLAN YOUR SEARCH",11,ACCENT));
        LinearLayout selectors=row();oreInput=spinner(Ore.values());dimensionInput=spinner(new String[]{"Overworld","Nether","The End"});weighted(selectors,oreInput);weighted(selectors,dimensionInput);settings.addView(selectors);
        LinearLayout coords=row();xInput=coordinate("X",store.prefs.getInt("x",0));yInput=coordinate("Y",store.prefs.getInt("y",-59));zInput=coordinate("Z",store.prefs.getInt("z",0));weighted(coords,xInput);weighted(coords,yInput);weighted(coords,zInput);settings.addView(coords);
        settings.addView(text("Your X / Y / Z coordinates",12,MUTED));LinearLayout radius=row();radius.addView(text("Search radius",14,TEXT),new LinearLayout.LayoutParams(0,-2,1));radiusInput=spinner(new String[]{"32 blocks","64 blocks","128 blocks","256 blocks"});radiusInput.setSelection(2);radius.addView(radiusInput);settings.addView(radius);body.addView(settings);gap(14);
        LinearLayout actions=row();searchButton=button("Find nearby ores",true);cancelButton=button("Cancel",false);cancelButton.setVisibility(View.GONE);weighted(actions,searchButton);weighted(actions,cancelButton);body.addView(actions);searchButton.setOnClickListener(v->startSearch());cancelButton.setOnClickListener(v->cancel());
        status=text("Reads actual saved blocks. Works offline.\nSeed-only searches are not implemented in this preview.",13,MUTED);body.addView(status);gap(10);
        map=new OreMapView(this);map.setBackground(background(PANEL,0));body.addView(map,new LinearLayout.LayoutParams(-1,dp(220)));gap(14);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);body.addView(results);
        LinearLayout footer=row();Button restore=button("Restore mined targets",false),help=button("How it works",false);weighted(footer,restore);weighted(footer,help);body.addView(footer);
        restore.setOnClickListener(v->{if(busy)return;if(world==null)return;String scope=store.scope(world,dimensionInput.getSelectedItemPosition(),(Ore)oreInput.getSelectedItem());store.prefs.edit().remove(scope).apply();startSearch();});help.setOnClickListener(v->help());
    }
    private void help(){new AlertDialog.Builder(this).setTitle("Read your saved world").setMessage("Close Minecraft, then export or ZIP a copy of your world. Import it here and enter your coordinates. The app finds blocks already stored in that export; it cannot read unexplored terrain.\n\nOre groups use face-connected blocks. Counts can be clipped at the search boundary. Mark mined only hides targets in this app. Import a fresh export after mining or exploring.\n\nThis preview reads common Java Anvil and Bedrock LevelDB formats, not every Minecraft version. Console Legacy worlds, pre-Anvil Java, LZ4/external Java chunks and unknown future formats are unsupported.\n\nThere is no server, paid unlock, ad SDK or network permission. Long-press a saved world to remove its imported copy.").setPositiveButton("Got it",null).show();}
    private void showSeedSupport(){
        LinearLayout layout=card();Spinner edition=spinner(new String[]{"Java Edition","Bedrock Edition"});EditText version=new EditText(this);version.setHint("Minecraft version, e.g. 1.21.1");version.setSingleLine();layout.addView(edition);layout.addView(version);layout.addView(text("Both editions are planned. No version has a verified seed-only engine in this preview. World imports use stored blocks instead of generation guesses.",14,MUTED));
        new AlertDialog.Builder(this).setTitle("Seed-only support").setView(layout).setPositiveButton("Check version",(d,w)->new AlertDialog.Builder(this).setMessage(SeedSupport.unavailable(edition.getSelectedItemPosition()==0?SeedSupport.Edition.JAVA:SeedSupport.Edition.BEDROCK,version.getText().toString().trim())).setPositiveButton("OK",null).show()).setNegativeButton("Close",null).show();
    }
    private void chooseWorld(){List<WorldStore.World> list=store.all();if(list.isEmpty()){toast("Import a world first");return;}String[] labels=new String[list.size()];for(int i=0;i<list.size();i++)labels[i]=list.get(i).name;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Saved worlds · hold to remove").setItems(labels,(d,which)->{world=list.get(which);store.select(world);worldLabel.setText(world.name);clearResults();status.setText("World selected. Enter your coordinates and search.");}).setNegativeButton("Close",null).create();
        dialog.setOnShowListener(d->dialog.getListView().setOnItemLongClickListener((parent,view,position,id)->{WorldStore.World selected=list.get(position);new AlertDialog.Builder(this).setTitle("Remove imported copy?").setMessage(selected.name+"\nYour original Minecraft world is untouched.").setPositiveButton("Remove",(a,b)->{store.remove(selected);world=store.selected();worldLabel.setText(world==null?"Import a saved world":world.name);clearResults();dialog.dismiss();}).setNegativeButton("Keep",null).show();return true;}));dialog.show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=1 || result!=RESULT_OK || data==null || data.getData()==null)return;Uri uri=data.getData();String name="world.mcworld";
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null && cursor.moveToFirst())name=cursor.getString(0);}catch(Exception ignored){}
        final String filename=name;final int token=++epoch;setBusy(true);clearResults();status.setText("Importing a private copy…");task=worker.submit(()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open selected file");WorldStore.World imported=store.importWorld(in,filename);post(token,()->{world=imported;worldLabel.setText(world.name);status.setText("Imported. Enter your coordinates and find nearby ores.");setBusy(false);});}catch(Exception e){fail(token,e);}});
    }
    private void startSearch(){if(busy)return;if(world==null){toast("Import a Java or Bedrock world first");return;}
        final Search q;try{int x=Integer.parseInt(xInput.getText().toString().trim()),y=Integer.parseInt(yInput.getText().toString().trim()),z=Integer.parseInt(zInput.getText().toString().trim());q=new Search(x,y,z,new int[]{32,64,128,256}[radiusInput.getSelectedItemPosition()],dimensionInput.getSelectedItemPosition(),(Ore)oreInput.getSelectedItem(),null);store.prefs.edit().putInt("x",x).putInt("y",y).putInt("z",z).apply();}catch(IllegalArgumentException e){toast("Enter valid whole-number coordinates: X/Z within ±30,000,000, Y −2048 to 2047");return;}
        final int token=++epoch;final WorldStore.World selected=world;final String scope=store.scope(selected,q.dimension,q.ore);setBusy(true);clearResults();status.setText("Scanning saved terrain…");
        task=worker.submit(()->{try{Search scan=new Search(q.x,q.y,q.z,q.radius,q.dimension,q.ore,message->post(token,()->status.setText(message)));String edition=WorldArchive.scan(store.file(selected),selected.name,getCacheDir(),scan);scan.blocks.removeAll(store.mined(scope));List<Search.Vein> found=scan.veins();post(token,()->{current=scan;resultScope=scope;veins=found;map.show(found,q.x,q.z,q.radius);status.setText(edition+" · "+scan.chunks+" saved chunks\n"+found.size()+" veins / "+scan.blocks.size()+" ore blocks remaining"+(scan.chunks==0?"\nNo saved chunks in this area. Explore it and import a fresh export.":""));shown=0;renderMore();setBusy(false);});}catch(Exception e){fail(token,e);}});
    }
    private void clearResults(){current=null;veins=Collections.emptyList();results.removeAllViews();map.show(veins,0,0,128);}
    private void renderMore(){if(current==null)return;int end=Math.min(shown+40,veins.size());for(int i=shown;i<end;i++){Search.Vein vein=veins.get(i);LinearLayout card=card();TextView position=text("X "+vein.pos.x+"    Y "+vein.pos.y+"    Z "+vein.pos.z,17,TEXT);position.setTypeface(Typeface.MONOSPACE);card.addView(position);card.addView(text(vein.count+" blocks · "+Math.round(vein.distance)+" blocks away",13,MUTED));LinearLayout actions=row();Button copy=button("Copy coords",false),mined=button("Mark mined",false);weighted(actions,copy);weighted(actions,mined);card.addView(actions);copy.setOnClickListener(v->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Ore coordinates",vein.pos.toString()));toast("Coordinates copied");});mined.setOnClickListener(v->{if(busy)return;store.mark(resultScope,vein.blocks);card.setVisibility(View.GONE);toast("Target hidden. Restore it below if needed.");});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(10);results.addView(card,lp);}shown=end;
        if(shown<veins.size()){Button more=button("Show more · "+(veins.size()-shown)+" remaining",false);results.addView(more);more.setOnClickListener(v->{results.removeView(more);renderMore();});}else if(veins.isEmpty())results.addView(text("No matching saved ore blocks in this area. Try another location, dimension or ore.",15,MUTED));
    }
    private void cancel(){epoch++;if(task!=null)task.cancel(true);setBusy(false);status.setText("Cancelled. You can start another search.");}
    private void setBusy(boolean value){busy=value;for(View v:new View[]{importButton,worldButton,searchButton,xInput,yInput,zInput,oreInput,dimensionInput,radiusInput})v.setEnabled(!value);cancelButton.setVisibility(value?View.VISIBLE:View.GONE);}
    private void post(int token,Runnable action){runOnUiThread(()->{if(token==epoch && !isFinishing() && !isDestroyed())action.run();});}
    private void fail(int token,Exception e){post(token,()->{setBusy(false);status.setText("Could not complete: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())+"\nNo partial results were shown.");});}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){epoch++;if(task!=null)task.cancel(true);worker.shutdownNow();super.onDestroy();}
}
