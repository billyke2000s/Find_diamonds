package uk.obskyra.diamonds;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;
import uk.obskyra.diamonds.core.*;

final class OreMapView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<Search.Vein> veins=Collections.emptyList(); private int x,z,radius=128;
    OreMapView(Context context){super(context);setContentDescription("Top-down ore map. North is up; the player is in the centre. Exact coordinates are listed below.");}
    void show(List<Search.Vein> veins,int x,int z,int radius){this.veins=veins;this.x=x;this.z=z;this.radius=radius;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float density=getResources().getDisplayMetrics().density;float cx=getWidth()/2f,cy=getHeight()/2f,scale=Math.min(getWidth(),getHeight())*.43f/radius;
        paint.setColor(0xff263b43);paint.setStrokeWidth(density);for(int i=-4;i<=4;i++){float delta=i*radius*.25f*scale;c.drawLine(cx-radius*scale,cy+delta,cx+radius*scale,cy+delta,paint);c.drawLine(cx+delta,cy-radius*scale,cx+delta,cy+radius*scale,paint);}
        paint.setColor(0xff9aafb8);paint.setTextSize(11*density);c.drawText("N · −Z",12*density,18*density,paint);
        paint.setColor(0xff62e8cb);for(int i=Math.min(veins.size(),2000)-1;i>=0;i--){Pos p=veins.get(i).pos;c.drawCircle(cx+(p.x-x)*scale,cy+(p.z-z)*scale,3*density,paint);}
        paint.setColor(Color.WHITE);c.drawCircle(cx,cy,5*density,paint);paint.setColor(0xff0c151a);c.drawCircle(cx,cy,2*density,paint);
    }
}
