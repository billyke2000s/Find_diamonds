package uk.obskyra.diamonds;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import uk.obskyra.diamonds.core.*;

/** Native seed-search form and candidate rendering; calculations live elsewhere. */
final class SeedLayout extends ScrollView {
    private static final int BG=0xff0c151a,PANEL=0xff15242b,TEXT=0xffedf6f7,ACCENT=0xff62e8cb,MUTED=0xffa0b3bb;
    final EditText seed,x,y,z;
    final Spinner edition,version,radius;
    final Button find,cancel,scanner,restore,help;
    final TextView status;
    final OreMapView map;
    private final LinearLayout results;
    private int shown;

    SeedLayout(Context context,SharedPreferences prefs) {
        super(context);setBackgroundColor(BG);
        LinearLayout body=column();body.setPadding(dp(20),dp(24),dp(20),dp(32));addView(body);
        body.setOnApplyWindowInsetsListener((view,insets)->{
            body.setPadding(dp(20),dp(24)+insets.getSystemWindowInsetTop(),dp(20),dp(32)+insets.getSystemWindowInsetBottom());return insets;
        });
        body.addView(text("OBSKYRA  /  OFFLINE TOOLS",11,ACCENT));
        TextView title=text("Find Diamonds",32,TEXT);title.setTypeface(null,Typeface.BOLD);body.addView(title);
        spaced(body,text("Enter your seed. Find nearby diamond candidates.",15,MUTED));
        LinearLayout world=card();world.addView(text("01  YOUR WORLD SEED",11,ACCENT));
        seed=number(prefs.getString("seed-input",""),"World seed");world.addView(seed);
        LinearLayout selectors=row();edition=spinner(new String[]{"Java Edition","Bedrock Edition"});
        version=spinner(versions());weighted(selectors,edition);weighted(selectors,version);world.addView(selectors);
        String savedVersion=prefs.getString("seed-version","1.21.1");int savedIndex=Arrays.asList(versions()).indexOf(savedVersion);version.setSelection(Math.max(0,savedIndex));
        world.addView(text("Use the version that originally generated this area.",12,MUTED));spaced(body,world);
        LinearLayout location=card();location.addView(text("02  WHERE ARE YOU?",11,ACCENT));
        LinearLayout coords=row();x=number(String.valueOf(prefs.getInt("seed-x",0)),"X");y=number(String.valueOf(prefs.getInt("seed-y",-59)),"Y");z=number(String.valueOf(prefs.getInt("seed-z",0)),"Z");
        weighted(coords,x);weighted(coords,y);weighted(coords,z);location.addView(coords);location.addView(text("Your X / Y / Z coordinates",12,MUTED));
        LinearLayout range=row();weighted(range,text("Diamond · Overworld",14,TEXT));radius=spinner(new String[]{"32 blocks","64 blocks","128 blocks","256 blocks"});radius.setSelection(1);weighted(range,radius);location.addView(range);spaced(body,location);
        LinearLayout actions=row();find=button("Find diamonds",true);cancel=button("Cancel",false);cancel.setVisibility(GONE);weighted(actions,find);weighted(actions,cancel);spaced(body,actions);
        status=text("Experimental Java seed search. No world import needed.\nCaves and terrain can remove candidates; not every diamond is listed.",13,MUTED);spaced(body,status);
        map=new OreMapView(context);map.setBackground(background(PANEL));LinearLayout.LayoutParams mapParams=new LinearLayout.LayoutParams(-1,dp(220));mapParams.bottomMargin=dp(14);body.addView(map,mapParams);
        results=column();body.addView(results);restore=button("Restore mined targets",false);spaced(body,restore);
        scanner=button("World scanner (optional)",false);spaced(body,scanner);help=button("Accuracy and supported versions",false);spaced(body,help);
    }

    static String[] versions(){DiamondProfile[] p=DiamondProfile.values();String[] v=new String[p.length];for(int i=0;i<p.length;i++)v[i]=p[i].version;return v;}
    void options(Spinner spinner,String[] values){spinner.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,values));}
    void busy(boolean value){for(View view:new View[]{seed,x,y,z,edition,version,radius,scanner})view.setEnabled(!value);find.setEnabled(!value && edition.getSelectedItemPosition()==0);cancel.setVisibility(value?VISIBLE:GONE);}
    void clear(){results.removeAllViews();map.show(Collections.emptyList(),0,0,64);}

    interface Mark {void mined(Search.Vein vein);}
    void showCandidates(Search query,List<Search.Vein> veins,Mark mark){
        results.removeAllViews();shown=0;map.show(veins,query.x,query.z,query.radius);more(veins,mark);
    }
    private void more(List<Search.Vein> veins,Mark mark){
        int end=Math.min(shown+40,veins.size());
        for(int i=shown;i<end;i++){
            Search.Vein vein=veins.get(i);LinearLayout card=card();TextView coords=text("X "+vein.pos.x+"    Y "+vein.pos.y+"    Z "+vein.pos.z,17,TEXT);coords.setTypeface(Typeface.MONOSPACE);card.addView(coords);
            card.addView(text("Up to "+vein.count+" potential blocks · "+Math.round(vein.distance)+" blocks away",13,MUTED));
            LinearLayout actions=row();Button copy=button("Copy coords",false),mined=button("Mark mined",false);weighted(actions,copy);weighted(actions,mined);card.addView(actions);
            copy.setOnClickListener(view->{((ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Diamond candidate",vein.pos.toString()));Toast.makeText(getContext(),"Coordinates copied",Toast.LENGTH_SHORT).show();});
            mined.setOnClickListener(view->mark.mined(vein));spaced(results,card);
        }
        shown=end;
        if(shown<veins.size()){Button next=button("Show more · "+(veins.size()-shown)+" remaining",false);spaced(results,next);next.setOnClickListener(view->{results.removeView(next);more(veins,mark);});}
        else if(veins.isEmpty())spaced(results,text("No candidates here. Try a larger radius or restore previously marked targets.",14,MUTED));
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(14),dp(18),dp(14));l.setBackground(background(PANEL));return l;}
    private void weighted(LinearLayout parent,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));parent.addView(view,p);}
    private void spaced(LinearLayout parent,View view){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(view,p);}
    private TextView text(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(5),0,dp(5));return v;}
    private Button button(String value,boolean primary){Button b=new Button(getContext());b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);b.setMinHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackground(background(primary?ACCENT:0xff22353e));return b;}
    private EditText number(String value,String label){EditText e=new EditText(getContext());e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setText(value);e.setHint(label);e.setContentDescription(label);return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(getContext());options(s,values);s.setMinimumHeight(dp(48));return s;}
}
